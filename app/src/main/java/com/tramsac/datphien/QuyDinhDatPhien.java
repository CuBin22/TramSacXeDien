package com.tramsac.datphien;

import com.tramsac.common.ApiException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Các quy định đặt phiên (QĐ5–QĐ9 và quy tắc nghiệp vụ 2, 9, 11). Một nơi duy nhất cho mọi con số. */
@Component
public class QuyDinhDatPhien {

    public static final Duration THOI_LUONG_TOI_THIEU = Duration.ofMinutes(30);
    public static final Duration THOI_LUONG_TOI_DA = Duration.ofHours(3);
    public static final Duration DAT_TRUOC_TOI_DA = Duration.ofHours(24);
    public static final int TOI_DA_DAT_CHO_HIEU_LUC = 2;
    public static final int TOI_DA_LAN_DOI_LICH = 2;
    /** Hủy hoặc đổi lịch trước giờ bắt đầu ít nhất khoảng này là bình thường; sát hơn tính là "hủy sát giờ". */
    public static final Duration HUY_SAT_GIO = Duration.ofMinutes(15);
    /** Quá hạn không đến: chưa bắt đầu sạc sau khoảng này kể từ giờ bắt đầu. */
    public static final Duration QUA_HAN_SAU = Duration.ofMinutes(15);
    /** Cổng được giữ (Đã đặt) từ trước giờ bắt đầu khoảng này (quy tắc 2). */
    public static final Duration CUA_SO_GIU_CHO = Duration.ofMinutes(15);
    /** Quy tắc 11: đủ NGUONG_VI_PHAM lần không đến/hủy sát giờ trong CHU_KY_VI_PHAM thì bị hạn chế. */
    public static final int NGUONG_VI_PHAM = 3;
    public static final Duration CHU_KY_VI_PHAM = Duration.ofDays(30);
    public static final Duration THOI_GIAN_HAN_CHE = Duration.ofHours(24);
    /** Đặt chỗ còn hiệu lực (chiếm khung giờ và tính vào giới hạn 2 đặt chỗ). */
    public static final List<String> TRANG_THAI_HIEU_LUC = List.of("Đã đặt", "Đang sạc");

    /** Sơ đồ tuần tự: kiemTraQuyDinh(30 phút–3 giờ, trước tối đa 24 giờ, loại cổng phù hợp). */
    public void kiemTra(LocalDateTime batDau, LocalDateTime ketThuc, LocalDateTime now, String loaiCong,
                        String loaiCongXe) {
        Map<String, String> loi = new LinkedHashMap<>();

        if (!ketThuc.isAfter(batDau)) {
            loi.put("gioKetThuc", "Giờ kết thúc phải sau giờ bắt đầu.");
        } else {
            Duration thoiLuong = Duration.between(batDau, ketThuc);
            if (thoiLuong.compareTo(THOI_LUONG_TOI_THIEU) < 0) {
                loi.put("gioKetThuc", "Mỗi lần đặt tối thiểu 30 phút.");
            } else if (thoiLuong.compareTo(THOI_LUONG_TOI_DA) > 0) {
                loi.put("gioKetThuc", "Mỗi lần đặt tối đa 3 giờ.");
            }
        }
        if (batDau.isBefore(now)) {
            loi.put("gioBatDau", "Không được đặt khung giờ đã qua.");
        } else if (batDau.isAfter(now.plus(DAT_TRUOC_TOI_DA))) {
            loi.put("gioBatDau", "Chỉ được đặt trước tối đa 24 giờ.");
        }
        if (!loaiCong.equals(loaiCongXe)) {
            loi.put("loaiCong", "Loại cổng đặt phải phù hợp với loại cổng của xe (" + loaiCongXe + ").");
        }

        if (!loi.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Đặt chỗ không hợp lệ.", loi);
        }
    }
}
