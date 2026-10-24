package com.tramsac.nguoidung;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.nguoidung.dto.DoiVaiTroResponse;
import com.tramsac.nguoidung.dto.LichSuPhanQuyenResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC04 – Phân quyền theo vai trò (BM4, QĐ4). Chỉ Quản trị được gọi (kiểm tra ở controller). */
@Service
public class PhanQuyenService {

    private final NguoiDungRepository nguoiDungRepository;
    private final LichSuPhanQuyenRepository lichSuRepository;
    private final MaGenerator maGenerator;
    private final AuditService auditService;
    private final Clock clock;

    public PhanQuyenService(NguoiDungRepository nguoiDungRepository, LichSuPhanQuyenRepository lichSuRepository,
                            MaGenerator maGenerator, AuditService auditService, Clock clock) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.lichSuRepository = lichSuRepository;
        this.maGenerator = maGenerator;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DoiVaiTroResponse doiVaiTro(String maND, String vaiTroMoiText) {
        VaiTro moi = parse(vaiTroMoiText);
        String nguoiThucHien = NguoiDungHienTai.layMaND();
        if (nguoiThucHien.equals(maND)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_TU_NANG_QUYEN",
                    "Không được tự đổi vai trò của chính mình.");
        }

        // Chỉ ĐỌC vai trò bằng truy vấn chiếu (không nạp entity): nạp entity trước rồi mới khóa sẽ trả về
        // bản cũ trong bộ nhớ đệm cấp 1 (lỗi đã gặp ở đăng nhập).
        String vaiTroHienTai = nguoiDungRepository.timVaiTroTheoMaND(maND).orElseThrow(PhanQuyenService::khongTimThay);

        // Hạ quyền một Quản trị: khóa TẤT CẢ Quản trị theo thứ tự MaND (tránh deadlock) rồi mới quyết định
        boolean haQuanTri = VaiTro.QUAN_TRI.giaTri().equals(vaiTroHienTai) && moi != VaiTro.QUAN_TRI;
        List<NguoiDung> quanTri = haQuanTri
                ? nguoiDungRepository.findForUpdateByVaiTroOrderByMaND(VaiTro.QUAN_TRI.giaTri())
                : List.of();

        // Người thực hiện phải CÒN là Quản trị tại thời điểm này (có thể vừa bị người khác hạ quyền trong lúc chờ khóa)
        boolean conLaQuanTri = haQuanTri
                ? quanTri.stream().anyMatch(a -> a.getMaND().equals(nguoiThucHien))
                : VaiTro.QUAN_TRI.giaTri().equals(nguoiDungRepository.timVaiTroTheoMaND(nguoiThucHien).orElse(null));
        if (!conLaQuanTri) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Không đủ quyền thực hiện.");
        }

        NguoiDung dich = quanTri.stream().filter(a -> a.getMaND().equals(maND)).findFirst()
                .orElseGet(() -> nguoiDungRepository.findForUpdateByMaND(maND)
                        .orElseThrow(PhanQuyenService::khongTimThay));
        VaiTro cu = dich.layVaiTro();
        if (cu == moi) {
            return new DoiVaiTroResponse(cu.giaTri(), moi.giaTri(), false);
        }
        if (cu == VaiTro.QUAN_TRI && !haQuanTri) {
            throw new ApiException(HttpStatus.CONFLICT, "XUNG_DOT_DONG_THOI",
                    "Vai trò vừa được người khác thay đổi, hãy thử lại.");
        }
        if (haQuanTri && cu == VaiTro.QUAN_TRI) {
            // Phòng vệ cuối: không bao giờ để hệ thống còn 0 Quản trị viên
            long conLai = quanTri.stream().filter(a -> !a.getMaND().equals(maND)).count();
            if (conLai < 1) {
                throw new ApiException(HttpStatus.CONFLICT, "QUAN_TRI_CUOI_CUNG",
                        "Không thể hạ quyền Quản trị viên cuối cùng.");
            }
        }

        dich.doiVaiTro(moi);
        nguoiDungRepository.saveAndFlush(dich);
        lichSuRepository.save(new LichSuPhanQuyen(maGenerator.next("LS"), maND, LocalDateTime.now(clock),
                cu.giaTri(), moi.giaTri(), nguoiThucHien));
        auditService.ghi(nguoiThucHien, "PHAN_QUYEN", maND + ": " + cu.giaTri() + " → " + moi.giaTri());
        return new DoiVaiTroResponse(cu.giaTri(), moi.giaTri(), true);
    }

    @Transactional(readOnly = true)
    public List<LichSuPhanQuyenResponse> lichSu(String maND) {
        if (nguoiDungRepository.timVaiTroTheoMaND(maND).isEmpty()) {
            throw khongTimThay();
        }
        return lichSuRepository.findByMaNDOrderByThoiDiemDesc(maND).stream()
                .map(LichSuPhanQuyenResponse::tu).toList();
    }

    private static VaiTro parse(String text) {
        try {
            return VaiTro.tuGiaTri(text);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Dữ liệu không hợp lệ.",
                    Map.of("vaiTroMoi", "Vai trò phải là Quản trị, Kỹ thuật viên, Nhân viên trực hoặc Người dùng."));
        }
    }

    private static ApiException khongTimThay() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_TAI_KHOAN", "Không tìm thấy tài khoản.");
    }
}
