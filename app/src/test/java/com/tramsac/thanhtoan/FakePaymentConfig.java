package com.tramsac.thanhtoan;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay PaymentSimGateway bằng FakeCongThanhToan trong test giai đoạn 4 (không cần chạy payment-sim). */
@TestConfiguration
public class FakePaymentConfig {

    @Bean
    @Primary
    FakeCongThanhToan fakeCongThanhToan() {
        return new FakeCongThanhToan();
    }
}
