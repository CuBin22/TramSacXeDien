package com.tramsac;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay đồng hồ hệ thống bằng MutableClock trong mọi test tích hợp. */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now());
    }
}
