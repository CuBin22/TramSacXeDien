package com.tramsac;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay cổng MQTT thật bằng FakeMqttGateway trong các test giai đoạn 3 (không cần Mosquitto). */
@TestConfiguration
public class FakeMqttConfig {

    @Bean
    @Primary
    FakeMqttGateway fakeMqttGateway() {
        return new FakeMqttGateway();
    }
}
