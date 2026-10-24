package com.tramsac.nguoidung;

import com.tramsac.common.security.NguoiDungHienTai;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint chỉ tồn tại trong test để kiểm tra xác thực và phân quyền theo vai trò. */
@RestController
class TestRoleController {

    @GetMapping("/api/test/toi")
    String toi() {
        return NguoiDungHienTai.layMaND();
    }

    @GetMapping("/api/test/chi-quan-tri")
    @PreAuthorize("hasRole('QUAN_TRI')")
    String chiQuanTri() {
        return "ok";
    }
}
