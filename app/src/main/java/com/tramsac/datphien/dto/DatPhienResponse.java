package com.tramsac.datphien.dto;

import com.tramsac.datphien.DatPhien;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DatPhienResponse(String maDatPhien, String maPhien, String bienSo, String maCong, String loaiCong,
                               OffsetDateTime gioBatDau, OffsetDateTime gioKetThuc, String trangThai,
                               int soLanDoiLich, String lyDoHuy) {

    public static DatPhienResponse tu(DatPhien dp, String maPhien, String bienSo) {
        return new DatPhienResponse(dp.getMaDatPhien(), maPhien, bienSo, dp.getMaCong(), dp.getLoaiCong(),
                utc(dp.getGioBatDau()), utc(dp.getGioKetThuc()), dp.getTrangThai(), dp.getSoLanDoiLich(),
                dp.getLyDoHuy());
    }

    private static OffsetDateTime utc(LocalDateTime t) {
        return t.atOffset(ZoneOffset.UTC);
    }
}
