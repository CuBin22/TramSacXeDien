package com.tramsac.thanhtoan;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook/thanh-toan")
public class WebhookThanhToanController {
    private final WebhookThanhToanService service;

    public WebhookThanhToanController(WebhookThanhToanService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebhookThanhToanResponse nhan(@RequestBody byte[] body,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        return service.xuLy(body, timestamp, signature);
    }
}
