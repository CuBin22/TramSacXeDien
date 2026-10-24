package com.tramsac.nguoidung;

import com.tramsac.nguoidung.dto.CapNhatHoSoRequest;
import com.tramsac.nguoidung.dto.HoSoResponse;
import com.tramsac.nguoidung.dto.XeDto;
import com.tramsac.nguoidung.dto.XeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ho-so")
public class HoSoController {

    private final HoSoService hoSoService;

    public HoSoController(HoSoService hoSoService) {
        this.hoSoService = hoSoService;
    }

    @GetMapping
    public HoSoResponse layHoSo() {
        return hoSoService.layHoSo();
    }

    @PutMapping
    public HoSoResponse capNhatHoSo(@Valid @RequestBody CapNhatHoSoRequest request) {
        return hoSoService.capNhatHoSo(request);
    }

    @PostMapping("/xe")
    public ResponseEntity<XeDto> themXe(@Valid @RequestBody XeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hoSoService.themXe(request));
    }

    @PutMapping("/xe/{maXe}")
    public XeDto suaXe(@PathVariable String maXe, @Valid @RequestBody XeRequest request) {
        return hoSoService.suaXe(maXe, request);
    }

    @DeleteMapping("/xe/{maXe}")
    public ResponseEntity<Void> xoaXe(@PathVariable String maXe) {
        hoSoService.xoaXe(maXe);
        return ResponseEntity.noContent().build();
    }
}
