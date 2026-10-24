package com.tramsac.thanhtoan;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderExpiryJob {
    private final DonThanhToanRepository donRepository;
    private final PaymentOrderExpiryTxService txService;
    private final Clock clock;

    public PaymentOrderExpiryJob(DonThanhToanRepository donRepository, PaymentOrderExpiryTxService txService, Clock clock) {
        this.donRepository = donRepository;
        this.txService = txService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tramsac.payment.expiry-scan-ms:30000}")
    public void quetDonHetHan() {
        List<DonThanhToan> expired = donRepository.findByTrangThaiInAndHanThanhToanBefore(
                List.of(TrangThaiThanhToan.CHO.name(), TrangThaiThanhToan.DANG_XU_LY.name()),
                LocalDateTime.now(clock));
        expired.forEach(d -> txService.hetHan(d.getMaDon()));
    }
}
