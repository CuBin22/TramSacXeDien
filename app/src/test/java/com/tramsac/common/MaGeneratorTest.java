package com.tramsac.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaGeneratorTest {

    private final MaGenerator generator = new MaGenerator();

    @Test
    void next_hasPrefixAndTenHex() {
        assertThat(generator.next("ND")).matches("ND-[0-9a-f]{10}");
    }

    @Test
    void next_twoCallsDiffer() {
        assertThat(generator.next("ND")).isNotEqualTo(generator.next("ND"));
    }
}
