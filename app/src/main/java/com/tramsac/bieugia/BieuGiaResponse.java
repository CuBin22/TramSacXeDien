package com.tramsac.bieugia;

import java.time.LocalDate;
import java.util.List;

public record BieuGiaResponse(String maBieuGia, String tenBieuGia, LocalDate ngayApDung,
                              List<ChiTietBieuGiaRequest> chiTiet) { }
