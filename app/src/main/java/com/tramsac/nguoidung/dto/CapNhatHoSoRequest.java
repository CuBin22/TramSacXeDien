package com.tramsac.nguoidung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Biểu mẫu BM3. Người dùng chỉ được sửa họ tên, email, số điện thoại. doiTuong và vaiTro chỉ để phát hiện
 * việc cố đổi: gửi giá trị khác giá trị hiện tại thì bị từ chối (QĐ3).
 */
public record CapNhatHoSoRequest(
        @NotBlank(message = "Họ tên không được trống.") @Size(max = 120) String hoTen,
        @Size(max = 120) String email,
        @Size(max = 30) String soDienThoai,
        String doiTuong,
        String vaiTro) {
}
