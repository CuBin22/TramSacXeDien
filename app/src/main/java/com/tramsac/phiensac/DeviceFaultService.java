package com.tramsac.phiensac;

import com.tramsac.congsac.PhanBoLaiDatPhienService;
import com.tramsac.congsac.CanBangTaiService;
import com.tramsac.mqtt.MqttGateway;
import com.tramsac.mqtt.MqttTopics;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeviceFaultService {
    private final MqttGateway mqtt;
    private final DeviceFaultTxService txService;
    private final PhanBoLaiDatPhienService reassignment;
    private final CanBangTaiService canBangTaiService;
    public DeviceFaultService(MqttGateway mqtt, DeviceFaultTxService txService,
                              PhanBoLaiDatPhienService reassignment, CanBangTaiService canBangTaiService) {
        this.mqtt = mqtt; this.txService = txService; this.reassignment = reassignment;
        this.canBangTaiService = canBangTaiService;
    }
    public void loi(String maCong, String loaiLoi) {
        xuLy(maCong, loaiLoi, true);
    }
    public void loiDoNhanVienBao(String maCong, String loaiLoi) {
        xuLy(maCong, loaiLoi, false);
    }
    private void xuLy(String maCong, String loaiLoi, boolean taoSuCoTuDong) {
        try { mqtt.publishJson(MqttTopics.lenh(maCong), Map.of("maLenh", "EMG-" + UUID.randomUUID(), "loai", "DUNG")); }
        catch (RuntimeException ignored) { /* Fail-safe: khóa phiên/cổng DB dù broker không có mặt. */ }
        String maCauHinh = txService.ghiNhan(maCong, loaiLoi, taoSuCoTuDong);
        if (maCauHinh != null) canBangTaiService.canBangNgoaiGiaoDich(maCauHinh);
        reassignment.phanBoLai(maCong);
    }
}
