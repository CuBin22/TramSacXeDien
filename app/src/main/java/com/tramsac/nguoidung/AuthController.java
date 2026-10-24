package com.tramsac.nguoidung;

import com.tramsac.nguoidung.dto.DangKyRequest;
import com.tramsac.nguoidung.dto.DangKyResponse;
import com.tramsac.nguoidung.dto.DangNhapRequest;
import com.tramsac.nguoidung.dto.DangNhapResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DangKyService dangKyService;
    private final DangNhapService dangNhapService;

    public AuthController(DangKyService dangKyService, DangNhapService dangNhapService) {
        this.dangKyService = dangKyService;
        this.dangNhapService = dangNhapService;
    }

    @PostMapping("/dang-ky")
    public ResponseEntity<DangKyResponse> dangKy(@Valid @RequestBody DangKyRequest request) {
        String maND = dangKyService.dangKy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DangKyResponse(maND));
    }

    @PostMapping("/dang-nhap")
    public ResponseEntity<DangNhapResponse> dangNhap(@Valid @RequestBody DangNhapRequest request) {
        return ResponseEntity.ok(dangNhapService.dangNhap(request));
    }
}
