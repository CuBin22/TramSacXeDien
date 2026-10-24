package com.tramsac.congsac;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/api/cong")
public class CongSacController {
    private final CongSacAdminService congService;
    private final CongSacStreamService streamService;
    public CongSacController(CongSacAdminService congService, CongSacStreamService streamService) {
        this.congService = congService; this.streamService = streamService;
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('NHAN_VIEN_TRUC','KY_THUAT_VIEN','QUAN_TRI')")
    public List<CongSacResponse> danhSach() { return congService.danhSach(); }
    /** Minimal directory for authenticated users to select a charger when reporting an issue. */
    @GetMapping("/tom-tat")
    public List<Map<String, String>> tomTat() {
        return congService.danhSach().stream().map(c -> Map.of("maCong", c.maCong(), "loaiCong", c.loaiCong(),
                "trangThai", c.trangThai(), "congSuatDinhMuc", String.valueOf(c.congSuatDinhMuc()))).toList();
    }
    @GetMapping("/stream")
    @PreAuthorize("hasAnyRole('NHAN_VIEN_TRUC','KY_THUAT_VIEN','QUAN_TRI')")
    public SseEmitter stream() { return streamService.connect(); }
    @PostMapping("/{maCong}/khoa")
    @PreAuthorize("hasAnyRole('KY_THUAT_VIEN','QUAN_TRI')")
    public CongSacResponse khoa(@PathVariable String maCong) { return congService.khoa(maCong); }
    @PostMapping("/{maCong}/mo")
    @PreAuthorize("hasAnyRole('KY_THUAT_VIEN','QUAN_TRI')")
    public CongSacResponse mo(@PathVariable String maCong) { return congService.mo(maCong); }
    @PostMapping("/{maCong}/xac-nhan-xu-ly")
    @PreAuthorize("hasAnyRole('KY_THUAT_VIEN','QUAN_TRI')")
    public CongSacResponse xacNhanXuLy(@PathVariable String maCong) { return congService.xacNhanXuLy(maCong); }
}
