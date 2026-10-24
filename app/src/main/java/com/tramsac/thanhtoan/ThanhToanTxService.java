package com.tramsac.thanhtoan;

import com.tramsac.bieugia.PhiSac;
import com.tramsac.bieugia.PhiSacRepository;
import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.TrangThaiPhien;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThanhToanTxService {
    private final DonThanhToanRepository donRepository;
    private final GiaoDichThanhToanRepository giaoDichRepository;
    private final PhienSacRepository phienRepository;
    private final PhiSacRepository phiRepository;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public ThanhToanTxService(DonThanhToanRepository donRepository,
                              GiaoDichThanhToanRepository giaoDichRepository,
                              PhienSacRepository phienRepository, PhiSacRepository phiRepository,
                              MaGenerator maGenerator, Clock clock) {
        this.donRepository = donRepository;
        this.giaoDichRepository = giaoDichRepository;
        this.phienRepository = phienRepository;
        this.phiRepository = phiRepository;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    @Transactional
    public DonThanhToan taoDon(String maND, String khoa, TaoDonThanhToanRequest request) {
        PhienSac phien = phienRepository.findByMaPhienAndMaND(request.maPhien(), maND)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_PHIEN",
                        "Không tìm thấy phiên sạc."));
        phien = phienRepository.findForUpdateByMaPhien(phien.getMaPhien()).orElseThrow();
        if (phien.layTrangThai() != TrangThaiPhien.CHO_THANH_TOAN) {
            throw new ApiException(HttpStatus.CONFLICT, "PHIEN_CHUA_CHO_THANH_TOAN",
                    "Phiên sạc chưa ở trạng thái Chờ thanh toán.");
        }
        PhiSac phi = phiRepository.findByMaPhien(phien.getMaPhien())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CHUA_CO_PHI_SAC",
                        "Phiên chưa có hóa đơn phí sạc."));
        LocalDateTime now = LocalDateTime.now(clock);
        DonThanhToan don = donRepository.saveAndFlush(new DonThanhToan(
                maGenerator.next("DTT"), phien.getMaPhien(), phi.getTongPhi(), request.phuongThuc(),
                khoa, now.plusMinutes(15)));
        TrangThaiThanhToan finalOutcome = request.ketQuaCuoi() == null
                ? TrangThaiThanhToan.THANH_CONG : request.ketQuaCuoi();
        if (finalOutcome != TrangThaiThanhToan.THANH_CONG && finalOutcome != TrangThaiThanhToan.THAT_BAI) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KET_QUA_CUOI_KHONG_HOP_LE",
                    "Kết quả cuối chỉ có thể là THANH_CONG hoặc THAT_BAI.");
        }
        if (request.kichBan() != KichBanThanhToan.CHAM_PHAN_HOI
                && request.kichBan() != KichBanThanhToan.HET_HAN
                && request.doTreGiay() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DO_TRE_KHONG_AP_DUNG",
                    "Chỉ kịch bản chậm phản hồi hoặc hết hạn mới nhận doTreGiay.");
        }
        giaoDichRepository.saveAndFlush(new GiaoDichThanhToan(
                maGenerator.next("GDT"), don.getMaDon(), request.kichBan(), request.cheDoCallback(),
                finalOutcome, request.doTreGiay(), now));
        return don;
    }
}
