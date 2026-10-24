package com.tramsac.datphien.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record DoiLichRequest(
        @NotNull(message = "Cần nhập giờ bắt đầu mới.") OffsetDateTime gioBatDau,
        @NotNull(message = "Cần nhập giờ kết thúc mới.") OffsetDateTime gioKetThuc,
        @NotBlank(message = "Cần nhập lý do.") @Size(max = 200, message = "Lý do tối đa 200 ký tự.") String lyDo) {
}
