package com.tramsac.datphien;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.datphien.dto.DatPhienRequest;
import com.tramsac.datphien.dto.DatPhienResponse;
import com.tramsac.nguoidung.DangKyService;
import com.tramsac.nguoidung.NguoiDungRepository;
import com.tramsac.nguoidung.Xe;
import com.tramsac.nguoidung.XeRepository;
import com.tramsac.phiensac.LichSuTrangThaiPhienRepository;
import com.tramsac.phiensac.PhienSac;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.PhienSacService;
import com.tramsac.phiensac.TrangThaiPhien;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC05 Đặt phiên sạc và UC07 Hủy/đổi lịch. Thứ tự khóa trong mọi luồng (tránh deadlock):
 * người dùng → đặt chỗ → phiên → cổng (theo MaCong). Mọi kiểm tra từ chối nằm TRƯỚC khi ghi dữ liệu.
 */
@Service
public class DatPhienService {

    private final NguoiDungRepository nguoiDungRepository;
    private final XeRepository xeRepository;
    private final DatPhienRepository datPhienRepository;
    private final PhienSacRepository phienSacRepository;
    private final LichSuTrangThaiPhienRepository lichSuRepository;
    private final QuyDinhDatPhien quyDinh;
    private final KiemTraXungDotService kiemTraXungDotService;
    private final PhanBoCongService phanBoCongService;
    private final PhienSacService phienSacService;
    private final GiuChoService giuChoService;
    private final MaGenerator maGenerator;
    private final AuditService auditService;
    private final Clock clock;

    public DatPhienService(NguoiDungRepository nguoiDungRepository, XeRepository xeRepository,
                           DatPhienRepository datPhienRepository, PhienSacRepository phienSacRepository,
                           LichSuTrangThaiPhienRepository lichSuRepository, QuyDinhDatPhien quyDinh,
                           KiemTraXungDotService kiemTraXungDotService, PhanBoCongService phanBoCongService,
                           PhienSacService phienSacService, GiuChoService giuChoService, MaGenerator maGenerator,
                           AuditService auditService, Clock clock) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.xeRepository = xeRepository;
        this.datPhienRepository = datPhienRepository;
        this.phienSacRepository = phienSacRepository;
        this.lichSuRepository = lichSuRepository;
        this.quyDinh = quyDinh;
        this.kiemTraXungDotService = kiemTraXungDotService;
        this.phanBoCongService = phanBoCongService;
        this.phienSacService = phienSacService;
        this.giuChoService = giuChoService;
        this.maGenerator = maGenerator;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ UC05

    /** Sơ đồ tuần tự: datPhien(maND, bienSo, loaiCong, gioBatDau, gioKetThuc). */
    @Transactional
    public DatPhienResponse datPhien(DatPhienRequest req) {
        String maND = NguoiDungHienTai.layMaND();
        // Khóa dòng người dùng TRƯỚC TIÊN: các yêu cầu của cùng một người xếp hàng, nên đếm "tối đa 2 đặt chỗ"
        // và kiểm tra trùng lịch không bị qua mặt bởi yêu cầu đồng thời.
        nguoiDungRepository.findForUpdateByMaND(maND).orElseThrow(DatPhienService::chuaXacThuc);

        String bienSo = DangKyService.chuanHoaBienSo(req.bienSo());
        Xe xe = xeRepository.findByBienSo(bienSo).filter(x -> x.getMaND().equals(maND))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_XE",
                        "Không tìm thấy xe này trong hồ sơ của bạn."));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime batDau = utc(req.gioBatDau());
        LocalDateTime ketThuc = utc(req.gioKetThuc());

        quyDinh.kiemTra(batDau, ketThuc, now, req.loaiCong(), xe.getLoaiCongSuDung());
        kiemTraHanChe(maND, now);
        if (phienSacRepository.demTheoNguoiDungVaTrangThai(maND, TrangThaiPhien.CHO_THANH_TOAN.giaTri()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CON_PHIEN_CHO_THANH_TOAN",
                    "Bạn còn phiên sạc chưa thanh toán, hãy thanh toán trước khi đặt chỗ mới.");
        }
        if (datPhienRepository.countByMaNDAndTrangThaiIn(maND, QuyDinhDatPhien.TRANG_THAI_HIEU_LUC)
                >= QuyDinhDatPhien.TOI_DA_DAT_CHO_HIEU_LUC) {
            throw new ApiException(HttpStatus.CONFLICT, "VUOT_SO_DAT_CHO",
                    "Mỗi người dùng chỉ có tối đa 2 đặt chỗ đang hiệu lực.");
        }
        nemLoiNeuXungDot(kiemTraXungDotService.kiemTraXungDot(maND, req.loaiCong(), batDau, ketThuc, ""));

        String maDatPhien = maGenerator.next("DP");
        String maCong = phanBoCongService.phanBoCong(maDatPhien, req.loaiCong(), batDau, ketThuc)
                .orElseThrow(DatPhienService::khongCoCong);

        DatPhien dp = new DatPhien(maDatPhien, maND, xe.getMaXe(), maCong, now, batDau, ketThuc, req.loaiCong());
        luuDatPhien(dp);
        PhienSac phien = phienSacService.taoPhien(maDatPhien, maCong, "Người dùng");
        giuChoService.doiChieu(maCong, now);
        auditService.ghi(maND, "DAT_PHIEN", "Đặt chỗ " + maDatPhien + " cổng " + maCong + " từ " + batDau
                + " đến " + ketThuc);
        return DatPhienResponse.tu(dp, phien.getMaPhien(), bienSo);
    }

    // ------------------------------------------------------------------ UC07

    /**
     * Sơ đồ tuần tự: huyHoacDoiLich(maDatPhien, thaoTac, lyDo, khungGioMoi). Với Hủy, khungGioMoi bị bỏ qua.
     * Kết quả lỗi nào cũng hoàn tác toàn bộ giao dịch nên lịch cũ được giữ nguyên.
     */
    @Transactional
    public DatPhienResponse huyHoacDoiLich(String maDatPhien, ThaoTacDatPhien thaoTac, String lyDo,
                                           LocalDateTime batDauMoi, LocalDateTime ketThucMoi) {
        String maND = NguoiDungHienTai.layMaND();
        nguoiDungRepository.findForUpdateByMaND(maND).orElseThrow(DatPhienService::chuaXacThuc);
        DatPhien dp = datPhienRepository.findForUpdateByMaDatPhienAndMaND(maDatPhien, maND)
                .orElseThrow(DatPhienService::khongTimThayDatCho);
        PhienSac phien = phienSacRepository.findForUpdateByMaDatPhien(maDatPhien)
                .orElseThrow(DatPhienService::khongTimThayDatCho);
        LocalDateTime now = LocalDateTime.now(clock);

        boolean daBatDauSac = dp.layTrangThai() == TrangThaiDatPhien.DANG_SAC
                || (dp.layTrangThai() == TrangThaiDatPhien.DA_DAT && phien.layTrangThai() != TrangThaiPhien.DA_DAT);
        if (daBatDauSac) {
            throw new ApiException(HttpStatus.CONFLICT, "DA_BAT_DAU_SAC",
                    "Phiên đã bắt đầu sạc nên không thể hủy hoặc đổi lịch.");
        }
        if (dp.layTrangThai() != TrangThaiDatPhien.DA_DAT) {
            throw new ApiException(HttpStatus.CONFLICT, "DAT_CHO_KHONG_CON_HIEU_LUC",
                    "Đặt chỗ đã " + dp.getTrangThai().toLowerCase() + ", không thể hủy hoặc đổi lịch.");
        }

        String maCongCu = dp.getMaCong();
        if (thaoTac == ThaoTacDatPhien.HUY) {
            boolean satGio = now.isAfter(dp.getGioBatDau().minus(QuyDinhDatPhien.HUY_SAT_GIO));
            dp.huy(lyDo);
            phienSacService.chuyenTrangThai(phien, TrangThaiPhien.HUY, "Người dùng",
                    (satGio ? "Hủy sát giờ: " : "Hủy: ") + lyDo);
            datPhienRepository.saveAndFlush(dp);
            giuChoService.doiChieu(maCongCu, now);
            auditService.ghi(maND, "HUY_DAT_PHIEN", "Hủy đặt chỗ " + maDatPhien + (satGio ? " (sát giờ)" : ""));
        } else {
            doiLich(dp, phien, maND, now, lyDo, batDauMoi, ketThucMoi);
            giuChoService.doiChieu(maCongCu, now);
            if (!maCongCu.equals(dp.getMaCong())) {
                giuChoService.doiChieu(dp.getMaCong(), now);
            }
        }
        String bienSo = xeRepository.findById(dp.getMaXe()).map(Xe::getBienSo).orElse(null);
        return DatPhienResponse.tu(dp, phien.getMaPhien(), bienSo);
    }

    private void doiLich(DatPhien dp, PhienSac phien, String maND, LocalDateTime now, String lyDo,
                         LocalDateTime batDauMoi, LocalDateTime ketThucMoi) {
        if (dp.getSoLanDoiLich() >= QuyDinhDatPhien.TOI_DA_LAN_DOI_LICH) {
            throw new ApiException(HttpStatus.CONFLICT, "VUOT_SO_LAN_DOI_LICH",
                    "Mỗi đặt chỗ chỉ được đổi lịch tối đa " + QuyDinhDatPhien.TOI_DA_LAN_DOI_LICH + " lần.");
        }
        quyDinh.kiemTra(batDauMoi, ketThucMoi, now, dp.getLoaiCong(), dp.getLoaiCong());
        nemLoiNeuXungDot(kiemTraXungDotService.kiemTraXungDot(maND, dp.getLoaiCong(), batDauMoi, ketThucMoi,
                dp.getMaDatPhien()));
        // Khung giờ mới có thể giữ nguyên cổng cũ: đặt chỗ đang đổi lịch được loại khỏi phép kiểm tra trùng lịch
        String maCongMoi = phanBoCongService.phanBoCong(dp.getMaDatPhien(), dp.getLoaiCong(), batDauMoi, ketThucMoi)
                .orElseThrow(DatPhienService::khongCoCong);

        dp.doiLich(batDauMoi, ketThucMoi, maCongMoi, lyDo);
        phien.doiCong(maCongMoi);
        phienSacRepository.save(phien);
        luuDatPhien(dp);
        auditService.ghi(maND, "DOI_LICH", "Đổi lịch đặt chỗ " + dp.getMaDatPhien() + " sang cổng " + maCongMoi
                + " từ " + batDauMoi + " đến " + ketThucMoi);
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public List<DatPhienResponse> danhSach() {
        String maND = NguoiDungHienTai.layMaND();
        return datPhienRepository.findByMaNDOrderByGioBatDauDesc(maND).stream().map(this::tuDatPhien).toList();
    }

    @Transactional(readOnly = true)
    public DatPhienResponse chiTiet(String maDatPhien) {
        String maND = NguoiDungHienTai.layMaND();
        return datPhienRepository.findByMaDatPhienAndMaND(maDatPhien, maND).map(this::tuDatPhien)
                .orElseThrow(DatPhienService::khongTimThayDatCho);
    }

    // ------------------------------------------------------------------ nội bộ

    private DatPhienResponse tuDatPhien(DatPhien dp) {
        String maPhien = phienSacRepository.findByMaDatPhien(dp.getMaDatPhien()).map(PhienSac::getMaPhien)
                .orElse(null);
        String bienSo = xeRepository.findById(dp.getMaXe()).map(Xe::getBienSo).orElse(null);
        return DatPhienResponse.tu(dp, maPhien, bienSo);
    }

    /** Quy tắc 11: đủ 3 lần không đến/hủy sát giờ trong 30 ngày thì bị hạn chế đặt chỗ 24 giờ kể từ lần gần nhất. */
    private void kiemTraHanChe(String maND, LocalDateTime now) {
        LocalDateTime tu = now.minus(QuyDinhDatPhien.CHU_KY_VI_PHAM);
        if (lichSuRepository.demViPham(maND, tu) < QuyDinhDatPhien.NGUONG_VI_PHAM) {
            return;
        }
        LocalDateTime ganNhat = lichSuRepository.thoiDiemViPhamGanNhat(maND, tu);
        if (ganNhat == null) {
            return;
        }
        LocalDateTime hetHan = ganNhat.plus(QuyDinhDatPhien.THOI_GIAN_HAN_CHE);
        if (now.isBefore(hetHan)) {
            long conLai = Math.max(1, Duration.between(now, hetHan).toSeconds());
            throw new ApiException(HttpStatus.FORBIDDEN, "BI_HAN_CHE_DAT_CHO",
                    "Bạn tạm thời bị hạn chế đặt chỗ do không đến hoặc hủy sát giờ nhiều lần.",
                    Map.of("conLaiGiay", String.valueOf(conLai)));
        }
    }

    private static void nemLoiNeuXungDot(KetQuaXungDot ketQua) {
        if (ketQua == KetQuaXungDot.TRUNG_LICH) {
            throw new ApiException(HttpStatus.CONFLICT, "TRUNG_LICH",
                    "Khung giờ này trùng với một đặt chỗ khác của bạn, hãy chọn khung giờ khác.");
        }
        if (ketQua == KetQuaXungDot.HET_CONG) {
            throw new ApiException(HttpStatus.CONFLICT, "HET_CONG",
                    "Không còn cổng phù hợp trong khung giờ này, hãy chọn khung giờ khác.");
        }
    }

    /** Ràng buộc loại trừ của CSDL (V2) là lớp phòng vệ cuối: nếu vẫn bị chồng giờ thì báo xung đột. */
    private void luuDatPhien(DatPhien dp) {
        try {
            datPhienRepository.saveAndFlush(dp);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "XUNG_DOT_LICH",
                    "Khung giờ vừa bị giữ bởi yêu cầu khác, hãy chọn lại.");
        }
    }

    public static LocalDateTime utc(OffsetDateTime thoiDiem) {
        return LocalDateTime.ofInstant(thoiDiem.toInstant(), ZoneOffset.UTC);
    }

    private static ApiException khongCoCong() {
        return new ApiException(HttpStatus.CONFLICT, "KHONG_CO_CONG_PHU_HOP",
                "Không có cổng phù hợp trong khung giờ này.");
    }

    private static ApiException khongTimThayDatCho() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_DAT_CHO", "Không tìm thấy đặt chỗ.");
    }

    private static ApiException chuaXacThuc() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CHUA_XAC_THUC", "Cần đăng nhập.");
    }
}
