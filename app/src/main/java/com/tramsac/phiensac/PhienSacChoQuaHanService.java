package com.tramsac.phiensac;

import com.tramsac.common.audit.AuditService;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.datphien.GiuChoService;
import com.tramsac.datphien.TrangThaiDatPhien;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhienSacChoQuaHanService {
    private final PhienSacRepository phienRepository;
    private final DatPhienRepository datRepository;
    private final CongSacRepository congRepository;
    private final PhienSacService phienService;
    private final GiuChoService giuChoService;
    private final AuditService audit;
    private final Clock clock;
    public PhienSacChoQuaHanService(PhienSacRepository phienRepository, DatPhienRepository datRepository,
                                   CongSacRepository congRepository, PhienSacService phienService,
                                   GiuChoService giuChoService, AuditService audit, Clock clock) {
        this.phienRepository = phienRepository; this.datRepository = datRepository; this.congRepository = congRepository;
        this.phienService = phienService; this.giuChoService = giuChoService; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public boolean expire(String maPhien) {
        String maDat = phienRepository.timMaDatPhien(maPhien).orElse(null);
        if (maDat == null) return false;
        DatPhien dp = datRepository.findForUpdateByMaDatPhien(maDat).orElse(null);
        PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (dp == null || phien == null || phien.layTrangThai() != TrangThaiPhien.CHO
                || phien.getChoLuc() == null || phien.getChoLuc().isAfter(now.minusMinutes(15))) return false;
        CongSac cong = congRepository.findForUpdateByMaCong(dp.getMaCong()).orElse(null);
        if (cong == null || cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.DANG_SAC) return false;
        phienService.chuyenTrangThai(phien, TrangThaiPhien.HUY, "Hệ thống", "Phiên chờ bắt đầu quá 15 phút");
        dp.huyDoHeThong("Phiên không nhận được xác nhận bắt đầu trong 15 phút.");
        if (dp.layTrangThai() == TrangThaiDatPhien.DA_DAT) datRepository.save(dp);
        giuChoService.doiChieu(dp.getMaCong(), now);
        audit.ghi(dp.getMaND(), "PHIEN_CHO_QUA_HAN", "Phiên " + maPhien + " bị hủy do không bắt đầu trong 15 phút.");
        return true;
    }
}
