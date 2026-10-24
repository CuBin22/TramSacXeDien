package com.tramsac.thanhtoan;

import com.tramsac.bieugia.ChiTietPhiRepository;
import com.tramsac.bieugia.PhiSac;
import com.tramsac.bieugia.PhiSacRepository;
import com.tramsac.common.ApiException;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThanhToanService {
    private final DonThanhToanRepository donRepository;
    private final GiaoDichThanhToanRepository giaoDichRepository;
    private final PhienSacRepository phienRepository;
    private final ThanhToanTxService tx;
    private final CongThanhToan gateway;
    private final PhiSacRepository phiRepository;
    private final ChiTietPhiRepository chiTietPhiRepository;

    /** Số thẻ thử nghiệm chuẩn của các cổng thanh toán; mọi số khác đều bị từ chối và không bao giờ được lưu. */
    private static final Set<String> THE_THU_NGHIEM = Set.of("4111111111111111", "4242424242424242",
            "5555555555554444");

    public ThanhToanService(DonThanhToanRepository donRepository,
                            GiaoDichThanhToanRepository giaoDichRepository,
                            PhienSacRepository phienRepository,
                            ThanhToanTxService tx, CongThanhToan gateway,
                            PhiSacRepository phiRepository, ChiTietPhiRepository chiTietPhiRepository) {
        this.donRepository = donRepository;
        this.giaoDichRepository = giaoDichRepository;
        this.phienRepository = phienRepository;
        this.tx = tx;
        this.gateway = gateway;
        this.phiRepository = phiRepository;
        this.chiTietPhiRepository = chiTietPhiRepository;
    }

    @io.opentelemetry.instrumentation.annotations.WithSpan("thanh_toan.taoDon")
    public DonThanhToanResponse taoDon(String khoa, TaoDonThanhToanRequest request) {
        kiemTraKhoa(khoa);
        kiemTraSoThe(request);
        DonThanhToan existing = donRepository.findByKhoaIdempotency(khoa).orElse(null);
        if (existing != null) return replay(existing, request);

        DonThanhToan created;
        try {
            created = tx.taoDon(NguoiDungHienTai.layMaND(), khoa, request);
        } catch (DataIntegrityViolationException e) {
            existing = donRepository.findByKhoaIdempotency(khoa).orElse(null);
            if (existing == null) {
                throw new ApiException(HttpStatus.CONFLICT, "DON_THANH_TOAN_DANG_CHO",
                        "Phiên này đã có một đơn thanh toán chưa kết thúc.");
            }
            return replay(existing, request);
        }
        GiaoDichThanhToan giaoDich = giaoDichRepository.findByMaDon(created.getMaDon()).orElseThrow();
        gateway.taoDon(created, giaoDich);
        return DonThanhToanResponse.tu(created, giaoDich, false);
    }

    @Transactional(readOnly = true)
    public DonThanhToanResponse chiTiet(String maDon) {
        DonThanhToan don = donRepository.findById(maDon).orElseThrow(ThanhToanService::khongTimThayDon);
        if (phienRepository.findByMaPhienAndMaND(don.getMaPhien(), NguoiDungHienTai.layMaND()).isEmpty()) {
            throw khongTimThayDon();
        }
        GiaoDichThanhToan giaoDich = giaoDichRepository.findByMaDon(maDon).orElseThrow();
        return DonThanhToanResponse.tu(don, giaoDich, false);
    }

    /** UC22 bước 1 (BM22/QĐ22): tổng phí và chi tiết cách tính của phiên, chỉ chủ phiên được xem. */
    @Transactional(readOnly = true)
    public HoaDonPhienResponse hoaDon(String maPhien) {
        PhienSac phien = phienRepository.findByMaPhienAndMaND(maPhien, NguoiDungHienTai.layMaND())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_PHIEN",
                        "Không tìm thấy phiên sạc."));
        PhiSac phi = phiRepository.findByMaPhien(maPhien)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHUA_CO_PHI_SAC",
                        "Phiên chưa có hóa đơn phí sạc."));
        List<HoaDonPhienResponse.DongPhi> dong = chiTietPhiRepository.findByMaPhi(phi.getMaPhi()).stream()
                .map(c -> new HoaDonPhienResponse.DongPhi(c.getKhungGio(), c.getDienNangKWh(), c.getDonGia(),
                        c.getThanhTien()))
                .sorted(Comparator.comparing(HoaDonPhienResponse.DongPhi::khungGio))
                .toList();
        return new HoaDonPhienResponse(maPhien, phien.getTrangThai(), phi.getTongPhi(), dong);
    }

    /**
     * QĐ22: chỉ chấp nhận số thẻ thử nghiệm. Số thẻ (nếu có) chỉ được kiểm tra rồi bỏ đi: không lưu, không ghi log,
     * không gửi sang cổng thanh toán và không đưa vào thông báo lỗi.
     */
    private static void kiemTraSoThe(TaoDonThanhToanRequest request) {
        String soThe = request.soTheThuNghiem();
        if (soThe == null || soThe.isBlank()) return;
        if (request.phuongThuc() != PhuongThucThanhToan.THE_THU) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SO_THE_KHONG_AP_DUNG",
                    "Chỉ phương thức thẻ thử nghiệm mới nhận số thẻ.");
        }
        if (!THE_THU_NGHIEM.contains(soThe.replaceAll("[\\s-]", ""))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SO_THE_KHONG_PHAI_THE_THU_NGHIEM",
                    "Chỉ chấp nhận số thẻ thử nghiệm; hệ thống không nhận thẻ thật.");
        }
    }

    private DonThanhToanResponse replay(DonThanhToan don, TaoDonThanhToanRequest request) {
        if (phienRepository.findByMaPhienAndMaND(don.getMaPhien(), NguoiDungHienTai.layMaND()).isEmpty()) {
            throw khongTimThayDon();
        }
        GiaoDichThanhToan giaoDich = giaoDichRepository.findByMaDon(don.getMaDon()).orElseThrow();
        if (!don.getMaPhien().equals(request.maPhien())
                || !don.getPhuongThuc().equals(request.phuongThuc().name())
                || !giaoDich.getKichBan().equals(request.kichBan().name())
                || !giaoDich.getCheDoCallback().equals(request.cheDoCallback().name())
                || !java.util.Objects.equals(giaoDich.getDoTreGiay(), request.doTreGiay())
                || (request.ketQuaCuoi() != null && !giaoDich.getKetQuaCuoi().equals(request.ketQuaCuoi().name()))) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_DUNG_LAI",
                    "Idempotency-Key này đã được dùng với dữ liệu khác.");
        }
        gateway.taoDon(don, giaoDich);
        return DonThanhToanResponse.tu(don, giaoDich, true);
    }

    private static void kiemTraKhoa(String key) {
        try {
            if (key == null || !UUID.fromString(key).toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_KHONG_HOP_LE",
                    "Header Idempotency-Key phải là UUID.");
        }
    }

    private static ApiException khongTimThayDon() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_DON", "Không tìm thấy đơn thanh toán.");
    }
}
