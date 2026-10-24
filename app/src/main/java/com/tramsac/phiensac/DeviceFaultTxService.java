package com.tramsac.phiensac;

import com.tramsac.common.audit.AuditService;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.datphien.TrangThaiDatPhien;
import com.tramsac.suco.SuCoService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceFaultTxService {
    private static final List<String> ACTIVE = List.of(TrangThaiPhien.CHO.giaTri(), TrangThaiPhien.DANG_SAC.giaTri());
    private final PhienSacRepository phienRepository;
    private final DatPhienRepository datRepository;
    private final CongSacRepository congRepository;
    private final PhienSacService phienService;
    private final SuCoService suCoService;
    private final AuditService audit;
    private final Clock clock;
    public DeviceFaultTxService(PhienSacRepository phienRepository, DatPhienRepository datRepository,
                                CongSacRepository congRepository, PhienSacService phienService,
                                SuCoService suCoService, AuditService audit, Clock clock) {
        this.phienRepository = phienRepository; this.datRepository = datRepository;
        this.congRepository = congRepository; this.phienService = phienService;
        this.suCoService = suCoService; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public String ghiNhan(String maCong, String loaiLoi) {
        return ghiNhan(maCong, loaiLoi, true);
    }
    @Transactional
    public String ghiNhan(String maCong, String loaiLoi, boolean taoSuCoTuDong) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (String maPhien : phienRepository.timMaPhienTheoCongVaTrangThai(maCong, ACTIVE)) {
            String maDat = phienRepository.timMaDatPhien(maPhien).orElse(null);
            DatPhien dp = maDat == null ? null : datRepository.findForUpdateByMaDatPhien(maDat).orElse(null);
            PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien).orElse(null);
            if (phien == null || phien.layTrangThai().laTrangThaiCuoi()) continue;
            if (phien.getMaDatPhien() != null && dp != null) dp.huyDoHeThong("Thiết bị báo lỗi: " + loaiLoi);
            phien.khoa();
            phienService.chuyenTrangThai(phien, TrangThaiPhien.HUY, "Hệ thống", "Thiết bị báo lỗi: " + loaiLoi);
            if (dp != null) datRepository.save(dp);
        }
        CongSac cong = congRepository.findForUpdateByMaCong(maCong).orElse(null);
        if (cong == null) return null;
        cong.baoLoi(now);
        if (taoSuCoTuDong) suCoService.taoNeuChuaMo(maCong, "An toàn điện", "Khẩn cấp",
                "Thiết bị báo lỗi " + loaiLoi + "; cổng bị khóa an toàn.");
        audit.ghi(null, taoSuCoTuDong ? "THIET_BI_BAO_LOI" : "NHAN_VIEN_BAO_LOI",
                "Cổng " + maCong + ": " + loaiLoi + "; đã khóa cổng và phiên liên quan.");
        return cong.getMaCauHinh();
    }
}
