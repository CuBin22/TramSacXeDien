package com.tramsac.congsac;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CauHinhCongSuatRequest(@NotNull @DecimalMin("0.01") BigDecimal congSuatDauNoi,
                                     @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal nguongGiamTai,
                                     @NotNull LocalDate ngayApDung) { }
