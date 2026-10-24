package com.tramsac.thanhtoan;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoanTienController {
    private final HoanTienService service;

    public HoanTienController(HoanTienService service) { this.service = service; }

    @PostMapping("/api/hoan-tien")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public ResponseEntity<HoanTienResponse> hoanTien(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody HoanTienRequest request) {
        HoanTienResponse response = service.hoanTien(key, request);
        return ResponseEntity.ok().header("Idempotent-Replay", Boolean.toString(response.idempotentReplay()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }
}
