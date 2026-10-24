package com.tramsac.datphien;

import com.tramsac.common.audit.AuditService;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.TrangThaiCong;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.PhienSacService;
import com.tramsac.phiensac.TrangThaiPhien;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC09 – Giải phóng cổng khi đặt chỗ quá hạn (BM9, QĐ9, quy tắc 9). Thứ tự khóa: đặt chỗ → phiên → cổng. */
@Service
public class GiaiPhongQuaHanService {

    private final DatPhienRepository datPhienRepository;
    private final PhienSacRepository phienSacRepository;
    private final CongSacRepository congSacRepository;
    private final PhienSacService phienSacService;
    private final GiuChoService giuChoService;
    private final AuditService auditService;

    public GiaiPhongQuaHanService(DatPhienRepository datPhienRepository, PhienSacRepository phienSacRepository,
                                  CongSacRepository congSacRepository, PhienSacService phienSacService,
                                  GiuChoService giuChoService, AuditService auditService) {
        this.datPhienRepository = datPhienRepository;
        this.phienSacRepository = phienSacRepository;
        this.congSacRepository = congSacRepository;
        this.phienSacService = phienSacService;
        this.giuChoService = giuChoService;
        this.auditService = auditService;
    }

    /**
     * Xử lý một đặt chỗ; trả về true nếu đã chuyển sang Quá hạn không đến. Trả false (không đổi gì) khi:
     * đã đổi trạng thái (hủy, bắt đầu sạc...), chưa quá hạn, phiên không còn ở Đã đặt (đang Chờ ack hoặc đang sạc,
     * quy tắc 9), cổng đang sạc (ngoại lệ 2a) hoặc mất kết nối nên chưa xác nhận được (ngoại lệ 2b: thử lại sau).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean giaiPhong(String maDatPhien, LocalDateTime now) {
        DatPhien dp = datPhienRepository.findForUpdateByMaDatPhien(maDatPhien).orElse(null);
        if (dp == null || dp.layTrangThai() != TrangThaiDatPhien.DA_DAT
                || !dp.getGioBatDau().isBefore(now.minus(QuyDinhDatPhien.QUA_HAN_SAU))) {
            return false;
        }
        PhienSac phien = phienSacRepository.findForUpdateByMaDatPhien(maDatPhien).orElse(null);
        if (phien == null || phien.layTrangThai() != TrangThaiPhien.DA_DAT) {
            return false;
        }
        CongSac cong = congSacRepository.findForUpdateByMaCong(dp.getMaCong()).orElse(null);
        if (cong == null) {
            return false;
        }
        TrangThaiCong trangThaiCong = cong.layTrangThai();
        if (trangThaiCong == TrangThaiCong.DANG_SAC || trangThaiCong == TrangThaiCong.MAT_KET_NOI) {
            return false;
        }

        dp.danhDauQuaHan();
        phienSacService.chuyenTrangThai(phien, TrangThaiPhien.HUY, "Hệ thống", "Quá hạn không đến");
        datPhienRepository.saveAndFlush(dp);
        giuChoService.doiChieu(dp.getMaCong(), now);
        auditService.ghi(dp.getMaND(), "GIAI_PHONG_QUA_HAN",
                "Đặt chỗ " + dp.getMaDatPhien() + " quá hạn không đến, cổng " + dp.getMaCong());
        return true;
    }
}
