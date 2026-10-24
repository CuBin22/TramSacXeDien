package com.tramsac.bieugia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record BieuGiaRequest(@NotBlank String tenBieuGia, @NotNull LocalDate ngayApDung,
                             @NotEmpty List<@Valid ChiTietBieuGiaRequest> chiTiet) { }
