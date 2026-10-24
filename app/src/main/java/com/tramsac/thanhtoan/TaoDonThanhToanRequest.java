package com.tramsac.thanhtoan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaoDonThanhToanRequest(
        @NotBlank String maPhien,
        @NotNull PhuongThucThanhToan phuongThuc,
        @NotNull KichBanThanhToan kichBan,
        @NotNull CheDoCallback cheDoCallback,
        @Min(0) @Max(60) Integer doTreGiay,
        TrangThaiThanhToan ketQuaCuoi,
        @Size(max = 25) String soTheThuNghiem) {
    public TaoDonThanhToanRequest {
        if (kichBan == null) kichBan = KichBanThanhToan.THANH_CONG;
        if (cheDoCallback == null) cheDoCallback = CheDoCallback.BINH_THUONG;
        if (doTreGiay == null) doTreGiay = 0;
        if (kichBan == KichBanThanhToan.CHAM_PHAN_HOI && doTreGiay == 0) doTreGiay = 2;
    }
}
