package com.tramsac.nguoidung.dto;

import jakarta.validation.constraints.NotBlank;

/** Biểu mẫu BM2. dinhDanh là email hoặc số điện thoại. */
public record DangNhapRequest(
        @NotBlank(message = "Cần nhập email hoặc số điện thoại.") String dinhDanh,
        @NotBlank(message = "Cần nhập mật khẩu.") String matKhau) {
}
