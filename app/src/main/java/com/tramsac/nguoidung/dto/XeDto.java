package com.tramsac.nguoidung.dto;

import com.tramsac.nguoidung.Xe;

public record XeDto(String maXe, String bienSo, String loaiXe, String loaiCongSuDung) {

    public static XeDto tu(Xe xe) {
        return new XeDto(xe.getMaXe(), xe.getBienSo(), xe.getLoaiXe(), xe.getLoaiCongSuDung());
    }
}
