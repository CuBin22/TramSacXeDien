package com.tramsac.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant T0 = Instant.parse("2026-10-14T00:00:00Z");

    private JwtService service(Instant now) {
        return new JwtService(SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void taoToken_thenDocMaND_returnsSameId() {
        String token = service(T0).taoToken("ND-123");
        assertThat(service(T0).docMaND(token)).contains("ND-123");
    }

    @Test
    void tokenStillValidJustBeforeThirtyMinutes() {
        String token = service(T0).taoToken("ND-123");
        assertThat(service(T0.plus(Duration.ofMinutes(29))).docMaND(token)).contains("ND-123");
    }

    @Test
    void expiredToken_returnsEmpty() {
        String token = service(T0).taoToken("ND-123");
        assertThat(service(T0.plus(Duration.ofMinutes(31))).docMaND(token)).isEmpty();
    }

    @Test
    void tamperedPayload_returnsEmpty() {
        String token = service(T0).taoToken("ND-123");
        String[] parts = token.split("\\.");
        char c = parts[1].charAt(5);
        String doi = parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A') + parts[1].substring(6);
        String gia = parts[0] + "." + doi + "." + parts[2];
        assertThat(service(T0).docMaND(gia)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecret_returnsEmpty() {
        JwtService khac = new JwtService("ffffffffffffffffffffffffffffffff", Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(service(T0).docMaND(khac.taoToken("ND-123"))).isEmpty();
    }

    @Test
    void garbageInput_returnsEmpty() {
        JwtService s = service(T0);
        assertThat(s.docMaND(null)).isEmpty();
        assertThat(s.docMaND("")).isEmpty();
        assertThat(s.docMaND("abc")).isEmpty();
        assertThat(s.docMaND("a.b.c")).isEmpty();
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("ngan", Clock.fixed(T0, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("", Clock.fixed(T0, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class);
    }
}
