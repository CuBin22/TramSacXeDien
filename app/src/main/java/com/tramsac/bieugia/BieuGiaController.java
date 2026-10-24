package com.tramsac.bieugia;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bieu-gia")
public class BieuGiaController {
    private final BieuGiaService service;
    public BieuGiaController(BieuGiaService service) { this.service = service; }
    @PostMapping
    @PreAuthorize("hasRole('QUAN_TRI')")
    public BieuGiaResponse tao(@Valid @RequestBody BieuGiaRequest request) { return service.tao(request); }
}
