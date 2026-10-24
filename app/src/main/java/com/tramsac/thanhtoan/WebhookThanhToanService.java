package com.tramsac.thanhtoan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.common.ApiException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WebhookThanhToanService {
    private final ObjectMapper objectMapper;
    private final WebhookSignatureService signatureService;
    private final CallbackAuditTxService auditService;
    private final WebhookTxService txService;

    public WebhookThanhToanService(ObjectMapper objectMapper, WebhookSignatureService signatureService,
                                   CallbackAuditTxService auditService, WebhookTxService txService) {
        this.objectMapper = objectMapper;
        this.signatureService = signatureService;
        this.auditService = auditService;
        this.txService = txService;
    }

    public WebhookThanhToanResponse xuLy(byte[] rawBody, String timestamp, String signature) {
        if (!signatureService.hopLe(timestamp, signature, rawBody)) {
            auditService.tuChoi(signature);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CHU_KY_WEBHOOK_KHONG_HOP_LE",
                    "Chữ ký webhook sai hoặc timestamp đã quá hạn.");
        }
        try {
            WebhookThanhToanEvent event = objectMapper.readValue(rawBody, WebhookThanhToanEvent.class);
            if (event.maGiaoDich() == null || event.maDon() == null || event.suKien() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_THIEU_TRUONG",
                        "Webhook thiếu mã giao dịch, mã đơn hoặc sự kiện.");
            }
            return txService.xuLy(event, signature);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_JSON_KHONG_HOP_LE", "Nội dung webhook không hợp lệ.");
        }
    }
}
