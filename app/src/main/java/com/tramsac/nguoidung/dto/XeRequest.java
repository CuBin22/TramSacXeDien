package com.tramsac.nguoidung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record XeRequest(
        @NotBlank(message = "Biển số xe không được trống.") @Size(max = 20) String bienSo,
        @NotBlank(message = "Loại xe không được trống.") @Size(max = 50) String loaiXe,
        @NotBlank(message = "Loại cổng sử dụng không được trống.")
        @Pattern(regexp = "AC|DC", message = "Loại cổng sử dụng phải là AC hoặc DC.")
        String loaiCongSuDung) {
}
