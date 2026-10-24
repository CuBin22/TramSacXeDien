package com.tramsac.datphien;

import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.TrangThaiCong;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC06 – Kiểm tra xung đột lịch (BM6, QĐ6). Không thay đổi dữ liệu. */
@Service
public class KiemTraXungDotService {

    private static final List<String> TRANG_THAI_KHONG_KHA_DUNG = Arrays.stream(TrangThaiCong.values())
            .filter(TrangThaiCong::khongKhaDung).map(TrangThaiCong::giaTri).toList();

    private final DatPhienRepository datPhienRepository;
    private final CongSacRepository congSacRepository;

    public KiemTraXungDotService(DatPhienRepository datPhienRepository, CongSacRepository congSacRepository) {
        this.datPhienRepository = datPhienRepository;
        this.congSacRepository = congSacRepository;
    }

    /**
     * Sơ đồ tuần tự: kiemTraXungDot(maND, loaiCong, khungGio, maDatPhienLoaiTru).
     * maDatPhienLoaiTru là đặt chỗ đang được đổi lịch (null hoặc rỗng nếu đặt mới).
     */
    @Transactional
    public KetQuaXungDot kiemTraXungDot(String maND, String loaiCong, LocalDateTime batDau, LocalDateTime ketThuc,
                                        String maDatPhienLoaiTru) {
        String loaiTru = maDatPhienLoaiTru == null ? "" : maDatPhienLoaiTru;
        if (!datPhienRepository.timDatChoTrung(maND, QuyDinhDatPhien.TRANG_THAI_HIEU_LUC, batDau, ketThuc, loaiTru)
                .isEmpty()) {
            return KetQuaXungDot.TRUNG_LICH;
        }
        return demCongKhaDung(loaiCong, batDau, ketThuc, loaiTru) == 0
                ? KetQuaXungDot.HET_CONG : KetQuaXungDot.KHONG_XUNG_DOT;
    }

    /** Sơ đồ tuần tự: demCongKhaDung(loaiCong, khungGio). */
    @Transactional
    public int demCongKhaDung(String loaiCong, LocalDateTime batDau, LocalDateTime ketThuc, String loaiTru) {
        // Chỉ lấy mã bằng truy vấn chiếu: không nạp CongSac vào bộ nhớ đệm trước khi PhanBoCongService khóa chúng
        List<String> khaDung = congSacRepository.timMaCongKhaDung(loaiCong, TRANG_THAI_KHONG_KHA_DUNG);
        if (khaDung.isEmpty()) {
            return 0;
        }
        Set<String> trungLich = new HashSet<>(datPhienRepository.timCongTrungLich(
                khaDung, QuyDinhDatPhien.TRANG_THAI_HIEU_LUC, batDau, ketThuc, loaiTru));
        return (int) khaDung.stream().filter(ma -> !trungLich.contains(ma)).count();
    }
}
