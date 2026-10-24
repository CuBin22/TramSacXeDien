package com.tramsac.phiensac;

import com.tramsac.bieugia.TinhPhiService;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.MaGenerator;
import com.tramsac.congsac.CanBangTaiService;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.CauHinhCongSuatRepository;
import com.tramsac.congsac.LichSuKetNoi;
import com.tramsac.congsac.LichSuKetNoiRepository;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.datphien.GiuChoService;
import com.tramsac.datphien.TrangThaiDatPhien;
import com.tramsac.suco.SuCoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceRecoveryTxService {
    public record KetQuaPhucHoi(boolean daPhucHoi, boolean canXuLySuCo) { }
    private static final List<String> ACTIVE = List.of(TrangThaiPhien.DA_DAT.giaTri(), TrangThaiPhien.CHO.giaTri(),
            TrangThaiPhien.DANG_SAC.giaTri());
    private final CongSacRepository congRepository;
    private final CauHinhCongSuatRepository configRepository;
    private final PhienSacRepository phienRepository;
    private final DatPhienRepository datRepository;
    private final LichSuKetNoiRepository lichSuRepository;
    private final PhienSacService phienService;
    private final TinhPhiService tinhPhiService;
    private final GiuChoService giuChoService;
    private final CanBangTaiService canBangTaiService;
    private final SuCoService suCoService;
    private final AuditService audit;
    private final Clock clock;
    private final MaGenerator maGenerator;
    private final LenhDieuKhienRepository commands;
    public DeviceRecoveryTxService(CongSacRepository congRepository, CauHinhCongSuatRepository configRepository,
                                   PhienSacRepository phienRepository,
                                   DatPhienRepository datRepository, LichSuKetNoiRepository lichSuRepository,
                                   PhienSacService phienService, TinhPhiService tinhPhiService,
                                   GiuChoService giuChoService, CanBangTaiService canBangTaiService,
                                   SuCoService suCoService, AuditService audit, Clock clock, MaGenerator maGenerator,
                                   LenhDieuKhienRepository commands) {
        this.commands = commands;
        this.congRepository = congRepository; this.configRepository = configRepository; this.phienRepository = phienRepository;
        this.datRepository = datRepository; this.lichSuRepository = lichSuRepository;
        this.phienService = phienService; this.tinhPhiService = tinhPhiService;
        this.giuChoService = giuChoService; this.canBangTaiService = canBangTaiService;
        this.suCoService = suCoService; this.audit = audit; this.clock = clock; this.maGenerator = maGenerator;
    }

    @Transactional
    public boolean heartbeat(String maCong, boolean coXe) {
        CongSac cong = congRepository.findForUpdateByMaCong(maCong).orElse(null);
        if (cong == null) return false;
        boolean lost = cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.MAT_KET_NOI;
        cong.capNhatHeartbeat(LocalDateTime.now(clock));
        cong.capNhatXe(coXe, LocalDateTime.now(clock));
        return lost;
    }

    @Transactional
    public KetQuaPhucHoi phucHoi(String maCong, String trangThaiThuc, BigDecimal dienNang, boolean coXe) {
        return phucHoi(maCong, trangThaiThuc, dienNang, coXe, "");
    }

    @Transactional
    public KetQuaPhucHoi phucHoi(String maCong, String trangThaiThuc, BigDecimal dienNang, boolean coXe, String correlation) {
        LocalDateTime now = LocalDateTime.now(clock);
        java.util.Map<String, PhienSac> sessions = new java.util.LinkedHashMap<>();
        java.util.Map<String, DatPhien> bookings = new java.util.LinkedHashMap<>();
        for (String maPhien : phienRepository.timMaPhienTheoCongVaTrangThai(maCong, ACTIVE)) {
            String maDat = phienRepository.timMaDatPhien(maPhien).orElse(null);
            DatPhien dp = maDat == null ? null : datRepository.findForUpdateByMaDatPhien(maDat).orElse(null);
            PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien).orElse(null);
            if (phien != null && dp != null) { sessions.put(maPhien, phien); bookings.put(maPhien, dp); }
        }
        CongSacRepository.CauHinhProjection projection = congRepository.findCauHinhByMaCong(maCong).orElse(null);
        CongSac cong;
        if (projection != null && projection.getMaCauHinh() != null) {
            configRepository.findForUpdateByMaCauHinh(projection.getMaCauHinh());
            cong = congRepository.findForUpdateByMaCauHinhOrderByMaCong(projection.getMaCauHinh()).stream()
                    .filter(c -> c.getMaCong().equals(maCong)).findFirst().orElse(null);
        } else cong = congRepository.findForUpdateByMaCong(maCong).orElse(null);
        if (cong == null) {
            return new KetQuaPhucHoi(false, false);
        }
        cong.capNhatXe(coXe, now);
        boolean lost = cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.MAT_KET_NOI;
        boolean late = correlation != null && correlation.startsWith("LATE-")
                && commands.findById(correlation.substring(5))
                    .filter(c -> "Thất bại".equals(c.getTrangThai()) && sessions.containsKey(c.getMaPhien()))
                    .isPresent();
        if (!lost && !late) {
            return new KetQuaPhucHoi(false, false);
        }
        // A delayed reply must never reopen a port locked for safety or maintenance.
        if (!lost && (cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.LOI
                || cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.BAO_TRI)) {
            return new KetQuaPhucHoi(false, "Đang sạc".equals(trangThaiThuc));
        }
        if ("Đang sạc".equals(trangThaiThuc)) {
            try { canBangTaiService.kiemTraKhaNangBatDau(cong); }
            catch (IllegalStateException capacity) {
                cong.baoLoi(now);
                return new KetQuaPhucHoi(false, true);
            }
        }
        LocalDateTime matLuc = cong.getCapNhatLuc() == null ? now : cong.getCapNhatLuc();
        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.max(0, Duration.between(matLuc, now).toSeconds()));
        if (!lost) cong.matKetNoi(now); // Reuse the validated device-state transition without a false connection event.
        cong.phucHoi(trangThaiThuc, now);
        if (lost) lichSuRepository.save(new LichSuKetNoi(maGenerator.next("LSKN"), maCong, now, "Khôi phục", seconds));
        boolean fault = "Lỗi".equals(trangThaiThuc);
        if ("Đang sạc".equals(trangThaiThuc)) {
            PhienSac phien = sessions.values().stream().filter(p -> p.layTrangThai() == TrangThaiPhien.DANG_SAC)
                    .findFirst().orElseGet(() -> sessions.values().stream()
                            .filter(p -> p.layTrangThai() == TrangThaiPhien.CHO).findFirst()
                            .orElseGet(() -> sessions.values().stream().filter(p -> {
                                DatPhien d = bookings.get(p.getMaPhien());
                                return p.layTrangThai() == TrangThaiPhien.DA_DAT && d != null
                                        && !d.getGioBatDau().isAfter(now) && d.getGioKetThuc().isAfter(now);
                            }).findFirst().orElse(null)));
            if (phien == null) {
                cong.baoLoi(now);
                fault = true;
            } else {
                if (phien == null || phien.isKhoaPhien()) {
                    cong.baoLoi(now); fault = true;
                } else if (phien.layTrangThai() == TrangThaiPhien.DA_DAT || phien.layTrangThai() == TrangThaiPhien.CHO) {
                    DatPhien dp = bookings.get(phien.getMaPhien());
                    if (dp == null) { cong.baoLoi(now); fault = true; }
                    else {
                        if (phien.layTrangThai() == TrangThaiPhien.DA_DAT) {
                            phienService.chuyenTrangThai(phien, TrangThaiPhien.CHO, "Thiết bị", "Khôi phục: thiết bị đang sạc");
                        }
                        phienService.chuyenTrangThai(phien, TrangThaiPhien.DANG_SAC, "Thiết bị", "Khôi phục: thiết bị đang sạc");
                        if (dp.layTrangThai() == TrangThaiDatPhien.DA_DAT) dp.batDauSac();
                    }
                }
            }
        } else if ("Rảnh".equals(trangThaiThuc)) {
            for (String maPhien : sessions.keySet()) {
                PhienSac phien = sessions.get(maPhien);
                if (phien == null || phien.layTrangThai() != TrangThaiPhien.DANG_SAC) continue;
                DatPhien dp = bookings.get(maPhien);
                if (dp == null) continue;
                BigDecimal meter = dienNang == null ? BigDecimal.ZERO : dienNang.max(BigDecimal.ZERO);
                phienService.chuyenTrangThai(phien, TrangThaiPhien.KET_THUC_SAC, "Thiết bị", "Khôi phục: thiết bị đã dừng");
                phien.danhDauKetThuc(now, meter);
                phienService.chuyenTrangThai(phien, TrangThaiPhien.CHO_THANH_TOAN, "Hệ thống", "Khôi phục và tính phí");
                dp.hoanTatSac();
                tinhPhiService.tinhPhi(phien);
                audit.ghi(dp.getMaND(), "PHUC_HOI_PHIEN_SAC", "Phiên " + maPhien + " đã kết thúc theo trạng thái thiết bị.");
            }
            giuChoService.doiChieu(maCong, now);
        }
        if (fault) {
            suCoService.taoNeuChuaMo(maCong, "An toàn điện", "Khẩn cấp",
                    "Thiết bị khôi phục với trạng thái không an toàn: " + trangThaiThuc);
        }
        if (cong.getMaCauHinh() != null) canBangTaiService.canBang(cong.getMaCauHinh(), now);
        audit.ghi(null, "CONG_KET_NOI_LAI", "Cổng " + maCong + " phục hồi với trạng thái " + trangThaiThuc);
        return new KetQuaPhucHoi(true, fault);
    }

    @Transactional
    public boolean danhDauMatKetNoi(String maCong) {
        LocalDateTime now = LocalDateTime.now(clock);
        CongSac cong = congRepository.findForUpdateByMaCong(maCong).orElse(null);
        if (cong == null || cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.MAT_KET_NOI
                || cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.BAO_TRI) return false;
        cong.matKetNoi(now);
        lichSuRepository.save(new LichSuKetNoi(maGenerator.next("LSKN"), maCong, now, "Mất kết nối", null));
        audit.ghi(null, "CONG_MAT_KET_NOI", "Cổng " + maCong + " không có heartbeat quá 90 giây.");
        return true;
    }
}
