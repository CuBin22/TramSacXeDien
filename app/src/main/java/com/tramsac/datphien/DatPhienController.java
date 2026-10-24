package com.tramsac.datphien;

import com.tramsac.datphien.dto.DatPhienRequest;
import com.tramsac.datphien.dto.DatPhienResponse;
import com.tramsac.datphien.dto.DoiLichRequest;
import com.tramsac.datphien.dto.HuyDatPhienRequest;
import com.tramsac.datphien.dto.KetQuaQuetResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dat-phien")
public class DatPhienController {

    private final DatPhienService datPhienService;
    private final DatPhienJobs jobs;

    public DatPhienController(DatPhienService datPhienService, DatPhienJobs jobs) {
        this.datPhienService = datPhienService;
        this.jobs = jobs;
    }

    @PostMapping
    public ResponseEntity<DatPhienResponse> datPhien(@Valid @RequestBody DatPhienRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(datPhienService.datPhien(request));
    }

    @GetMapping
    public List<DatPhienResponse> danhSach() {
        return datPhienService.danhSach();
    }

    @GetMapping("/{maDatPhien}")
    public DatPhienResponse chiTiet(@PathVariable String maDatPhien) {
        return datPhienService.chiTiet(maDatPhien);
    }

    @PostMapping("/{maDatPhien}/huy")
    public DatPhienResponse huy(@PathVariable String maDatPhien, @Valid @RequestBody HuyDatPhienRequest request) {
        return datPhienService.huyHoacDoiLich(maDatPhien, ThaoTacDatPhien.HUY, request.lyDo(), null, null);
    }

    @PutMapping("/{maDatPhien}/doi-lich")
    public DatPhienResponse doiLich(@PathVariable String maDatPhien, @Valid @RequestBody DoiLichRequest request) {
        return datPhienService.huyHoacDoiLich(maDatPhien, ThaoTacDatPhien.DOI_LICH, request.lyDo(),
                DatPhienService.utc(request.gioBatDau()), DatPhienService.utc(request.gioKetThuc()));
    }

    /** Quản trị: chạy ngay tác vụ UC09 (không cần chờ bộ định thời, tiện cho kiểm thử và trình diễn). */
    @PostMapping("/quet-qua-han")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public KetQuaQuetResponse quetQuaHan() {
        return new KetQuaQuetResponse(jobs.quetQuaHan());
    }

    /** Quản trị: chạy ngay việc đối chiếu trạng thái giữ chỗ của các cổng (quy tắc 2). */
    @PostMapping("/quet-giu-cho")
    @PreAuthorize("hasRole('QUAN_TRI')")
    public KetQuaQuetResponse quetGiuCho() {
        return new KetQuaQuetResponse(jobs.quetGiuCho());
    }
}
