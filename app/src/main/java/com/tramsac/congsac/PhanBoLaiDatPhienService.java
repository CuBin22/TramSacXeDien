package com.tramsac.congsac;

import com.tramsac.common.audit.AuditService;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.datphien.PhanBoCongService;
import com.tramsac.datphien.TrangThaiDatPhien;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.PhienSacService;
import com.tramsac.phiensac.TrangThaiPhien;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** UC08 bổ sung: dời các đặt chỗ tương lai khi cổng chuyển sang Lỗi/Bảo trì/Mất kết nối. */
@Service
public class PhanBoLaiDatPhienService {
    private final DatPhienRepository datRepository;
    private final PhienSacRepository phienRepository;
    private final PhanBoCongService phanBoCongService;
    private final PhienSacService phienSacService;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public PhanBoLaiDatPhienService(DatPhienRepository datRepository, PhienSacRepository phienRepository,
                                    PhanBoCongService phanBoCongService, PhienSacService phienSacService,
                                    AuditService audit, Clock clock, PlatformTransactionManager transactionManager) {
        this.datRepository = datRepository; this.phienRepository = phienRepository;
        this.phanBoCongService = phanBoCongService; this.phienSacService = phienSacService;
        this.audit = audit; this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }
    public void phanBoLai(String maCong) {
        LocalDateTime now = LocalDateTime.now(clock);
        datRepository.timMaDatPhienTuongLaiTheoCong(maCong, TrangThaiDatPhien.DA_DAT.giaTri(), now)
                // Mỗi đặt chỗ một giao dịch riêng. Không gọi phanBoMotDatCho trực tiếp vì gọi nội bộ trong cùng
                // bean sẽ bỏ qua @Transactional, trong khi khóa dòng FOR UPDATE bắt buộc phải có giao dịch.
                .forEach(id -> transaction.executeWithoutResult(status -> phanBoMotDatCho(id, maCong)));
    }
    @Transactional
    public void phanBoMotDatCho(String maDatPhien, String maCongCu) {
        DatPhien dp = datRepository.findForUpdateByMaDatPhien(maDatPhien).orElse(null);
        if (dp == null || dp.layTrangThai() != TrangThaiDatPhien.DA_DAT
                || !dp.getMaCong().equals(maCongCu) || !dp.getGioBatDau().isAfter(LocalDateTime.now(clock))) return;
        PhienSac phien = phienRepository.findForUpdateByMaDatPhien(maDatPhien).orElse(null);
        if (phien == null || phien.layTrangThai() != TrangThaiPhien.DA_DAT) return;
        String newPort = phanBoCongService.phanBoCong(maDatPhien, dp.getLoaiCong(), dp.getGioBatDau(), dp.getGioKetThuc())
                .filter(id -> !id.equals(maCongCu)).orElse(null);
        if (newPort == null) {
            dp.huyDoHeThong("Cổng " + maCongCu + " không khả dụng; không có cổng thay thế.");
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.HUY, "Hệ thống", "Cổng lỗi; không có cổng thay thế");
            audit.ghi(dp.getMaND(), "HUY_DAT_PHIEN_KHONG_CO_CONG", "Đặt chỗ " + maDatPhien + " bị hủy do cổng " + maCongCu);
            return;
        }
        dp.ganLaiCong(newPort);
        phien.doiCong(newPort);
        datRepository.save(dp); phienRepository.save(phien);
        audit.ghi(dp.getMaND(), "PHAN_BO_LAI_CONG", "Đặt chỗ " + maDatPhien + " được chuyển từ " + maCongCu + " sang " + newPort);
    }
}
