package com.tramsac.thanhtoan;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderExpiryTxService {
    private final DonThanhToanRepository donRepository;
    private final GiaoDichThanhToanRepository giaoDichRepository;
    private final Clock clock;

    public PaymentOrderExpiryTxService(DonThanhToanRepository donRepository,
                                       GiaoDichThanhToanRepository giaoDichRepository, Clock clock) {
        this.donRepository = donRepository;
        this.giaoDichRepository = giaoDichRepository;
        this.clock = clock;
    }

    @Transactional
    public void hetHan(String maDon) {
        GiaoDichThanhToan giaoDich = giaoDichRepository.findByMaDon(maDon).orElse(null);
        if (giaoDich != null) {
            giaoDich = giaoDichRepository.findForUpdateByMaGiaoDich(giaoDich.getMaGiaoDich()).orElse(null);
        }
        DonThanhToan don = donRepository.findForUpdateByMaDon(maDon).orElse(null);
        if (don == null || giaoDich == null || don.getHanThanhToan() == null
                || LocalDateTime.now(clock).isBefore(don.getHanThanhToan())
                || don.layTrangThai().ketThuc()) return;
        don.capNhatTrangThai(TrangThaiThanhToan.HET_HAN);
        giaoDich.capNhatKetQua(TrangThaiThanhToan.HET_HAN);
    }
}
