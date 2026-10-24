package com.tramsac.common;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Sinh mã khóa chính dạng chuỗi: tiền tố + "-" + 10 ký tự hex ngẫu nhiên (ví dụ ND-3fa91c07be). */
@Component
public class MaGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String next(String tienTo) {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return tienTo + "-" + HexFormat.of().formatHex(bytes);
    }
}
