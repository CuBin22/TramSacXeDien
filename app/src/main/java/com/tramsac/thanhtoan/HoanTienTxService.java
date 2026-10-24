package com.tramsac.thanhtoan;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.TrangThaiPhien;
import com.tramsac.suco.SuCoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoanTienTxService {
    private static final Set<String> LY_DO_HOP_LE = Set.of("Hủy phiên", "Thiết bị lỗi");
    private final GiaoDichThanhToanRepository giaoDichRepository;
    private final DonThanhToanRepository donRepository;
    private final PhienSacRepository phienRepository;
    private final HoanTienRepository hoanTienRepository;
    private final SuCoService suCoService;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public HoanTienTxService(GiaoDichThanhToanRepository giaoDichRepository,
                             DonThanhToanRepository donRepository,
                             PhienSacRepository phienRepository, HoanTienRepository hoanTienRepository,
                             SuCoService suCoService, MaGenerator maGenerator, Clock clock) {
        this.giaoDichRepository = giaoDichRepository;
        this.donRepository = donRepository;
        this.phienRepository = phienRepository;
        this.hoanTienRepository = hoanTienRepository;
        this.suCoService = suCoService;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void kiemTra(String maGiaoDich, BigDecimal amount, String reason) {
        validate(maGiaoDich, amount, reason);
    }

    @Transactional
    public HoanTien tao(String maGiaoDich, BigDecimal amount, String reason, String key, String actor) {
        GiaoDichThanhToan giaoDich = giaoDichRepository.findForUpdateByMaGiaoDich(maGiaoDich)
                .orElseThrow(HoanTienTxService::khongTimThayGiaoDich);
        DonThanhToan don = donRepository.findForUpdateByMaDon(giaoDich.getMaDon()).orElseThrow();
        PhienSac phien = phienRepository.findForUpdateByMaPhien(don.getMaPhien()).orElseThrow();
        validate(giaoDich, don, phien, amount, reason);
        HoanTien refund = hoanTienRepository.saveAndFlush(new HoanTien(maGenerator.next("HT"), maGiaoDich,
                amount, reason, key, actor, LocalDateTime.now(clock)));
        don.capNhatTrangThai(TrangThaiThanhToan.DA_HOAN);
        return refund;
    }

    private void validate(String maGiaoDich, BigDecimal amount, String reason) {
        GiaoDichThanhToan giaoDich = giaoDichRepository.findById(maGiaoDich)
                .orElseThrow(HoanTienTxService::khongTimThayGiaoDich);
        DonThanhToan don = donRepository.findById(giaoDich.getMaDon()).orElseThrow();
        PhienSac phien = phienRepository.findById(don.getMaPhien()).orElseThrow();
        validate(giaoDich, don, phien, amount, reason);
    }

    private void validate(GiaoDichThanhToan giaoDich, DonThanhToan don, PhienSac phien,
                          BigDecimal amount, String reason) {
        if (hoanTienRepository.findByMaGiaoDich(giaoDich.getMaGiaoDich()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "GIAO_DICH_DA_HOAN", "Giao dịch này đã được hoàn tiền.");
        }
        if (giaoDich.layKetQua() != TrangThaiThanhToan.THANH_CONG
                || don.layTrangThai() != TrangThaiThanhToan.THANH_CONG) {
            throw new ApiException(HttpStatus.CONFLICT, "GIAO_DICH_CHUA_THANH_CONG",
                    "Chỉ giao dịch thanh toán thành công mới được hoàn tiền.");
        }
        if (phien.layTrangThai() != TrangThaiPhien.HOAN_TAT) {
            throw new ApiException(HttpStatus.CONFLICT, "PHIEN_CHUA_HOAN_TAT",
                    "Chỉ phiên Hoàn tất mới được hoàn tiền.");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(don.getSoTien()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SO_TIEN_HOAN_KHONG_HOP_LE",
                    "Số tiền hoàn phải lớn hơn 0 và không vượt quá số tiền đã thanh toán.");
        }
        if (!LY_DO_HOP_LE.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LY_DO_HOAN_KHONG_HOP_LE",
                    "Lý do hoàn tiền phải là 'Hủy phiên' hoặc 'Thiết bị lỗi'.");
        }
        if ("Thiết bị lỗi".equals(reason) && !suCoService.coSuCoTrenCong(phien.getMaCong())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHUA_CO_SU_CO_LIEN_QUAN",
                    "Cần có sự cố đã ghi nhận trên cổng trước khi hoàn tiền vì thiết bị lỗi.");
        }
    }

    private static ApiException khongTimThayGiaoDich() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_GIAO_DICH", "Không tìm thấy giao dịch.");
    }
}
