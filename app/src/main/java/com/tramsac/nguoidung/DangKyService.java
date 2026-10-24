package com.tramsac.nguoidung;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.nguoidung.dto.DangKyRequest;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC01 – Đăng ký tài khoản (BM1, QĐ1). */
@Service
public class DangKyService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SDT = Pattern.compile("^\\+?[0-9]{9,15}$");

    private final NguoiDungRepository nguoiDungRepository;
    private final XeRepository xeRepository;
    private final PasswordEncoder passwordEncoder;
    private final MaGenerator maGenerator;
    private final AuditService auditService;

    public DangKyService(NguoiDungRepository nguoiDungRepository, XeRepository xeRepository,
                         PasswordEncoder passwordEncoder, MaGenerator maGenerator, AuditService auditService) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.xeRepository = xeRepository;
        this.passwordEncoder = passwordEncoder;
        this.maGenerator = maGenerator;
        this.auditService = auditService;
    }

    /** Trả về mã tài khoản mới (MaND). Vai trò luôn là Người dùng, bất kể dữ liệu client gửi lên. */
    @Transactional
    public String dangKy(DangKyRequest req) {
        String email = chuanHoaEmail(req.email());
        String sdt = chuanHoaSoDienThoai(req.soDienThoai());
        String bienSo = chuanHoaBienSo(req.bienSo());

        kiemTraDinhDang(req, email, sdt);

        if (email != null && nguoiDungRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "TRUNG_DU_LIEU", "Email đã được sử dụng.");
        }
        if (sdt != null && nguoiDungRepository.existsBySoDienThoai(sdt)) {
            throw new ApiException(HttpStatus.CONFLICT, "TRUNG_DU_LIEU", "Số điện thoại đã được sử dụng.");
        }
        if (xeRepository.existsByBienSo(bienSo)) {
            throw new ApiException(HttpStatus.CONFLICT, "TRUNG_BIEN_SO", "Biển số xe đã gắn với tài khoản khác.");
        }

        String maND = maGenerator.next("ND");
        // saveAndFlush: nếu hai yêu cầu đồng thời cùng email thì UNIQUE của CSDL báo lỗi ngay tại đây
        nguoiDungRepository.saveAndFlush(new NguoiDung(maND, req.hoTen().trim(), email, sdt,
                passwordEncoder.encode(req.matKhau()), req.doiTuong(), req.maDinhDanh(), VaiTro.NGUOI_DUNG));
        xeRepository.saveAndFlush(new Xe(maGenerator.next("XE"), maND, bienSo, req.loaiXe().trim(),
                req.loaiCongSuDung()));

        auditService.ghi(maND, "DANG_KY", "Đăng ký tài khoản, đối tượng " + req.doiTuong());
        return maND;
    }

    private void kiemTraDinhDang(DangKyRequest req, String email, String sdt) {
        kiemTraLienHe(email, sdt);
        if (!req.matKhau().equals(req.xacNhanMatKhau())) {
            loi("xacNhanMatKhau", "Mật khẩu xác nhận không khớp.");
        }
    }

    /** Email/số điện thoại đã chuẩn hóa: cần ít nhất một trong hai và đúng định dạng (dùng lại ở hồ sơ). */
    static void kiemTraLienHe(String email, String sdt) {
        if (email == null && sdt == null) {
            loi("email", "Cần nhập email hoặc số điện thoại.");
        }
        if (email != null && !EMAIL.matcher(email).matches()) {
            loi("email", "Email không hợp lệ.");
        }
        if (sdt != null && !SDT.matcher(sdt).matches()) {
            loi("soDienThoai", "Số điện thoại không hợp lệ.");
        }
    }

    private static void loi(String truong, String thongBao) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Dữ liệu không hợp lệ.",
                Map.of(truong, thongBao));
    }

    static String chuanHoaEmail(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    static String chuanHoaSoDienThoai(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("[\\s.\\-]", "");
        return t.isEmpty() ? null : t;
    }

    public static String chuanHoaBienSo(String s) {
        return s.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
