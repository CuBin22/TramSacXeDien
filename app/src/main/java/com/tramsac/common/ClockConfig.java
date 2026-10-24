package com.tramsac.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mọi code cần thời gian phải inject Clock, không gọi trực tiếp Instant.now()/LocalDateTime.now(). */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
