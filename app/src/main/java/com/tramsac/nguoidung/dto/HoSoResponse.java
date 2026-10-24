package com.tramsac.nguoidung.dto;

import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.Xe;
import java.util.List;

public record HoSoResponse(String maND, String hoTen, String email, String soDienThoai, String doiTuong,
                           String maDinhDanh, String vaiTro, List<XeDto> xe) {

    public static HoSoResponse tu(NguoiDung nd, List<Xe> xe) {
        return new HoSoResponse(nd.getMaND(), nd.getHoTen(), nd.getEmail(), nd.getSoDienThoai(),
                nd.getDoiTuong(), nd.getMaDinhDanh(), nd.layVaiTro().giaTri(),
                xe.stream().map(XeDto::tu).toList());
    }
}
