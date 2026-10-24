package com.tramsac.datphien.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HuyDatPhienRequest(
        @NotBlank(message = "Cần nhập lý do.") @Size(max = 200, message = "Lý do tối đa 200 ký tự.") String lyDo) {
}
