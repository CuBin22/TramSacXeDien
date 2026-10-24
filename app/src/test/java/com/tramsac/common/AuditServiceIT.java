package com.tramsac.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.tramsac.AbstractIntegrationTest;
import com.tramsac.common.audit.AuditService;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AuditServiceIT extends AbstractIntegrationTest {

    @Autowired
    AuditService audit;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM NhatKyHeThong WHERE HanhDong LIKE 'TEST%'");
    }

    @Test
    void ghi_persistsRowWithTimestamp() {
        audit.ghi(null, "TEST", "noi dung");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM NhatKyHeThong WHERE HanhDong = 'TEST'");
        assertThat(row.get("mand")).isNull();
        assertThat(row.get("thoidiem")).isNotNull();
        assertThat(row.get("noidung")).isEqualTo("noi dung");
        assertThat((String) row.get("manhatky")).startsWith("NK-");
    }

    @Test
    void ghi_joinsCallerTransaction_rollsBackTogether() {
        try {
            tx.executeWithoutResult(status -> {
                audit.ghi(null, "TEST_ROLLBACK", "se bi hoan tac");
                throw new IllegalStateException("loi nghiep vu");
            });
        } catch (IllegalStateException expected) {
            // giao dịch của người gọi bị hoàn tác
        }

        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM NhatKyHeThong WHERE HanhDong = 'TEST_ROLLBACK'", Integer.class);
        assertThat(n).isZero();
    }

    @Test
    void ghi_truncatesOverlongContent() {
        audit.ghi(null, "TEST_LONG", "x".repeat(600));

        String stored = jdbc.queryForObject(
                "SELECT NoiDung FROM NhatKyHeThong WHERE HanhDong = 'TEST_LONG'", String.class);
        assertThat(stored).hasSize(500);
    }
}
