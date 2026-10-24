package com.tramsac.congsac;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC20: cấp đều theo định mức nhưng giữ tối thiểu 20% cho mỗi cổng đang sạc. */
@Service
public class CanBangTaiService {
    private final CongSacRepository congRepository;
    private final CauHinhCongSuatRepository configRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final com.tramsac.phiensac.LenhDieuKhienRepository commands;
    public CanBangTaiService(CongSacRepository congRepository, CauHinhCongSuatRepository configRepository,
                             ApplicationEventPublisher events, Clock clock,
                             com.tramsac.phiensac.LenhDieuKhienRepository commands) {
        this.commands = commands;
        this.congRepository = congRepository; this.configRepository = configRepository; this.events = events; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal kiemTraKhaNangBatDau(CongSac ungVien) {
        CauHinhCongSuat config = configRepository.findForUpdateByMaCauHinh(ungVien.getMaCauHinh())
                .orElseThrow(() -> new IllegalStateException("Cổng chưa được gán cấu hình công suất."));
        List<CongSac> ports = congRepository.findForUpdateByMaCauHinhOrderByMaCong(ungVien.getMaCauHinh());
        BigDecimal dinhMucDangSac = BigDecimal.ZERO;
        BigDecimal toiThieu = BigDecimal.ZERO;
        boolean found = false;
        var pending = commands.timCongChoBatDau();
        for (CongSac port : ports) {
            if (port.getMaCong().equals(ungVien.getMaCong())) found = true;
            if (port.layTrangThai() == TrangThaiCong.DANG_SAC || pending.contains(port.getMaCong())
                    || port.getMaCong().equals(ungVien.getMaCong())) {
                dinhMucDangSac = dinhMucDangSac.add(port.getCongSuatDinhMuc());
                toiThieu = toiThieu.add(port.getCongSuatDinhMuc().multiply(new BigDecimal("0.20")));
            }
        }
        if (!found) throw new IllegalStateException("Không tìm thấy cổng trong nhóm công suất.");
        BigDecimal gioiHan = gioiHan(config);
        if (toiThieu.compareTo(gioiHan) > 0) {
            throw new IllegalStateException("Công suất đấu nối không đủ công suất tối thiểu cho các cổng đang sạc.");
        }
        BigDecimal heSo = dinhMucDangSac.signum() == 0 ? BigDecimal.ONE
                : gioiHan.divide(dinhMucDangSac, 8, RoundingMode.DOWN).min(BigDecimal.ONE);
        return ungVien.getCongSuatDinhMuc().multiply(heSo).setScale(2, RoundingMode.DOWN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void canBang(String maCauHinh, LocalDateTime now) {
        CauHinhCongSuat config = configRepository.findForUpdateByMaCauHinh(maCauHinh).orElse(null);
        if (config == null) return;
        List<CongSac> ports = congRepository.findForUpdateByMaCauHinhOrderByMaCong(maCauHinh);
        var pending = commands.timCongChoBatDau();
        List<CongSac> active = ports.stream().filter(p -> p.layTrangThai() == TrangThaiCong.DANG_SAC
                || pending.contains(p.getMaCong())).toList();
        BigDecimal sumRated = active.stream().map(CongSac::getCongSuatDinhMuc).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal limit = gioiHan(config);
        BigDecimal minimum = active.stream().map(p -> p.getCongSuatDinhMuc().multiply(new BigDecimal("0.20")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (minimum.compareTo(limit) > 0) {
            throw new IllegalStateException("Ngưỡng công suất thấp hơn tổng mức tối thiểu của các cổng đang sạc.");
        }
        BigDecimal factor = sumRated.signum() == 0 ? BigDecimal.ZERO
                : limit.divide(sumRated, 8, RoundingMode.DOWN).min(BigDecimal.ONE);
        List<CongSuatCap> changed = new ArrayList<>();
        for (CongSac port : ports) {
            BigDecimal wanted = BigDecimal.ZERO;
            if (port.layTrangThai() == TrangThaiCong.DANG_SAC) {
                BigDecimal perPortMinimum = port.getCongSuatDinhMuc().multiply(new BigDecimal("0.20"));
                wanted = port.getCongSuatDinhMuc().multiply(factor).max(perPortMinimum).setScale(2, RoundingMode.DOWN);
            }
            if (port.getCongSuatDuocCap().compareTo(wanted) != 0) {
                port.capNhatCongSuat(wanted, now);
                changed.add(new CongSuatCap(port.getMaCong(), wanted));
            }
        }
        if (!changed.isEmpty()) events.publishEvent(new CongSuatDaCanBang(List.copyOf(changed)));
    }

    @Transactional
    public void canBangNgoaiGiaoDich(String maCauHinh) {
        canBang(maCauHinh, LocalDateTime.now(clock));
    }

    private static BigDecimal gioiHan(CauHinhCongSuat config) {
        return config.getCongSuatDauNoi().multiply(config.getNguongGiamTai())
                .divide(new BigDecimal("100"), 4, RoundingMode.DOWN);
    }
}
