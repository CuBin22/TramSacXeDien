package com.tramsac.datphien;

import com.tramsac.congsac.CauHinhCongSuat;
import com.tramsac.congsac.CauHinhCongSuatRepository;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC08 – Phân bổ cổng sạc tự động (BM7, QĐ7). Chạy TRONG giao dịch của người gọi.
 * Khóa toàn bộ cổng cùng loại (theo thứ tự MaCong) trước khi chọn: hai yêu cầu đặt cùng loại cổng
 * được xếp hàng nên không thể cùng chiếm một cổng trong cùng khung giờ.
 */
@Service
public class PhanBoCongService {

    private final CongSacRepository congSacRepository;
    private final CauHinhCongSuatRepository cauHinhRepository;
    private final DatPhienRepository datPhienRepository;

    public PhanBoCongService(CongSacRepository congSacRepository, CauHinhCongSuatRepository cauHinhRepository,
                             DatPhienRepository datPhienRepository) {
        this.congSacRepository = congSacRepository;
        this.cauHinhRepository = cauHinhRepository;
        this.datPhienRepository = datPhienRepository;
    }

    private record UngVien(CongSac cong, BigDecimal duCongSuat, long soDatChoTrongNgay) {
    }

    /**
     * Sơ đồ tuần tự: phanBoCong(maDatPhien, loaiCong, khungGio) → maCong hoặc rỗng (không có cổng hợp lệ).
     * maDatPhien được loại khỏi phép kiểm tra trùng lịch (dùng khi đổi lịch).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> phanBoCong(String maDatPhien, String loaiCong, LocalDateTime batDau,
                                       LocalDateTime ketThuc) {
        // layTheoLoai(loaiCong): khóa và nạp MỚI toàn bộ cổng cùng loại
        List<CongSac> dsCong = congSacRepository.findForUpdateByLoaiCongOrderByMaCong(loaiCong);

        // loaiCong(Lỗi, Bảo trì, Mất kết nối, trùng lịch)
        List<String> khaDung = dsCong.stream().filter(c -> !c.layTrangThai().khongKhaDung())
                .map(CongSac::getMaCong).toList();
        if (khaDung.isEmpty()) {
            return Optional.empty();
        }
        Set<String> trungLich = new HashSet<>(datPhienRepository.timCongTrungLich(
                khaDung, QuyDinhDatPhien.TRANG_THAI_HIEU_LUC, batDau, ketThuc, maDatPhien));
        List<CongSac> hopLe = dsCong.stream()
                .filter(c -> khaDung.contains(c.getMaCong()) && !trungLich.contains(c.getMaCong()))
                .toList();
        if (hopLe.isEmpty()) {
            return Optional.empty();
        }

        // xepUuTien(dư công suất, ít phiên trong ngày); hòa thì theo MaCong để kết quả xác định
        LocalDateTime dauNgay = batDau.toLocalDate().atStartOfDay();
        LocalDateTime cuoiNgay = dauNgay.plusDays(1);
        Map<String, BigDecimal> duTheoCauHinh = new HashMap<>();
        return hopLe.stream()
                .map(c -> new UngVien(c, congSuatDu(c.getMaCauHinh(), duTheoCauHinh),
                        datPhienRepository.demTrongKhoang(c.getMaCong(), QuyDinhDatPhien.TRANG_THAI_HIEU_LUC,
                                dauNgay, cuoiNgay, maDatPhien)))
                .min(Comparator.comparing(UngVien::duCongSuat, Comparator.reverseOrder())
                        .thenComparingLong(UngVien::soDatChoTrongNgay)
                        .thenComparing(u -> u.cong().getMaCong()))
                .map(u -> u.cong().getMaCong());
    }

    /** layCongSuatKhaDung(): công suất đấu nối của nhóm trừ tổng công suất đang cấp; cổng chưa có nhóm tính 0. */
    private BigDecimal congSuatDu(String maCauHinh, Map<String, BigDecimal> boNho) {
        if (maCauHinh == null) {
            return BigDecimal.ZERO;
        }
        return boNho.computeIfAbsent(maCauHinh, ma -> {
            CauHinhCongSuat cauHinh = cauHinhRepository.findById(ma).orElse(null);
            if (cauHinh == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal tong = congSacRepository.tongCongSuatDuocCap(ma);
            return cauHinh.getCongSuatDauNoi().subtract(tong == null ? BigDecimal.ZERO : tong);
        });
    }
}
