package com.tramsac.suco;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.phiensac.DeviceFaultService;
import com.tramsac.nguoidung.NguoiDungRepository;
import com.tramsac.nguoidung.VaiTro;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/su-co")
public class SuCoController {
    public record TaoSuCo(@NotBlank @Size(max=20) String maCong, @NotBlank @Size(max=30) String loaiSuCo,
                          @Size(max=20) String mucUuTien, @NotBlank @Size(max=500) String moTa) { }
    public record GiaoViec(@NotBlank @Size(max=20) String maKTV, LocalDateTime hanXuLy) { }
    public record CapNhatSuCo(@NotBlank String trangThai, @Size(max=500) String nguyenNhan,
                             @Size(max=500) String cachXuLy, @Size(max=255) String ghiChu) { }

    private final SuCoRepository suCoRepo;
    private final LichSuSuCoRepository lichSuRepo;
    private final CongSacRepository congRepo;
    private final NguoiDungRepository nguoiDungRepo;
    private final MaGenerator ids;
    private final AuditService audit;
    private final DeviceFaultService faultService;
    private final Clock clock;

    public SuCoController(SuCoRepository suCoRepo, LichSuSuCoRepository lichSuRepo,
                          CongSacRepository congRepo, NguoiDungRepository nguoiDungRepo,
                          MaGenerator ids, AuditService audit, Clock clock, DeviceFaultService faultService) {
        this.suCoRepo = suCoRepo; this.lichSuRepo = lichSuRepo; this.congRepo = congRepo;
        this.nguoiDungRepo = nguoiDungRepo; this.ids = ids; this.audit = audit; this.clock = clock;
        this.faultService = faultService;
    }

    @PostMapping
    @Transactional
    public SuCo tao(@Valid @RequestBody TaoSuCo req, Authentication auth) {
        var user = nguoiDungRepo.findById(auth.getName()).orElseThrow();
        if (!congRepo.existsById(req.maCong())) throw new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_CONG", "Không tìm thấy cổng sạc.");
        if (blank(req.loaiSuCo()) || blank(req.moTa())) throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Loại sự cố và mô tả là bắt buộc.");
        String priority = req.mucUuTien() == null ? "Bình thường" : req.mucUuTien();
        if (!List.of("Thấp", "Bình thường", "Cao", "Khẩn cấp").contains(priority)) throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Mức ưu tiên không hợp lệ.");
        boolean vanHanh = user.layVaiTro() != VaiTro.NGUOI_DUNG;
        String nguon = vanHanh ? user.layVaiTro().giaTri() : "Người dùng";
        SuCo s = new SuCo(ids.next("SC"), req.maCong(), nguon, req.loaiSuCo().trim(), priority, "Mới", req.moTa().trim());
        s.baoCao(user.getMaND(), nguon); suCoRepo.save(s);
        ghiLichSu(s, "Mới", user.getMaND(), nguon + " gửi báo cáo sự cố.");
        audit.ghi(user.getMaND(), "BAO_CAO_SU_CO", s.getMaSuCo());
        if (vanHanh) faultService.loiDoNhanVienBao(req.maCong(), req.loaiSuCo().trim());
        return s;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SuCo> danhSach(@RequestParam(required = false) String trangThai,
                               @RequestParam(required = false) String maCong, Authentication auth) {
        boolean own = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_NGUOI_DUNG"));
        return suCoRepo.findAll().stream().filter(s -> !own || auth.getName().equals(s.getMaNguoiBao()))
                .filter(s -> trangThai == null || trangThai.equals(s.getTrangThai()))
                .filter(s -> maCong == null || maCong.equals(s.getMaCong())).toList();
    }

    @GetMapping("/{id}/lich-su")
    @Transactional(readOnly = true)
    public List<LichSuSuCo> lichSu(@PathVariable String id, Authentication auth) {
        SuCo s = lay(id);
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_NGUOI_DUNG"))
                && !auth.getName().equals(s.getMaNguoiBao())) throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Chỉ người báo sự cố mới xem được lịch sử này.");
        return lichSuRepo.findByMaSuCoOrderByThoiDiemAsc(id);
    }

    @PutMapping("/{id}/giao-viec")
    @Transactional
    public SuCo giao(@PathVariable String id, @Valid @RequestBody GiaoViec req, Authentication auth) {
        can(auth, VaiTro.QUAN_TRI, VaiTro.NHAN_VIEN_TRUC);
        var ktv = nguoiDungRepo.findById(req.maKTV()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_NGUOI_DUNG", "Không tìm thấy kỹ thuật viên."));
        if (ktv.layVaiTro() != VaiTro.KY_THUAT_VIEN) throw new ApiException(HttpStatus.BAD_REQUEST, "SAI_VAI_TRO", "Người được giao phải có vai trò Kỹ thuật viên.");
        if (req.hanXuLy() != null && !req.hanXuLy().isAfter(LocalDateTime.now(clock)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "HAN_XU_LY_KHONG_HOP_LE", "Hạn xử lý phải ở tương lai (UTC).");
        SuCo s = khoa(id); s.giao(req.maKTV(), req.hanXuLy());
        suCoRepo.save(s); ghiLichSu(s, s.getTrangThai(), auth.getName(), "Giao cho " + req.maKTV());
        audit.ghi(auth.getName(), "GIAO_SU_CO", id + " -> " + req.maKTV()); return s;
    }

    @PutMapping("/{id}/trang-thai")
    @Transactional
    public SuCo capNhat(@PathVariable String id, @Valid @RequestBody CapNhatSuCo req, Authentication auth) {
        SuCo s = khoa(id);
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_QUAN_TRI"));
        if (!admin && (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_KY_THUAT_VIEN")) || !auth.getName().equals(s.getMaKTV())))
            throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Chỉ kỹ thuật viên được giao hoặc quản trị viên mới cập nhật được.");
        if (!List.of("Đang xử lý", "Chờ linh kiện", "Đã xử lý", "Đã đóng").contains(req.trangThai()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "DU_LIEU_KHONG_HOP_LE", "Trạng thái sự cố không hợp lệ.");
        if ("Đã đóng".equals(req.trangThai()) && congRepo.findById(s.getMaCong()).map(c -> "Lỗi".equals(c.getTrangThai())).orElse(false))
            throw new ApiException(HttpStatus.CONFLICT, "CAN_XAC_NHAN_AN_TOAN", "Cổng còn lỗi: dùng xác nhận xử lý cổng sau khi thiết bị an toàn.");
        s.capNhat(req.trangThai(), req.nguyenNhan(), req.cachXuLy()); suCoRepo.save(s);
        ghiLichSu(s, s.getTrangThai(), auth.getName(), req.ghiChu()); audit.ghi(auth.getName(), "CAP_NHAT_SU_CO", id + " -> " + req.trangThai()); return s;
    }

    private SuCo lay(String id) { return suCoRepo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_SU_CO", "Không tìm thấy sự cố.")); }
    private SuCo khoa(String id) { return suCoRepo.findForUpdateByMaSuCo(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_SU_CO", "Không tìm thấy sự cố.")); }
    private void ghiLichSu(SuCo s, String state, String actor, String note) { lichSuRepo.save(new LichSuSuCo(ids.next("LSSC"), s.getMaSuCo(), LocalDateTime.now(clock), state, actor, note)); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void can(Authentication auth, VaiTro... allowed) {
        for (VaiTro v : allowed) if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + v.name()))) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Không đủ quyền thực hiện.");
    }
}
