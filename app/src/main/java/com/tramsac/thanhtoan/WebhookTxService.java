package com.tramsac.thanhtoan;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.PhienSacService;
import com.tramsac.phiensac.TrangThaiPhien;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookTxService {
    private final GiaoDichThanhToanRepository giaoDichRepository;
    private final DonThanhToanRepository donRepository;
    private final CallbackLogRepository callbackRepository;
    private final PhienSacRepository phienRepository;
    private final PhienSacService phienService;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public WebhookTxService(GiaoDichThanhToanRepository giaoDichRepository,
                            DonThanhToanRepository donRepository,
                            CallbackLogRepository callbackRepository,
                            PhienSacRepository phienRepository,
                            PhienSacService phienService, MaGenerator maGenerator, Clock clock) {
        this.giaoDichRepository = giaoDichRepository;
        this.donRepository = donRepository;
        this.callbackRepository = callbackRepository;
        this.phienRepository = phienRepository;
        this.phienService = phienService;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    @Transactional
    @io.opentelemetry.instrumentation.annotations.WithSpan("thanh_toan.xuLyCallback")
    public WebhookThanhToanResponse xuLy(WebhookThanhToanEvent event, String signature) {
        GiaoDichThanhToan giaoDich = giaoDichRepository.findForUpdateByMaGiaoDich(event.maGiaoDich()).orElse(null);
        if (giaoDich == null) {
            ghi(null, event.suKien(), signature, "HOP_LE", "Bỏ qua");
            return new WebhookThanhToanResponse("KHONG_TIM_THAY", event.maGiaoDich());
        }
        DonThanhToan don = donRepository.findForUpdateByMaDon(giaoDich.getMaDon()).orElse(null);
        if (don == null || !giaoDich.getMaDon().equals(event.maDon())
                || event.soTien() == null || don.getSoTien().compareTo(event.soTien()) != 0) {
            ghi(giaoDich.getMaGiaoDich(), event.suKien(), signature, "HOP_LE", "Bỏ qua");
            return new WebhookThanhToanResponse("DU_LIEU_SAI", event.maGiaoDich());
        }

        TrangThaiThanhToan next;
        try {
            next = TrangThaiThanhToan.tuSuKien(event.suKien());
        } catch (IllegalArgumentException e) {
            ghi(giaoDich.getMaGiaoDich(), event.suKien(), signature, "HOP_LE", "Bỏ qua");
            return new WebhookThanhToanResponse("KHONG_HOP_LE", event.maGiaoDich());
        }
        if (callbackRepository.existsByMaGiaoDichAndSuKienAndKetQuaXuLy(
                giaoDich.getMaGiaoDich(), next.name(), "Mới")) {
            ghi(giaoDich.getMaGiaoDich(), next.name(), signature, "HOP_LE", "Trùng");
            return new WebhookThanhToanResponse("TRUNG", event.maGiaoDich());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (giaoDich.layKetQua().ketThuc()) {
            // Giao dịch đã kết thúc: sự kiện sớm hơn là "Sai thứ tự", sự kiện kết thúc khác chỉ được ghi nhận "Bỏ qua".
            ghi(giaoDich.getMaGiaoDich(), next.name(), signature, "HOP_LE", next.ketThuc() ? "Bỏ qua" : "Sai thứ tự");
            return new WebhookThanhToanResponse(next.ketThuc() ? "DA_KET_THUC" : "SAI_THU_TU", event.maGiaoDich());
        }
        if (don.getHanThanhToan() != null && !now.isBefore(don.getHanThanhToan())) {
            ketThucHetHan(don, giaoDich);
            ghi(giaoDich.getMaGiaoDich(), next.name(), signature, "HOP_LE", "Bỏ qua");
            return new WebhookThanhToanResponse("DON_HET_HAN", event.maGiaoDich());
        }
        if (hang(next) < hang(giaoDich.layKetQua()) || next == giaoDich.layKetQua()) {
            ghi(giaoDich.getMaGiaoDich(), next.name(), signature, "HOP_LE", "Sai thứ tự");
            return new WebhookThanhToanResponse("SAI_THU_TU", event.maGiaoDich());
        }

        if (next == TrangThaiThanhToan.THANH_CONG) {
            PhienSac phien = phienRepository.findForUpdateByMaPhien(don.getMaPhien()).orElseThrow();
            if (phien.layTrangThai() != TrangThaiPhien.CHO_THANH_TOAN) {
                throw new ApiException(HttpStatus.CONFLICT, "PHIEN_KHONG_CHO_THANH_TOAN",
                        "Không thể hoàn tất thanh toán vì phiên không còn Chờ thanh toán.");
            }
            phienService.chuyenTrangThai(phien, TrangThaiPhien.HOAN_TAT, "Thanh toán", "Giao dịch thành công.");
        }
        giaoDich.capNhatKetQua(next);
        don.capNhatTrangThai(next);
        ghi(giaoDich.getMaGiaoDich(), next.name(), signature, "HOP_LE", "Mới");
        return new WebhookThanhToanResponse("DA_XU_LY", event.maGiaoDich());
    }

    private void ketThucHetHan(DonThanhToan don, GiaoDichThanhToan giaoDich) {
        don.capNhatTrangThai(TrangThaiThanhToan.HET_HAN);
        giaoDich.capNhatKetQua(TrangThaiThanhToan.HET_HAN);
    }

    private void ghi(String maGiaoDich, String suKien, String signature, String auth, String result) {
        callbackRepository.save(new CallbackLog(maGenerator.next("CB"), maGiaoDich,
                cat(suKien, 30), cat(signature, 255), auth, result, LocalDateTime.now(clock)));
    }

    private static int hang(TrangThaiThanhToan status) {
        return switch (status) {
            case CHO -> 0;
            case DANG_XU_LY -> 1;
            case THANH_CONG, THAT_BAI, HET_HAN, DA_HOAN -> 2;
        };
    }

    private static String cat(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
