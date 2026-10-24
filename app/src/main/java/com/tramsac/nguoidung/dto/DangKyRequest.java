package com.tramsac.nguoidung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Biểu mẫu BM1 (đăng ký). BM1 chỉ có biển số xe; loaiXe và loaiCongSuDung được bổ sung vì
 * UC05 (đặt phiên) cần loại cổng của xe. Email và số điện thoại được kiểm tra trong service
 * sau khi chuẩn hóa (cắt khoảng trắng, chữ thường).
 */
public record DangKyRequest(
        @NotBlank(message = "Họ tên không được trống.") @Size(max = 120) String hoTen,
        @Size(max = 120) String email,
        @Size(max = 30) String soDienThoai,
        @NotBlank(message = "Đối tượng không được trống.")
        @Pattern(regexp = "Sinh viên|Cán bộ|Khách vãng lai|Xe công vụ",
                message = "Đối tượng phải là Sinh viên, Cán bộ, Khách vãng lai hoặc Xe công vụ.")
        String doiTuong,
        @Size(max = 30) String maDinhDanh,
        @NotBlank(message = "Biển số xe không được trống.") @Size(max = 20) String bienSo,
        @NotBlank(message = "Loại xe không được trống.") @Size(max = 50) String loaiXe,
        @NotBlank(message = "Loại cổng sử dụng không được trống.")
        @Pattern(regexp = "AC|DC", message = "Loại cổng sử dụng phải là AC hoặc DC.")
        String loaiCongSuDung,
        @NotBlank(message = "Mật khẩu không được trống.")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$", message = "Mật khẩu phải gồm cả chữ và số.")
        String matKhau,
        @NotBlank(message = "Cần xác nhận mật khẩu.") String xacNhanMatKhau) {
}
