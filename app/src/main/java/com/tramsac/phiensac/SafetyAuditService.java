package com.tramsac.phiensac;

import com.tramsac.common.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SafetyAuditService {
    private final AuditService audit;
    public SafetyAuditService(AuditService audit) { this.audit = audit; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ghi(String action, String reason) { audit.ghi(null, action, reason); }
}
