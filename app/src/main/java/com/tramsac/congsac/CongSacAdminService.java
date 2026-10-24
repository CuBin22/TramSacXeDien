package com.tramsac.congsac;

import com.tramsac.common.ApiException;
import com.tramsac.mqtt.MqttGateway;
import com.tramsac.phiensac.SafetyAuditService;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CongSacAdminService {
    private final CongSacAdminTxService tx;
    private final PhanBoLaiDatPhienService reassignment;
    private final MqttGateway mqtt;
    private final SafetyAuditService audit;
    public CongSacAdminService(CongSacAdminTxService tx, PhanBoLaiDatPhienService reassignment,
                               MqttGateway mqtt, SafetyAuditService audit) {
        this.tx = tx; this.reassignment = reassignment; this.mqtt = mqtt; this.audit = audit;
    }
    public java.util.List<CongSacResponse> danhSach() { return tx.danhSach(); }
    public CongSacResponse khoa(String maCong) {
        CongSacResponse result = tx.khoa(maCong);
        reassignment.phanBoLai(maCong);
        return result;
    }
    public CongSacResponse mo(String maCong) {
        return tx.mo(maCong, safe(maCong, "TU_CHOI_MO_CONG"));
    }
    public CongSacResponse xacNhanXuLy(String maCong) {
        return tx.xacNhanXuLy(maCong, safe(maCong, "TU_CHOI_XAC_NHAN_XU_LY"));
    }
    private boolean safe(String maCong, String action) {
        boolean safe = false;
        if (mqtt.dangKetNoi()) {
            try { safe = mqtt.hoiAnToan(maCong).get(4, TimeUnit.SECONDS); }
            catch (Exception ignored) { safe = false; }
        }
        if (!safe) {
            audit.ghi(action, "Cổng " + maCong + " không có xác nhận an toàn từ thiết bị.");
            throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN", "Thiết bị không xác nhận an toàn; cổng vẫn khóa.");
        }
        return true;
    }
}
