package com.tramsac.thanhtoan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureService {
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private final String secret;
    private final Clock clock;

    public WebhookSignatureService(@Value("${tramsac.payment.webhook-secret}") String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public boolean hopLe(String timestamp, String signature, byte[] body) {
        if (secret == null || secret.isBlank() || timestamp == null || signature == null || body == null) return false;
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(clock.instant().getEpochSecond() - seconds) > MAX_CLOCK_SKEW_SECONDS) return false;
            String supplied = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            byte[] expected = HexFormat.of().parseHex(taoChuKy(timestamp, body));
            byte[] actual = HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String taoChuKy(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký webhook.", e);
        }
    }
}
