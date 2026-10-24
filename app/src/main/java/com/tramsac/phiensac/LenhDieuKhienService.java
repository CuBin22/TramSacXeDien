package com.tramsac.phiensac;

import com.tramsac.bieugia.TinhPhiService;
import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.congsac.CanBangTaiService;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.CauHinhCongSuatRepository;
import com.tramsac.datphien.DatPhien;
import com.tramsac.datphien.DatPhienRepository;
import com.tramsac.datphien.GiuChoService;
import com.tramsac.datphien.QuyDinhDatPhien;
import com.tramsac.datphien.TrangThaiDatPhien;
import com.tramsac.nguoidung.VaiTro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Các giao dịch ngắn cho lệnh điều khiển; không chờ ack trong giao dịch cơ sở dữ liệu. */
@Service
public class LenhDieuKhienService {
    public record KetQua(LenhDieuKhien lenh, boolean replay) { }
    private final LenhDieuKhienRepository lenhRepository;
    private final PhienSacRepository phienRepository;
    private final DatPhienRepository datPhienRepository;
    private final CongSacRepository congRepository;
    private final CauHinhCongSuatRepository configRepository;
    private final PhienSacService phienSacService;
    private final GiuChoService giuChoService;
    private final CanBangTaiService canBangTaiService;
    private final TinhPhiService tinhPhiService;
    private final MaGenerator maGenerator;
    private final AuditService auditService;
    private final SafetyAuditService safetyAuditService;
    private final Clock clock;

    public LenhDieuKhienService(LenhDieuKhienRepository lenhRepository, PhienSacRepository phienRepository,
                                DatPhienRepository datPhienRepository, CongSacRepository congRepository,
                                CauHinhCongSuatRepository configRepository, PhienSacService phienSacService,
                                GiuChoService giuChoService, CanBangTaiService canBangTaiService,
                                TinhPhiService tinhPhiService, MaGenerator maGenerator,
                                AuditService auditService, SafetyAuditService safetyAuditService, Clock clock) {
        this.lenhRepository = lenhRepository; this.phienRepository = phienRepository;
        this.datPhienRepository = datPhienRepository; this.congRepository = congRepository;
        this.configRepository = configRepository; this.phienSacService = phienSacService;
        this.giuChoService = giuChoService; this.canBangTaiService = canBangTaiService;
        this.tinhPhiService = tinhPhiService; this.maGenerator = maGenerator;
        this.auditService = auditService; this.safetyAuditService = safetyAuditService; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<KetQua> timReplay(String maPhien, String loaiLenh, String key) {
        return lenhRepository.findByKhoaIdempotency(key).map(l -> {
            if (!l.getMaPhien().equals(maPhien) || !l.getLoaiLenh().equals(loaiLenh)) {
                throw xungDotKhoa();
            }
            return new KetQua(l, true);
        });
    }

    @Transactional
    public KetQua batDau(String maPhien, String key, boolean daXacNhanAnToan) {
        Optional<KetQua> replay = timReplayNoTx(maPhien, "BAT_DAU", key);
        if (replay.isPresent()) return replay.get();
        String maDatPhien = phienRepository.timMaDatPhien(maPhien).orElseThrow(LenhDieuKhienService::khongTimThay);
        DatPhien dp = datPhienRepository.findForUpdateByMaDatPhien(maDatPhien)
                .orElseThrow(LenhDieuKhienService::khongTimThay);
        PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien)
                .orElseThrow(LenhDieuKhienService::khongTimThay);
        yeuCauQuyen(maPhien, dp.getMaND(), false);
        LocalDateTime now = LocalDateTime.now(clock);
        // Thời điểm sạc chỉ nằm trong khung đặt chỗ; cổng được mở trước tối đa 15 phút.
        if (now.isBefore(dp.getGioBatDau().minus(QuyDinhDatPhien.CUA_SO_GIU_CHO))
                || !now.isBefore(dp.getGioKetThuc())) {
            throw new ApiException(HttpStatus.CONFLICT, "NGOAI_KHUNG_DAT_CHO",
                    "Chỉ có thể bắt đầu sạc trong khung giờ đã đặt (từ 15 phút trước giờ bắt đầu).");
        }
        if (phien.layTrangThai() != TrangThaiPhien.DA_DAT && phien.layTrangThai() != TrangThaiPhien.CHO) {
            throw new ApiException(HttpStatus.CONFLICT, "PHIEN_KHONG_CHO_BAT_DAU", "Phiên sạc không ở trạng thái có thể bắt đầu.");
        }
        if (phien.isKhoaPhien()) throw new ApiException(HttpStatus.CONFLICT, "PHIEN_DA_KHOA", "Phiên sạc đang bị khóa.");
        if (lenhRepository.existsByMaPhienAndLoaiLenhAndTrangThai(maPhien, "BAT_DAU", "Chờ ack")) {
            throw new ApiException(HttpStatus.CONFLICT, "LENH_DANG_CHO_ACK", "Lệnh bắt đầu trước đó đang chờ thiết bị xác nhận.");
        }

        String maCong = phien.getMaCong();
        CongSacRepository.CauHinhProjection projection = congRepository.findCauHinhByMaCong(maCong)
                .orElseThrow(() -> new IllegalStateException("Cổng chưa có cấu hình công suất."));
        configRepository.findForUpdateByMaCauHinh(projection.getMaCauHinh())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy cấu hình công suất."));
        List<CongSac> ports = congRepository.findForUpdateByMaCauHinhOrderByMaCong(projection.getMaCauHinh());
        CongSac cong = ports.stream().filter(c -> c.getMaCong().equals(maCong)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy cổng sạc."));
        if (cong.layTrangThai() == com.tramsac.congsac.TrangThaiCong.RANH) giuChoService.doiChieu(maCong, now);
        BigDecimal khaDung;
        try { khaDung = canBangTaiService.kiemTraKhaNangBatDau(cong); }
        catch (IllegalStateException e) {
            safetyAuditService.ghi("TU_CHOI_CAP_DIEN", "Cổng " + maCong + ": " + e.getMessage());
            throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN", e.getMessage());
        }
        cong.xacNhanAnToan(now);
        try {
            cong.kiemTraAnToan(daXacNhanAnToan, phien.isKhoaPhien(), now, khaDung);
        } catch (IllegalStateException e) {
            safetyAuditService.ghi("TU_CHOI_CAP_DIEN", "Cổng " + maCong + ": " + e.getMessage());
            throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN", e.getMessage());
        }
        if (phien.layTrangThai() == TrangThaiPhien.DA_DAT) {
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.CHO, "Người dùng", "Gửi lệnh bắt đầu sạc");
        }
        return taoLenh(phien, "BAT_DAU", key, now);
    }

    @Transactional
    public KetQua dung(String maPhien, String key, BigDecimal dienNangNeuCo) {
        Optional<KetQua> replay = timReplayNoTx(maPhien, "DUNG", key);
        if (replay.isPresent()) return replay.get();
        String maDatPhien = phienRepository.timMaDatPhien(maPhien).orElseThrow(LenhDieuKhienService::khongTimThay);
        DatPhien dp = datPhienRepository.findForUpdateByMaDatPhien(maDatPhien)
                .orElseThrow(LenhDieuKhienService::khongTimThay);
        PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien)
                .orElseThrow(LenhDieuKhienService::khongTimThay);
        yeuCauQuyen(maPhien, dp.getMaND(), true);
        if (phien.layTrangThai() != TrangThaiPhien.DANG_SAC || phien.isKhoaPhien()) {
            throw new ApiException(HttpStatus.CONFLICT, "PHIEN_KHONG_DANG_SAC", "Chỉ phiên đang sạc mới có thể dừng.");
        }
        if (lenhRepository.existsByMaPhienAndLoaiLenhAndTrangThai(maPhien, "DUNG", "Chờ ack")) {
            throw new ApiException(HttpStatus.CONFLICT, "LENH_DANG_CHO_ACK", "Lệnh dừng trước đó đang chờ thiết bị xác nhận.");
        }
        KetQua ketQua = taoLenh(phien, "DUNG", key, LocalDateTime.now(clock));
        VaiTro vaiTro = NguoiDungHienTai.layVaiTro();
        if (vaiTro != VaiTro.NGUOI_DUNG) {
            // Quy tắc 14: ghi nguồn thao tác khi Nhân viên trực/Quản trị dừng phiên thay người dùng.
            auditService.ghi(NguoiDungHienTai.layMaND(), "DUNG_SAC_HO_TRO",
                    vaiTro.name() + " dừng phiên " + maPhien + " của người dùng " + dp.getMaND());
        }
        return ketQua;
    }

    private KetQua taoLenh(PhienSac phien, String loai, String key, LocalDateTime now) {
        try {
            LenhDieuKhien command = lenhRepository.saveAndFlush(
                    new LenhDieuKhien(maGenerator.next("LNH"), phien.getMaPhien(), loai, key, now));
            return new KetQua(command, false);
        } catch (DataIntegrityViolationException e) {
            throw xungDotKhoa();
        }
    }

    private Optional<KetQua> timReplayNoTx(String maPhien, String loai, String key) {
        return lenhRepository.findByKhoaIdempotency(key).map(l -> {
            if (!l.getMaPhien().equals(maPhien) || !l.getLoaiLenh().equals(loai)) throw xungDotKhoa();
            return new KetQua(l, true);
        });
    }

    private void yeuCauQuyen(String maPhien, String maNDChu, boolean choNhanVien) {
        VaiTro role = NguoiDungHienTai.layVaiTro();
        if (role == VaiTro.NGUOI_DUNG) {
            if (!maNDChu.equals(NguoiDungHienTai.layMaND())) throw khongTimThay();
            return;
        }
        if (choNhanVien && (role == VaiTro.NHAN_VIEN_TRUC || role == VaiTro.QUAN_TRI)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Vai trò hiện tại không được thao tác phiên này.");
    }

    @Transactional
    @io.opentelemetry.instrumentation.annotations.WithSpan("lenh.nhanAck")
    public boolean nhanAck(String maLenh, String ketQua, BigDecimal dienNang) {
        String maPhien = lenhRepository.timMaPhien(maLenh).orElse(null);
        if (maPhien == null) return false;
        String maDatPhien = phienRepository.timMaDatPhien(maPhien).orElse(null);
        if (maDatPhien == null) return false;
        DatPhien dp = datPhienRepository.findForUpdateByMaDatPhien(maDatPhien).orElse(null);
        PhienSac phien = phienRepository.findForUpdateByMaPhien(maPhien).orElse(null);
        if (dp == null || phien == null) return false;
        CongSacRepository.CauHinhProjection projection = congRepository.findCauHinhByMaCong(phien.getMaCong()).orElse(null);
        if (projection == null || projection.getMaCauHinh() == null) return false;
        configRepository.findForUpdateByMaCauHinh(projection.getMaCauHinh());
        List<CongSac> ports = congRepository.findForUpdateByMaCauHinhOrderByMaCong(projection.getMaCauHinh());
        CongSac cong = ports.stream().filter(c -> c.getMaCong().equals(phien.getMaCong())).findFirst().orElse(null);
        if (cong == null) return false;
        LenhDieuKhien command = lenhRepository.findForUpdate(maLenh).orElse(null);
        if (command == null) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        if (!command.dangChoAck()) {
            return "Thất bại".equals(command.getTrangThai()) && "OK".equalsIgnoreCase(ketQua);
        }
        if (!"OK".equalsIgnoreCase(ketQua)) {
            command.thatBai(now);
            if ("UNSAFE".equalsIgnoreCase(ketQua)) {
                safetyAuditService.ghi("TU_CHOI_CAP_DIEN", "Thiết bị xác nhận không an toàn cho lệnh " + maLenh + ".");
            }
            return false;
        }
        if ("BAT_DAU".equals(command.getLoaiLenh())) {
            if (phien.layTrangThai() != TrangThaiPhien.CHO || phien.isKhoaPhien()
                    || cong.layTrangThai() != com.tramsac.congsac.TrangThaiCong.DA_DAT) {
                command.thatBai(now);
                return false;
            }
            command.thanhCong(now);
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.DANG_SAC, "Thiết bị", "Thiết bị xác nhận bắt đầu");
            dp.batDauSac();
            cong.batDauSac(now);
            canBangTaiService.canBang(cong.getMaCauHinh(), now);
            auditService.ghi(dp.getMaND(), "BAT_DAU_SAC", "Phiên " + maPhien + " bắt đầu tại cổng " + cong.getMaCong());
            return false;
        }
        if ("DUNG".equals(command.getLoaiLenh())) {
            if (phien.layTrangThai() != TrangThaiPhien.DANG_SAC
                    || cong.layTrangThai() != com.tramsac.congsac.TrangThaiCong.DANG_SAC) {
                command.thatBai(now);
                return false;
            }
            command.thanhCong(now);
            BigDecimal meter = dienNang == null ? BigDecimal.ZERO : dienNang.max(BigDecimal.ZERO);
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.KET_THUC_SAC, "Thiết bị", "Thiết bị xác nhận dừng");
            phien.danhDauKetThuc(now, meter);
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.CHO_THANH_TOAN, "Hệ thống", "Đã tính phí sạc");
            dp.hoanTatSac();
            cong.ketThucSac(now);
            giuChoService.doiChieu(cong.getMaCong(), now);
            tinhPhiService.tinhPhi(phien);
            canBangTaiService.canBang(cong.getMaCauHinh(), now);
            auditService.ghi(dp.getMaND(), "DUNG_SAC", "Phiên " + maPhien + " dừng; điện năng " + meter + " kWh");
        }
        return false;
    }

    @Transactional
    @io.opentelemetry.instrumentation.annotations.WithSpan("lenh.guiLai")
    public boolean xuLyLaiLenh(String maLenh) {
        LenhDieuKhien lenh = lenhRepository.findForUpdate(maLenh).orElse(null);
        if (lenh == null || !lenh.dangChoAck()) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        if (lenh.getGuiLuc() == null || lenh.getGuiLuc().isAfter(now.minusSeconds(10))) return false;
        if (lenh.getSoLanGui() >= 4) {
            lenh.thatBai(now);
            return false;
        }
        lenh.daGui(now);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<LenhDieuKhien> timTheoId(String maLenh) { return lenhRepository.findById(maLenh); }

    private static ApiException xungDotKhoa() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_XUNG_DOT",
                "Idempotency-Key đã được dùng cho một yêu cầu khác hoặc đang được xử lý đồng thời.");
    }
    private static ApiException khongTimThay() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_PHIEN", "Không tìm thấy phiên sạc.");
    }
}
