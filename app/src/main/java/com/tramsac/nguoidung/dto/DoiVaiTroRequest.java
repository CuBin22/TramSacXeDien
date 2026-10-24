package com.tramsac.nguoidung.dto;

import jakarta.validation.constraints.NotBlank;

/** Biểu mẫu BM4. vaiTroMoi: Quản trị, Kỹ thuật viên, Nhân viên trực hoặc Người dùng. */
public record DoiVaiTroRequest(@NotBlank(message = "Cần chọn vai trò mới.") String vaiTroMoi) {
}
