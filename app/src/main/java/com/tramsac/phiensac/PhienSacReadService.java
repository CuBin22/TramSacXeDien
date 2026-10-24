package com.tramsac.phiensac;

import com.tramsac.bieugia.PhiSacRepository;
import com.tramsac.bieugia.TinhPhiService;
import com.tramsac.common.ApiException;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.nguoidung.VaiTro;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC13 / BM12 / QĐ12: người dùng chỉ xem phiên của chính mình; Nhân viên trực và Quản trị xem phiên của mọi người
 * dùng. Phiên đang sạc kèm phí tạm tính (FR19).
 */
@Service
public class PhienSacReadService {
    private final PhienSacRepository phienRepository;
    private final PhiSacRepository phiRepository;
    private final DatPhienRepository datPhienRepository;
    private final TinhPhiService tinhPhiService;
    private final Clock clock;

    public PhienSacReadService(PhienSacRepository phienRepository, PhiSacRepository phiRepository,
                               DatPhienRepository datPhienRepository, TinhPhiService tinhPhiService, Clock clock) {
        this.phienRepository = phienRepository; this.phiRepository = phiRepository;
        this.datPhienRepository = datPhienRepository; this.tinhPhiService = tinhPhiService; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PhienSacResponse> danhSach() {
        return danhSach(null, null, null);
    }

    /** Lọc theo khoảng ngày (tính theo thời điểm bắt đầu sạc, hoặc giờ bắt đầu đặt chỗ nếu chưa sạc) và trạng thái. */
    @Transactional(readOnly = true)
    public List<PhienSacResponse> danhSach(LocalDate tuNgay, LocalDate denNgay, String trangThai) {
        if (tuNgay != null && denNgay != null && denNgay.isBefore(tuNgay)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KHOANG_THOI_GIAN_KHONG_HOP_LE",
                    "Đến ngày không được trước từ ngày.");
        }
        String trangThaiLoc = null;
        if (trangThai != null && !trangThai.isBlank()) {
            try {
                trangThaiLoc = TrangThaiPhien.tuGiaTri(trangThai.trim()).giaTri();
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TRANG_THAI_PHIEN_KHONG_HOP_LE",
                        "Trạng thái phiên không hợp lệ.");
            }
        }
        List<PhienSac> phien = NguoiDungHienTai.layVaiTro() == VaiTro.NGUOI_DUNG
                ? phienRepository.findByMaND(NguoiDungHienTai.layMaND())
                : phienRepository.findAll();
        final String loc = trangThaiLoc;
        return phien.stream()
                .filter(p -> loc == null || loc.equals(p.getTrangThai()))
                .filter(p -> trongKhoang(p, tuNgay, denNgay))
                .sorted(Comparator.comparing(this::moc, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public PhienSacResponse chiTiet(String maPhien) {
        if (NguoiDungHienTai.layVaiTro() != VaiTro.NGUOI_DUNG) {
            return phienRepository.findById(maPhien).map(this::response).orElseThrow(() -> khongTimThay());
        }
        return phienRepository.findByMaPhienAndMaND(maPhien, NguoiDungHienTai.layMaND())
                .map(this::response).orElseThrow(() -> khongTimThay());
    }

    @Transactional(readOnly = true)
    public boolean coQuyenTruyCap(String maPhien) {
        if (NguoiDungHienTai.layVaiTro() != VaiTro.NGUOI_DUNG) {
            return phienRepository.existsById(maPhien);
        }
        return phienRepository.findByMaPhienAndMaND(maPhien, NguoiDungHienTai.layMaND()).isPresent();
    }

    @Transactional(readOnly = true)
    public String layMaCong(String maPhien) {
        return phienRepository.findById(maPhien).map(PhienSac::getMaCong)
                .orElseThrow(() -> khongTimThay());
    }

    private LocalDateTime moc(PhienSac p) {
        if (p.getBatDauLuc() != null) return p.getBatDauLuc();
        return datPhienRepository.findById(p.getMaDatPhien()).map(DatPhien::getGioBatDau).orElse(null);
    }

    private boolean trongKhoang(PhienSac p, LocalDate tuNgay, LocalDate denNgay) {
        if (tuNgay == null && denNgay == null) return true;
        LocalDateTime m = moc(p);
        if (m == null) return false;
        LocalDate ngay = m.toLocalDate();
        return (tuNgay == null || !ngay.isBefore(tuNgay)) && (denNgay == null || !ngay.isAfter(denNgay));
    }

    private PhienSacResponse response(PhienSac p) {
        return PhienSacResponse.tu(p, phiRepository.findByMaPhien(p.getMaPhien()).orElse(null),
                tinhPhiService.tinhPhiTamTinh(p, LocalDateTime.now(clock)));
    }

    private static ApiException khongTimThay() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_PHIEN", "Không tìm thấy phiên sạc.");
    }
}
