package com.tramsac.nguoidung;

import com.tramsac.nguoidung.dto.DoiVaiTroRequest;
import com.tramsac.nguoidung.dto.DoiVaiTroResponse;
import com.tramsac.nguoidung.dto.LichSuPhanQuyenResponse;
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
@RequestMapping("/api/nguoi-dung")
public class PhanQuyenController {

    private final PhanQuyenService phanQuyenService;

    public PhanQuyenController(PhanQuyenService phanQuyenService) {
        this.phanQuyenService = phanQuyenService;
    }

    @PutMapping("/{maND}/vai-tro")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public DoiVaiTroResponse doiVaiTro(@PathVariable String maND, @Valid @RequestBody DoiVaiTroRequest request) {
        return phanQuyenService.doiVaiTro(maND, request.vaiTroMoi());
    }

    @GetMapping("/{maND}/lich-su-phan-quyen")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public List<LichSuPhanQuyenResponse> lichSu(@PathVariable String maND) {
        return phanQuyenService.lichSu(maND);
    }
}
