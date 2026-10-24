package com.tramsac.common.security;

import com.tramsac.common.ApiException;
import com.tramsac.nguoidung.VaiTro;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Truy cập người dùng đang đăng nhập từ SecurityContext. */
public final class NguoiDungHienTai {

    private NguoiDungHienTai() {
    }

    public static String layMaND() {
        return (String) xacThuc().getPrincipal();
    }

    public static VaiTro layVaiTro() {
        String quyen = xacThuc().getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElseThrow(() -> chuaXacThuc());
        return VaiTro.valueOf(quyen.substring("ROLE_".length()));
    }

    private static Authentication xacThuc() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            throw chuaXacThuc();
        }
        return a;
    }

    private static ApiException chuaXacThuc() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CHUA_XAC_THUC", "Cần đăng nhập.");
    }
}
