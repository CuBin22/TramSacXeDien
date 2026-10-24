package com.tramsac.suco;

import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cổng dùng chung cho cảnh báo tự động của giai đoạn 3 và luồng quản lý sự cố giai đoạn 5. */
@Service
public class SuCoService {
    private final SuCoRepository repository;
    private final MaGenerator maGenerator;
    private final AuditService audit;
    private final LichSuSuCoRepository historyRepository;
    private final Clock clock;
    public SuCoService(SuCoRepository repository, MaGenerator maGenerator, AuditService audit,
                       LichSuSuCoRepository historyRepository, Clock clock) {
        this.repository = repository; this.maGenerator = maGenerator; this.audit = audit;
        this.historyRepository = historyRepository; this.clock = clock;
    }
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean coSuCoTrenCong(String maCong) {
        return repository.existsByMaCong(maCong);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public SuCo taoNeuChuaMo(String maCong, String loai, String mucUuTien, String moTa) {
        SuCo existing = repository.timSuCoDangMo(maCong, loai).orElse(null);
        if (existing != null) return existing;
        SuCo created = repository.save(new SuCo(maGenerator.next("SC"), maCong, "Tự động", loai,
                mucUuTien, "Mới", moTa));
        historyRepository.save(new LichSuSuCo(maGenerator.next("LSSC"), created.getMaSuCo(),
                LocalDateTime.now(clock), "Mới", "Hệ thống", moTa));
        audit.ghi(null, "TAO_SU_CO_TU_DONG", "Cổng " + maCong + ": " + loai + " (" + mucUuTien + ")");
        return created;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void dongSuCoDangMo(String maCong) {
        repository.timDanhSachSuCoDangMo(maCong).forEach(s -> {
            s.dong();
            historyRepository.save(new LichSuSuCo(maGenerator.next("LSSC"), s.getMaSuCo(),
                    LocalDateTime.now(clock), "Đã đóng", "Kỹ thuật viên", "Thiết bị xác nhận an toàn."));
            audit.ghi(null, "DONG_SU_CO", "Đóng sự cố " + s.getMaSuCo() + " sau khi xác nhận cổng " + maCong + " an toàn.");
        });
    }
}
