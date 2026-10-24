package com.tramsac.datphien.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

/** Biểu mẫu BM5. Thời gian theo ISO-8601 có múi giờ, ví dụ 2026-10-16T08:00:00+07:00 (hệ thống lưu theo UTC). */
public record DatPhienRequest(
        @NotBlank(message = "Biển số xe không được trống.") String bienSo,
        @NotBlank(message = "Loại cổng không được trống.")
        @Pattern(regexp = "AC|DC", message = "Loại cổng phải là AC hoặc DC.") String loaiCong,
        @NotNull(message = "Cần nhập giờ bắt đầu.") OffsetDateTime gioBatDau,
        @NotNull(message = "Cần nhập giờ kết thúc.") OffsetDateTime gioKetThuc) {
}
