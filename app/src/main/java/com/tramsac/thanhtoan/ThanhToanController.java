package com.tramsac.thanhtoan;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/thanh-toan")
public class ThanhToanController {
    private final ThanhToanService service;

    public ThanhToanController(ThanhToanService service) { this.service = service; }

    @PostMapping("/don")
    @PreAuthorize("hasRole('NGUOI_DUNG')")
    public ResponseEntity<DonThanhToanResponse> taoDon(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaoDonThanhToanRequest request) {
        DonThanhToanResponse response = service.taoDon(key, request);
        return ResponseEntity.accepted().header("Idempotent-Replay", Boolean.toString(response.idempotentReplay()))
                .body(response);
    }

    @GetMapping("/phien/{maPhien}/hoa-don")
    @PreAuthorize("hasRole('NGUOI_DUNG')")
    public HoaDonPhienResponse hoaDon(@PathVariable String maPhien) { return service.hoaDon(maPhien); }

    @GetMapping("/don/{maDon}")
    @PreAuthorize("hasRole('NGUOI_DUNG')")
    public DonThanhToanResponse chiTiet(@PathVariable String maDon) { return service.chiTiet(maDon); }
}
