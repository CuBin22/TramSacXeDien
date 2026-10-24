package com.tramsac.phiensac;

import jakarta.validation.constraints.NotBlank;

public record PhanHoiCanhBaoRequest(@NotBlank String phanHoi) { }
