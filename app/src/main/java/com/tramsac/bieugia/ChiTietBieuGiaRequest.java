package com.tramsac.bieugia;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalTime;

public record ChiTietBieuGiaRequest(@NotBlank String khungGio,
                                    @NotBlank String loaiCong,
                                    @NotBlank String doiTuong,
                                    @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal donGia,
                                    LocalTime gioBatDau,
                                    LocalTime gioKetThuc) { }
