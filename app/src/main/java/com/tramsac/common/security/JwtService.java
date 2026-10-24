package com.tramsac.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tạo và đọc JWT (HS256). Token hết hạn sau 30 phút (QĐ2); JwtAuthFilter cấp token mới sau mỗi
 * yêu cầu hợp lệ nên thời hạn "trượt" theo hoạt động.
 */
@Component
public class JwtService {

    static final Duration THOI_HAN = Duration.ofMinutes(30);
    private static final int TOI_THIEU_BYTE = 32;

    private final SecretKey key;
    private final Clock clock;

    public JwtService(@Value("${tramsac.jwt.secret:}") String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < TOI_THIEU_BYTE) {
            throw new IllegalStateException(
                    "JWT_SECRET phải được đặt và dài ít nhất " + TOI_THIEU_BYTE + " ký tự.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String taoToken(String maND) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(maND)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(THOI_HAN)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Trả về mã người dùng nếu token đúng chữ ký và chưa hết hạn; ngược lại trả rỗng. */
    public Optional<String> docMaND(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
