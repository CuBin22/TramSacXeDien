package com.tramsac.thanhtoan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record HoanTienRequest(
        @NotBlank String maGiaoDich,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2) BigDecimal soTienHoan,
        @NotBlank @jakarta.validation.constraints.Size(max = 30) String lyDo) { }
