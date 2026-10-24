package com.tramsac.thanhtoan;

import com.tramsac.common.ApiException;
import com.tramsac.common.security.NguoiDungHienTai;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HoanTienService {
    private final HoanTienRepository repository;
    private final HoanTienTxService txService;
    private final CongThanhToan gateway;

    public HoanTienService(HoanTienRepository repository, HoanTienTxService txService, CongThanhToan gateway) {
        this.repository = repository;
        this.txService = txService;
        this.gateway = gateway;
    }

    @io.opentelemetry.instrumentation.annotations.WithSpan("thanh_toan.hoanTien")
    public HoanTienResponse hoanTien(String key, HoanTienRequest request) {
        kiemTraKhoa(key);
        HoanTien old = repository.findByKhoaIdempotency(key).orElse(null);
        if (old != null) return replay(old, request);

        txService.kiemTra(request.maGiaoDich(), request.soTienHoan(), request.lyDo());
        gateway.hoanTien(key, request.maGiaoDich(), request.soTienHoan());
        try {
            HoanTien refund = txService.tao(request.maGiaoDich(), request.soTienHoan(), request.lyDo(), key,
                    NguoiDungHienTai.layMaND());
            return HoanTienResponse.tu(refund, false);
        } catch (DataIntegrityViolationException e) {
            old = repository.findByKhoaIdempotency(key).orElse(null);
            if (old != null) return replay(old, request);
            throw new ApiException(HttpStatus.CONFLICT, "GIAO_DICH_DA_HOAN",
                    "Giao dịch này đã được hoàn tiền bằng một yêu cầu khác.");
        }
    }

    private static HoanTienResponse replay(HoanTien old, HoanTienRequest request) {
        if (!old.getMaGiaoDich().equals(request.maGiaoDich())
                || old.getSoTienHoan().compareTo(request.soTienHoan()) != 0
                || !old.getLyDo().equals(request.lyDo())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_DUNG_LAI",
                    "Idempotency-Key này đã được dùng với dữ liệu khác.");
        }
        return HoanTienResponse.tu(old, true);
    }

    private static void kiemTraKhoa(String key) {
        try {
            if (key == null || !UUID.fromString(key).toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_KHONG_HOP_LE",
                    "Header Idempotency-Key phải là UUID.");
        }
    }
}
