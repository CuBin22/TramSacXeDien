package com.tramsac.congsac;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cau-hinh-cong-suat")
public class CauHinhCongSuatController {
    private final CauHinhCongSuatService service;
    public CauHinhCongSuatController(CauHinhCongSuatService service) { this.service = service; }
    @PutMapping("/{maCauHinh}")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public CauHinhCongSuat capNhat(@PathVariable String maCauHinh,
                                   @Valid @RequestBody CauHinhCongSuatRequest request) {
        return service.capNhat(maCauHinh, request);
    }
}
