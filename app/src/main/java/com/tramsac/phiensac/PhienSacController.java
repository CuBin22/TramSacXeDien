package com.tramsac.phiensac;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/phien")
public class PhienSacController {
    private final PhienSacCommandService commandService;
    private final PhienSacReadService readService;
    public PhienSacController(PhienSacCommandService commandService, PhienSacReadService readService) {
        this.commandService = commandService; this.readService = readService;
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('NGUOI_DUNG','NHAN_VIEN_TRUC','QUAN_TRI')")
    public List<PhienSacResponse> danhSach(
            @RequestParam(name = "tuNgay", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam(name = "denNgay", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(name = "trangThai", required = false) String trangThai) {
        return readService.danhSach(tuNgay, denNgay, trangThai);
    }
    @GetMapping("/{maPhien}")
    @PreAuthorize("hasAnyRole('NGUOI_DUNG','NHAN_VIEN_TRUC','QUAN_TRI')")
    public PhienSacResponse chiTiet(@PathVariable String maPhien) { return readService.chiTiet(maPhien); }
    @PostMapping("/{maPhien}/bat-dau")
    @PreAuthorize("hasRole('NGUOI_DUNG')")
    public ResponseEntity<LenhDieuKhienResponse> batDau(@PathVariable String maPhien,
            @RequestHeader("Idempotency-Key") @NotBlank String key) {
        LenhDieuKhienResponse body = commandService.batDau(maPhien, key);
        return response(body);
    }
    @PostMapping("/{maPhien}/dung")
    @PreAuthorize("hasAnyRole('NGUOI_DUNG','NHAN_VIEN_TRUC','QUAN_TRI')")
    public ResponseEntity<LenhDieuKhienResponse> dung(@PathVariable String maPhien,
            @RequestHeader("Idempotency-Key") @NotBlank String key) {
        return response(commandService.dung(maPhien, key));
    }
    private static ResponseEntity<LenhDieuKhienResponse> response(LenhDieuKhienResponse body) {
        return ResponseEntity.accepted().header("Idempotent-Replay", Boolean.toString(body.idempotentReplay()))
                .header(HttpHeaders.RETRY_AFTER, "2").body(body);
    }
}
