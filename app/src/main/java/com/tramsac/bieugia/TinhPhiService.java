package com.tramsac.bieugia;

import com.tramsac.common.MaGenerator;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.NguoiDungRepository;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.TrangThaiPhien;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC14–15: tính một hóa đơn duy nhất, chia điện năng theo thời lượng thực ở từng khung giá. */
@Service
public class TinhPhiService {
    private static final BigDecimal KWH_SCALE = new BigDecimal("0.001");
    private static final BigDecimal MONEY_SCALE = new BigDecimal("0.01");
    private final PhiSacRepository phiRepository;
    private final ChiTietPhiRepository chiTietPhiRepository;
    private final BieuGiaRepository bieuGiaRepository;
    private final ChiTietBieuGiaRepository chiTietBieuGiaRepository;
    private final DatPhienRepository datPhienRepository;
    private final CongSacRepository congSacRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public TinhPhiService(PhiSacRepository phiRepository, ChiTietPhiRepository chiTietPhiRepository,
                          BieuGiaRepository bieuGiaRepository, ChiTietBieuGiaRepository chiTietBieuGiaRepository,
                          DatPhienRepository datPhienRepository, CongSacRepository congSacRepository,
                          NguoiDungRepository nguoiDungRepository, MaGenerator maGenerator, Clock clock) {
        this.phiRepository = phiRepository; this.chiTietPhiRepository = chiTietPhiRepository;
        this.bieuGiaRepository = bieuGiaRepository; this.chiTietBieuGiaRepository = chiTietBieuGiaRepository;
        this.datPhienRepository = datPhienRepository; this.congSacRepository = congSacRepository;
        this.nguoiDungRepository = nguoiDungRepository; this.maGenerator = maGenerator; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @io.opentelemetry.instrumentation.annotations.WithSpan("phi.tinhPhi")
    public PhiSac tinhPhi(PhienSac phien) {
        return phiRepository.findByMaPhien(phien.getMaPhien()).orElseGet(() -> tinhMoi(phien));
    }

    /**
     * FR19 / UC13 / BM21: phí tạm tính của phiên đang sạc = công suất cổng × thời gian đã sạc × đơn giá của từng
     * khung giờ, làm tròn đến đồng. Chỉ để hiển thị (luôn gắn nhãn "Tạm tính"), không lưu và không thay đổi dữ liệu.
     * Trả về null khi phiên chưa/không còn ở Đang sạc hoặc chưa có biểu giá phù hợp.
     */
    @Transactional(readOnly = true)
    public PhiTamTinh tinhPhiTamTinh(PhienSac phien, LocalDateTime now) {
        if (phien.layTrangThai() != TrangThaiPhien.DANG_SAC || phien.getBatDauLuc() == null
                || !now.isAfter(phien.getBatDauLuc())) {
            return null;
        }
        DatPhien datPhien = datPhienRepository.findById(phien.getMaDatPhien()).orElse(null);
        CongSac cong = congSacRepository.findById(phien.getMaCong()).orElse(null);
        if (datPhien == null || cong == null) return null;
        NguoiDung nguoiDung = nguoiDungRepository.findById(datPhien.getMaND()).orElse(null);
        BieuGia bieuGia = bieuGiaRepository.findTopByNgayApDungLessThanEqualOrderByNgayApDungDesc(
                LocalDate.now(clock)).orElse(null);
        if (nguoiDung == null || bieuGia == null) return null;
        List<ChiTietBieuGia> rates = chiTietBieuGiaRepository.findByMaBieuGiaAndLoaiCongAndDoiTuong(
                bieuGia.getMaBieuGia(), cong.getLoaiCong(), nguoiDung.getDoiTuong());
        if (rates.isEmpty()) return null;
        BigDecimal congSuat = cong.getCongSuatDuocCap() != null && cong.getCongSuatDuocCap().signum() > 0
                ? cong.getCongSuatDuocCap() : cong.getCongSuatDinhMuc();
        long giay = Duration.between(phien.getBatDauLuc(), now).getSeconds();
        BigDecimal kwh = congSuat.multiply(BigDecimal.valueOf(giay))
                .divide(BigDecimal.valueOf(3600), 3, RoundingMode.HALF_UP);
        try {
            List<Segment> segments = chiaKhung(phien.getBatDauLuc(), now, rates);
            long tongGiay = segments.stream().mapToLong(Segment::seconds).sum();
            BigDecimal tien = BigDecimal.ZERO;
            for (Segment s : segments) {
                BigDecimal phanKwh = kwh.multiply(BigDecimal.valueOf(s.seconds()))
                        .divide(BigDecimal.valueOf(tongGiay), 6, RoundingMode.HALF_UP);
                tien = tien.add(phanKwh.multiply(s.rate().getDonGia()));
            }
            return new PhiTamTinh(giay, congSuat, kwh, tien.setScale(0, RoundingMode.HALF_UP));
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private PhiSac tinhMoi(PhienSac phien) {
        DatPhien datPhien = datPhienRepository.findById(phien.getMaDatPhien()).orElseThrow();
        CongSac cong = congSacRepository.findById(phien.getMaCong()).orElseThrow();
        NguoiDung nguoiDung = nguoiDungRepository.findById(datPhien.getMaND()).orElseThrow();
        BieuGia bieuGia = bieuGiaRepository.findTopByNgayApDungLessThanEqualOrderByNgayApDungDesc(
                LocalDate.now(clock)).orElseThrow(() -> new IllegalStateException("Chưa cấu hình biểu giá áp dụng."));
        List<ChiTietBieuGia> rates = chiTietBieuGiaRepository.findByMaBieuGiaAndLoaiCongAndDoiTuong(
                bieuGia.getMaBieuGia(), cong.getLoaiCong(), nguoiDung.getDoiTuong());
        if (rates.isEmpty()) throw new IllegalStateException("Không tìm thấy mức giá cho loại cổng và đối tượng này.");
        List<Segment> segments = chiaKhung(phien.getBatDauLuc(), phien.getKetThucLuc(), rates);
        BigDecimal totalEnergy = phien.getDienNangKWh().setScale(3, RoundingMode.HALF_UP);
        BigDecimal totalMoney = BigDecimal.ZERO;
        PhiSac phi = new PhiSac(maGenerator.next("PHI"), phien.getMaPhien(), bieuGia.getMaBieuGia(),
                BigDecimal.ZERO, LocalDateTime.now(clock));
        phiRepository.saveAndFlush(phi);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            BigDecimal kwh = i == segments.size() - 1 ? totalEnergy.subtract(allocated)
                    : totalEnergy.multiply(BigDecimal.valueOf(s.seconds)).divide(
                            BigDecimal.valueOf(Duration.between(phien.getBatDauLuc(), phien.getKetThucLuc()).getSeconds()),
                            3, RoundingMode.HALF_UP);
            allocated = allocated.add(kwh);
            BigDecimal amount = kwh.multiply(s.rate.getDonGia()).setScale(2, RoundingMode.HALF_UP);
            totalMoney = totalMoney.add(amount);
            chiTietPhiRepository.save(new ChiTietPhi(maGenerator.next("CTP"), phi.getMaPhi(),
                    s.rate.getKhungGio(), kwh, s.rate.getDonGia(), amount));
        }
        phi.capNhatTongPhi(totalMoney.setScale(0, RoundingMode.HALF_UP));
        phiRepository.save(phi);
        return phi;
    }

    private static List<Segment> chiaKhung(LocalDateTime start, LocalDateTime end, List<ChiTietBieuGia> rates) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalStateException("Thời gian phiên không hợp lệ.");
        if (start.equals(end)) {
            ChiTietBieuGia rate = timGia(rates, start.toLocalTime());
            return List.of(new Segment(rate, 1));
        }
        List<Segment> result = new ArrayList<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            ChiTietBieuGia rate = timGia(rates, cursor.toLocalTime());
            LocalDateTime next = end;
            for (ChiTietBieuGia candidate : rates) {
                if (candidate.getGioBatDau() == null || candidate.getGioKetThuc() == null
                        || candidate.getGioBatDau().equals(candidate.getGioKetThuc())) continue;
                for (LocalDate day : List.of(cursor.toLocalDate(), cursor.toLocalDate().plusDays(1))) {
                    for (LocalTime boundary : List.of(candidate.getGioBatDau(), candidate.getGioKetThuc())) {
                        LocalDateTime point = day.atTime(boundary);
                        if (point.isAfter(cursor) && point.isBefore(next)) next = point;
                    }
                }
            }
            long seconds = Math.max(1, Duration.between(cursor, next).getSeconds());
            result.add(new Segment(rate, seconds));
            cursor = next;
        }
        return result;
    }

    private static ChiTietBieuGia timGia(List<ChiTietBieuGia> rates, LocalTime time) {
        return rates.stream().filter(r -> r.apDungLuc(time)).min(Comparator.comparing(ChiTietBieuGia::getMaCTBG))
                .orElseThrow(() -> new IllegalStateException("Biểu giá chưa phủ kín giờ sạc " + time + "."));
    }

    private record Segment(ChiTietBieuGia rate, long seconds) { }

}
