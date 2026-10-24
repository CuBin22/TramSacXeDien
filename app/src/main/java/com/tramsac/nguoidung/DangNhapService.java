package com.tramsac.nguoidung;

import com.tramsac.common.ApiException;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.JwtService;
import com.tramsac.nguoidung.dto.DangNhapRequest;
import com.tramsac.nguoidung.dto.DangNhapResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC02 – Đăng nhập (BM2, QĐ2): sai 5 lần liên tiếp thì tạm khóa 15 phút. */
@Service
public class DangNhapService {

    static final int TOI_DA_SAI_LIEN_TIEP = 5;
    static final Duration THOI_GIAN_KHOA = Duration.ofMinutes(15);

    private final NguoiDungRepository nguoiDungRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final Clock clock;
    /** Băm giả để thời gian phản hồi khi định danh không tồn tại gần giống khi sai mật khẩu. */
    private final String bamGia;

    public DangNhapService(NguoiDungRepository nguoiDungRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AuditService auditService, Clock clock) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.clock = clock;
        this.bamGia = passwordEncoder.encode("khong-co-tai-khoan-nay");
    }

    /**
     * noRollbackFor: lần đăng nhập sai ném ApiException nhưng bộ đếm sai, thời điểm khóa và nhật ký
     * vẫn phải được lưu; nếu để mặc định thì giao dịch bị hoàn tác và không bao giờ khóa được.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DangNhapResponse dangNhap(DangNhapRequest req) {
        Optional<String> timThay = timMaNDTheoDinhDanh(req.dinhDanh());
        if (timThay.isEmpty()) {
            passwordEncoder.matches(req.matKhau(), bamGia);
            throw saiThongTin();
        }

        // Khóa dòng để nhiều lần đoán mật khẩu đồng thời vẫn được đếm đủ từng lần. Chỉ lấy MÃ ở bước trên
        // (không nạp entity) để entity được đọc MỚI sau khi có khóa, không phải bản cũ trong bộ nhớ đệm.
        NguoiDung nd = nguoiDungRepository.findForUpdateByMaND(timThay.get())
                .orElseThrow(DangNhapService::saiThongTin);
        LocalDateTime now = LocalDateTime.now(clock);

        if (nd.getKhoaDenLuc() != null) {
            if (now.isBefore(nd.getKhoaDenLuc())) {
                throw tamKhoa(now, nd.getKhoaDenLuc());   // kể cả khi mật khẩu đúng
            }
            nd.datLaiBoDemDangNhap();   // hết khóa: được thử lại từ đầu (5 lần mới)
        }

        if (!passwordEncoder.matches(req.matKhau(), nd.getMatKhauHash())) {
            int soLanSai = nd.tangSoLanSai();
            auditService.ghi(nd.getMaND(), "DANG_NHAP_SAI", "Sai mật khẩu lần " + soLanSai);
            if (soLanSai >= TOI_DA_SAI_LIEN_TIEP) {
                nd.khoaDen(now.plus(THOI_GIAN_KHOA));
                auditService.ghi(nd.getMaND(), "TAM_KHOA", "Tạm khóa đăng nhập 15 phút");
            }
            throw saiThongTin();
        }

        nd.datLaiBoDemDangNhap();
        auditService.ghi(nd.getMaND(), "DANG_NHAP_THANH_CONG", null);
        return new DangNhapResponse(jwtService.taoToken(nd.getMaND()), nd.getMaND(), nd.layVaiTro().giaTri());
    }

    private Optional<String> timMaNDTheoDinhDanh(String dinhDanh) {
        if (dinhDanh.contains("@")) {
            String email = DangKyService.chuanHoaEmail(dinhDanh);
            return email == null ? Optional.empty() : nguoiDungRepository.timMaNDTheoEmail(email);
        }
        String sdt = DangKyService.chuanHoaSoDienThoai(dinhDanh);
        return sdt == null ? Optional.empty() : nguoiDungRepository.timMaNDTheoSoDienThoai(sdt);
    }

    /** Thông báo giống nhau cho "không có tài khoản" và "sai mật khẩu" để không lộ tài khoản nào tồn tại. */
    private static ApiException saiThongTin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SAI_THONG_TIN", "Thông tin đăng nhập không đúng.");
    }

    private static ApiException tamKhoa(LocalDateTime now, LocalDateTime khoaDen) {
        long conLai = Math.max(1, Duration.between(now, khoaDen).toSeconds());
        return new ApiException(HttpStatus.LOCKED, "TAM_KHOA",
                "Tài khoản tạm khóa do đăng nhập sai nhiều lần.", Map.of("conLaiGiay", String.valueOf(conLai)));
    }
}
