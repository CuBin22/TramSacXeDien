package com.tramsac.common.audit;

import com.tramsac.common.MaGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhật ký kiểm toán (NFR08). Chạy trong giao dịch của người gọi (REQUIRED) để nhật ký và
 * thao tác nghiệp vụ cùng thành công hoặc cùng hoàn tác.
 */
@Service
public class AuditService {

    private static final int MAX_HANH_DONG = 60;
    private static final int MAX_NOI_DUNG = 500;

    private final NhatKyRepository repository;
    private final MaGenerator maGenerator;
    private final Clock clock;

    public AuditService(NhatKyRepository repository, MaGenerator maGenerator, Clock clock) {
        this.repository = repository;
        this.maGenerator = maGenerator;
        this.clock = clock;
    }

    /** maND = null với thao tác do hệ thống tự thực hiện. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void ghi(String maND, String hanhDong, String noiDung) {
        repository.save(new NhatKyHeThong(
                maGenerator.next("NK"),
                maND,
                LocalDateTime.now(clock),
                cat(hanhDong, MAX_HANH_DONG),
                cat(noiDung, MAX_NOI_DUNG)));
    }

    private static String cat(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) : s;
    }
}
