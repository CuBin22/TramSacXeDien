package com.tramsac.phiensac;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mọi thay đổi trạng thái phiên đi qua chuyenTrangThai: kiểm tra theo bảng hợp lệ, ghi lịch sử, cùng
 * giao dịch với người gọi (QĐ10).
 */
@Service
public class PhienSacService {

    private static final int MAX_LY_DO = 255;

    private final PhienSacRepository phienSacRepository;
    private final LichSuTrangThaiPhienRepository lichSuRepository;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public PhienSacService(PhienSacRepository phienSacRepository, LichSuTrangThaiPhienRepository lichSuRepository,
                           MaGenerator maGenerator, Clock clock) {
        this.phienSacRepository = phienSacRepository;
        this.lichSuRepository = lichSuRepository;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    /** Sơ đồ trạng thái: taoPhien() [đặt chỗ hợp lệ] → Đã đặt. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PhienSac taoPhien(String maDatPhien, String maCong, String nguonThaoTac) {
        PhienSac phien = phienSacRepository.save(new PhienSac(maGenerator.next("PS"), maDatPhien, maCong));
        ghiLichSu(phien, null, TrangThaiPhien.DA_DAT, nguonThaoTac, "Đặt chỗ");
        return phien;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void chuyenTrangThai(PhienSac phien, TrangThaiPhien moi, String nguonThaoTac, String lyDo) {
        TrangThaiPhien cu = phien.layTrangThai();
        try {
            phien.chuyenTrangThai(moi);
        } catch (IllegalStateException e) {
            throw new ApiException(HttpStatus.CONFLICT, "CHUYEN_TRANG_THAI_KHONG_HOP_LE", e.getMessage());
        }
        if (moi == TrangThaiPhien.CHO) phien.danhDauCho(LocalDateTime.now(clock));
        if (moi == TrangThaiPhien.DANG_SAC) phien.danhDauBatDau(LocalDateTime.now(clock));
        phienSacRepository.save(phien);
        ghiLichSu(phien, cu, moi, nguonThaoTac, lyDo);
    }

    private void ghiLichSu(PhienSac phien, TrangThaiPhien cu, TrangThaiPhien moi, String nguon, String lyDo) {
        lichSuRepository.save(new LichSuTrangThaiPhien(maGenerator.next("LT"), phien.getMaPhien(),
                LocalDateTime.now(clock), cu == null ? null : cu.giaTri(), moi.giaTri(), nguon, cat(lyDo)));
    }

    private static String cat(String s) {
        return (s != null && s.length() > MAX_LY_DO) ? s.substring(0, MAX_LY_DO) : s;
    }
}
