package com.tramsac.datphien;

import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.TrangThaiCong;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Các tác vụ quét định kỳ của giai đoạn 2. Mỗi đặt chỗ/cổng xử lý trong một giao dịch riêng để lỗi hoặc khóa
 * của một mục không làm hỏng các mục khác. Bộ định thời (DatPhienScheduler) và API quản trị cùng gọi vào đây.
 */
@Component
public class DatPhienJobs {

    private static final Logger log = LoggerFactory.getLogger(DatPhienJobs.class);

    private final DatPhienRepository datPhienRepository;
    private final CongSacRepository congSacRepository;
    private final GiaiPhongQuaHanService giaiPhongQuaHanService;
    private final GiuChoService giuChoService;
    private final Clock clock;
    private final TransactionTemplate moiGiaoDich;

    public DatPhienJobs(DatPhienRepository datPhienRepository, CongSacRepository congSacRepository,
                        GiaiPhongQuaHanService giaiPhongQuaHanService, GiuChoService giuChoService,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.datPhienRepository = datPhienRepository;
        this.congSacRepository = congSacRepository;
        this.giaiPhongQuaHanService = giaiPhongQuaHanService;
        this.giuChoService = giuChoService;
        this.clock = clock;
        this.moiGiaoDich = new TransactionTemplate(transactionManager);
        this.moiGiaoDich.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** UC09: chuyển các đặt chỗ quá hạn sang "Quá hạn không đến", trả cổng về Rảnh. Trả về số đặt chỗ đã xử lý. */
    public int quetQuaHan() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> ungVien = datPhienRepository.timMaDatChoQuaHan(TrangThaiDatPhien.DA_DAT.giaTri(),
                now.minus(QuyDinhDatPhien.QUA_HAN_SAU));
        int daXuLy = 0;
        for (String maDatPhien : ungVien) {
            try {
                Boolean ketQua = moiGiaoDich.execute(status -> giaiPhongQuaHanService.giaiPhong(maDatPhien, now));
                if (Boolean.TRUE.equals(ketQua)) {
                    daXuLy++;
                }
            } catch (RuntimeException e) {
                log.warn("Không giải phóng được đặt chỗ {}: {}", maDatPhien, e.toString());
            }
        }
        return daXuLy;
    }

    /** Quy tắc 2: đối chiếu trạng thái giữ chỗ của mọi cổng Rảnh/Đã đặt. Trả về số cổng đã đổi trạng thái. */
    public int quetGiuCho() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> dsCong = congSacRepository.timMaCongTheoTrangThai(
                List.of(TrangThaiCong.RANH.giaTri(), TrangThaiCong.DA_DAT.giaTri()));
        int daDoi = 0;
        for (String maCong : dsCong) {
            try {
                Boolean ketQua = moiGiaoDich.execute(status -> giuChoService.doiChieu(maCong, now));
                if (Boolean.TRUE.equals(ketQua)) {
                    daDoi++;
                }
            } catch (RuntimeException e) {
                log.warn("Không đối chiếu được cổng {}: {}", maCong, e.toString());
            }
        }
        return daDoi;
    }
}
