package com.tramsac.phiensac;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.nguoidung.VaiTro;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanhBaoChiemCongService {
    public static final String NGUOI_DUNG = "Người dùng";
    public static final String NHAN_VIEN_TRUC = "Nhân viên trực";
    private static final int CHU_KY_PHUT = 10;
    private static final int SO_LAN_NHAC = 3;
    private static final List<String> CLOSED = List.of(TrangThaiPhien.HOAN_TAT.giaTri(), TrangThaiPhien.CHO_THANH_TOAN.giaTri());
    private final PhienSacRepository phienRepository;
    private final CongSacRepository congRepository;
    private final CanhBaoChiemCongRepository warningRepository;
    private final MaGenerator maGenerator;
    private final Clock clock;
    private final AuditService audit;
    public CanhBaoChiemCongService(PhienSacRepository phienRepository, CongSacRepository congRepository,
                                   CanhBaoChiemCongRepository warningRepository, MaGenerator maGenerator,
                                   Clock clock, AuditService audit) {
        this.phienRepository = phienRepository; this.congRepository = congRepository;
        this.warningRepository = warningRepository; this.maGenerator = maGenerator; this.clock = clock; this.audit = audit;
    }
    /**
     * UC18 / QĐ8: sau 10 phút kể từ khi kết thúc sạc mà xe chưa rời cổng thì nhắc người dùng, nhắc lại mỗi 10 phút
     * (tối đa 3 lần). Sau 3 lần nhắc không có phản hồi thì cảnh báo Nhân viên trực. Có phản hồi hoặc xe rời cổng
     * thì dừng. Mốc thời gian là lúc kết thúc sạc (thiết bị đã báo hoàn tất).
     */
    @Transactional
    public void quet() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (PhienSac session : phienRepository.findEndedByStatuses(CLOSED)) {
            if (session.getKetThucLuc() == null
                    || session.getKetThucLuc().isAfter(now.minusMinutes(CHU_KY_PHUT))) continue;
            boolean vehiclePresent = congRepository.findById(session.getMaCong()).map(c -> c.isCoXe()).orElse(false);
            if (!vehiclePresent) continue;
            List<CanhBaoChiemCong> sent = warningRepository.findByMaPhienOrderByLanCanhBaoAsc(session.getMaPhien());
            boolean coPhanHoi = sent.stream().anyMatch(w -> w.getPhanHoi() != null && !w.getPhanHoi().isBlank());
            boolean daLeoThang = sent.stream().anyMatch(w -> NHAN_VIEN_TRUC.equals(w.getNguoiNhan()));
            if (coPhanHoi || daLeoThang) continue;
            long soLanNhac = sent.stream().filter(w -> NGUOI_DUNG.equals(w.getNguoiNhan())).count();
            long phutDaQua = Duration.between(session.getKetThucLuc(), now).toMinutes();
            if (soLanNhac < SO_LAN_NHAC) {
                long expected = Math.min(SO_LAN_NHAC, phutDaQua / CHU_KY_PHUT);
                if (soLanNhac < expected) {
                    warningRepository.save(new CanhBaoChiemCong(maGenerator.next("CB"), session.getMaPhien(),
                            sent.size() + 1, now, NGUOI_DUNG));
                    audit.ghi(null, "CANH_BAO_CHIEM_CONG", "Nhắc người dùng lần " + (soLanNhac + 1) + ": xe vẫn chiếm cổng "
                            + session.getMaCong() + " sau khi phiên " + session.getMaPhien() + " kết thúc.");
                }
            } else if (phutDaQua >= (long) (SO_LAN_NHAC + 1) * CHU_KY_PHUT) {
                warningRepository.save(new CanhBaoChiemCong(maGenerator.next("CB"), session.getMaPhien(),
                        sent.size() + 1, now, NHAN_VIEN_TRUC));
                audit.ghi(null, "LEO_THANG_CHIEM_CONG", "Người dùng không phản hồi sau " + SO_LAN_NHAC
                        + " lần nhắc; báo Nhân viên trực xử lý xe chiếm cổng " + session.getMaCong() + ".");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CanhBaoChiemCongResponse> danhSach() {
        return warningRepository.findAllByOrderByThoiDiemDesc().stream().map(CanhBaoChiemCongResponse::tu).toList();
    }
    @Transactional
    public CanhBaoChiemCongResponse phanHoi(String maCanhBao, String note) {
        if (NguoiDungHienTai.layVaiTro() == VaiTro.NGUOI_DUNG) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Chỉ nhân viên trực hoặc Quản trị được phản hồi cảnh báo.");
        }
        CanhBaoChiemCong c = warningRepository.findById(maCanhBao).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_CANH_BAO", "Không tìm thấy cảnh báo."));
        c.phanHoi(note == null ? "" : note.substring(0, Math.min(255, note.length())));
        return CanhBaoChiemCongResponse.tu(c);
    }
}
