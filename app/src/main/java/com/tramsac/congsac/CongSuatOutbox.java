package com.tramsac.congsac;

import com.tramsac.telemetry.TraceContext;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** One durable desired value per port; newer revisions supersede older commands. */
@Service
public class CongSuatOutbox {
    public record Command(String port, long revision, BigDecimal power, String parent) {
        public String id() { return "POWER-" + revision; }
    }
    private final JdbcTemplate jdbc;
    public CongSuatOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void save(CongSuatDaCanBang event) {
        for (var cap : event.dsCongSuat()) {
            jdbc.update("""
                insert into CongSuatOutbox(MaCong, Revision, CongSuatKw, TraceParent, DaAck)
                values (?, nextval('CongSuatCommandRevision'), ?, ?, false)
                on conflict (MaCong) do update set Revision=excluded.Revision,
                    CongSuatKw=excluded.CongSuatKw, TraceParent=excluded.TraceParent, DaAck=false
                """, cap.maCong(), cap.congSuatKw(), TraceContext.parent());
        }
    }
    public List<Command> pending() {
        return jdbc.query("select * from CongSuatOutbox where not DaAck order by Revision",
                (r, n) -> new Command(r.getString("MaCong"), r.getLong("Revision"),
                        r.getBigDecimal("CongSuatKw"), r.getString("TraceParent")));
    }
    @Transactional
    public void reconcile(String port, long deviceRevision) {
        jdbc.update("update CongSuatOutbox set DaAck=false where MaCong=? and Revision<>?", port, deviceRevision);
    }
    @Transactional
    public void ack(String port, String id, String result) {
        if (!"OK".equalsIgnoreCase(result) || !id.startsWith("POWER-")) return;
        long revision;
        try { revision = Long.parseLong(id.substring(6)); }
        catch (NumberFormatException invalid) { return; }
        jdbc.update("update CongSuatOutbox set DaAck=true where MaCong=? and Revision=?", port, revision);
    }
}
