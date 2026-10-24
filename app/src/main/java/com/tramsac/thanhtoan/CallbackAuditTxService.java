package com.tramsac.thanhtoan;

import com.tramsac.common.MaGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallbackAuditTxService {
    private final CallbackLogRepository repository;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public CallbackAuditTxService(CallbackLogRepository repository, MaGenerator maGenerator, Clock clock) {
        this.repository = repository;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tuChoi(String signature) {
        repository.save(new CallbackLog(maGenerator.next("CB"), null, null, cat(signature),
                "KHONG_HOP_LE", "Bỏ qua", LocalDateTime.now(clock)));
    }

    private static String cat(String text) {
        return text != null && text.length() > 255 ? text.substring(0, 255) : text;
    }
}
