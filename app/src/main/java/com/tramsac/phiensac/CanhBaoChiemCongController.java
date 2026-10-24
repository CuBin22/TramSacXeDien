package com.tramsac.phiensac;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canh-bao-chiem-cong")
public class CanhBaoChiemCongController {
    private final CanhBaoChiemCongService service;
    public CanhBaoChiemCongController(CanhBaoChiemCongService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAnyRole('NHAN_VIEN_TRUC','KY_THUAT_VIEN','QUAN_TRI')")
    public List<CanhBaoChiemCongResponse> danhSach() { return service.danhSach(); }
    @PutMapping("/{maCanhBao}")
    @PreAuthorize("hasAnyRole('NHAN_VIEN_TRUC','KY_THUAT_VIEN','QUAN_TRI')")
    public CanhBaoChiemCongResponse phanHoi(@PathVariable String maCanhBao,
                                             @Valid @RequestBody PhanHoiCanhBaoRequest request) {
        return service.phanHoi(maCanhBao, request.phanHoi());
    }
}
