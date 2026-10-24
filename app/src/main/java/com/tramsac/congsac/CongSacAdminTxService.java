package com.tramsac.congsac;

import com.tramsac.common.ApiException;
import com.tramsac.common.audit.AuditService;
import com.tramsac.datphien.GiuChoService;
import com.tramsac.suco.SuCoService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CongSacAdminTxService {
    private final CongSacRepository congRepository;
    private final CauHinhCongSuatRepository configRepository;
    private final GiuChoService giuChoService;
    private final CanBangTaiService canBangTaiService;
    private final AuditService audit;
    private final SuCoService suCoService;
    private final Clock clock;
    public CongSacAdminTxService(CongSacRepository congRepository, CauHinhCongSuatRepository configRepository,
                                 GiuChoService giuChoService,
                                 CanBangTaiService canBangTaiService, AuditService audit, Clock clock,
                                 SuCoService suCoService) {
        this.congRepository = congRepository; this.configRepository = configRepository; this.giuChoService = giuChoService;
        this.canBangTaiService = canBangTaiService; this.audit = audit; this.clock = clock; this.suCoService = suCoService;
    }
    @Transactional(readOnly = true)
    public java.util.List<CongSacResponse> danhSach() {
        return congRepository.findAll().stream().sorted(java.util.Comparator.comparing(CongSac::getMaCong))
                .map(CongSacResponse::tu).toList();
    }
    @Transactional
    public CongSacResponse khoa(String maCong) {
        CongSac cong = lockPortAndGroup(maCong);
        try { cong.khoaBaoTri(LocalDateTime.now(clock)); }
        catch (IllegalStateException e) { throw new ApiException(HttpStatus.CONFLICT, "CONG_DANG_BAN", e.getMessage()); }
        giuChoService.doiChieu(maCong, LocalDateTime.now(clock));
        if (cong.getMaCauHinh() != null) canBangTaiService.canBang(cong.getMaCauHinh(), LocalDateTime.now(clock));
        audit.ghi(null, "KHOA_CONG_BAO_TRI", "Cổng " + maCong + " được khóa để bảo trì.");
        return CongSacResponse.tu(cong);
    }
    @Transactional
    public CongSacResponse mo(String maCong, boolean safe) {
        CongSac cong = lockPortAndGroup(maCong);
        if (!safe) throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN", "Thiết bị chưa xác nhận an toàn.");
        if (cong.layTrangThai() != TrangThaiCong.BAO_TRI) {
            throw new ApiException(HttpStatus.CONFLICT, "CONG_KHONG_BAO_TRI", "Cổng hiện không ở trạng thái Bảo trì.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        cong.xacNhanAnToan(now);
        cong.moBaoTri(now);
        giuChoService.doiChieu(maCong, now);
        audit.ghi(null, "MO_CONG_BAO_TRI", "Cổng " + maCong + " được mở lại sau xác nhận an toàn.");
        return CongSacResponse.tu(cong);
    }
    @Transactional
    public CongSacResponse xacNhanXuLy(String maCong, boolean safe) {
        CongSac cong = lockPortAndGroup(maCong);
        if (!safe) throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN", "Thiết bị chưa xác nhận an toàn.");
        if (cong.layTrangThai() != TrangThaiCong.LOI) {
            throw new ApiException(HttpStatus.CONFLICT, "CONG_KHONG_LOI", "Cổng hiện không ở trạng thái Lỗi.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        cong.xacNhanAnToan(now);
        cong.xacNhanXuLy(now);
        suCoService.dongSuCoDangMo(maCong);
        giuChoService.doiChieu(maCong, now);
        audit.ghi(null, "XAC_NHAN_XU_LY_CONG", "Kỹ thuật viên xác nhận cổng " + maCong + " đã an toàn.");
        return CongSacResponse.tu(cong);
    }
    private CongSac lockPortAndGroup(String maCong) {
        CongSacRepository.CauHinhProjection projection = congRepository.findCauHinhByMaCong(maCong)
                .orElseThrow(CongSacAdminTxService::notFound);
        if (projection.getMaCauHinh() == null) {
            return congRepository.findForUpdateByMaCong(maCong).orElseThrow(CongSacAdminTxService::notFound);
        }
        // Khóa nhóm trước các cổng theo cùng thứ tự với luồng bắt đầu sạc.
        configRepository.findForUpdateByMaCauHinh(projection.getMaCauHinh())
                .orElseThrow(CongSacAdminTxService::notFound);
        java.util.List<CongSac> ports = congRepository.findForUpdateByMaCauHinhOrderByMaCong(projection.getMaCauHinh());
        return ports.stream().filter(c -> c.getMaCong().equals(maCong)).findFirst()
                .orElseThrow(CongSacAdminTxService::notFound);
    }
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_CONG", "Không tìm thấy cổng sạc.");
    }
}
