package com.tramsac.congsac;

import com.tramsac.common.ApiException;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CauHinhCongSuatService {
    private final CauHinhCongSuatRepository repository;
    private final CanBangTaiService canBang;
    private final AuditService audit;
    private final Clock clock;
    public CauHinhCongSuatService(CauHinhCongSuatRepository repository, CanBangTaiService canBang,
                                  AuditService audit, Clock clock) {
        this.repository = repository; this.canBang = canBang; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public CauHinhCongSuat capNhat(String id, CauHinhCongSuatRequest request) {
        CauHinhCongSuat config = repository.findForUpdateByMaCauHinh(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_CAU_HINH", "Không tìm thấy cấu hình công suất."));
        try { config.capNhat(request.congSuatDauNoi(), request.nguongGiamTai(), request.ngayApDung(), NguoiDungHienTai.layMaND()); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "CAU_HINH_KHONG_HOP_LE", e.getMessage()); }
        canBang.canBang(id, LocalDateTime.now(clock));
        audit.ghi(NguoiDungHienTai.layMaND(), "CAP_NHAT_CONG_SUAT", "Cấu hình " + id + " được cập nhật.");
        return config;
    }
}
