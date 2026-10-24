package com.tramsac;

import com.tramsac.mqtt.MqttGateway;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cổng MQTT giả cho test: ghi lại mọi thông điệp app gửi đi và cho phép test quyết định thiết bị có xác nhận an toàn
 * hay không. Test mô phỏng thiết bị trả lời bằng cách gọi trực tiếp MqttMessageHandler.nhan(...).
 */
public class FakeMqttGateway implements MqttGateway {

    public record DaGui(String topic, Map<String, ?> payload) { }

    private final List<DaGui> daGui = new CopyOnWriteArrayList<>();
    private volatile boolean anToan = true;
    private volatile boolean ketNoi = true;

    @Override
    public void publishJson(String topic, Map<String, ?> payload) {
        daGui.add(new DaGui(topic, payload));
    }

    @Override
    public CompletableFuture<Boolean> hoiAnToan(String maCong) {
        return CompletableFuture.completedFuture(anToan);
    }

    @Override
    public void hoanTatHoiAnToan(String maLenh, boolean anToan) { }

    @Override
    public boolean dangKetNoi() {
        return ketNoi;
    }

    public void datLai() {
        daGui.clear();
        anToan = true;
        ketNoi = true;
    }

    public void datAnToan(boolean value) { this.anToan = value; }

    public void datKetNoi(boolean value) { this.ketNoi = value; }

    public List<DaGui> danhSachGui() { return List.copyOf(daGui); }

    /** Số lần một lệnh (theo maLenh) đã được publish, kể cả các lần gửi lại. */
    public long soLanGuiLenh(String maLenh) {
        return daGui.stream().filter(g -> maLenh.equals(String.valueOf(g.payload().get("maLenh")))).count();
    }

    /** Số thông điệp có trường loai bằng giá trị cho trước (ví dụ DUNG, BAT_DAU). */
    public long soThongDiepLoai(String loai) {
        return daGui.stream().filter(g -> loai.equals(String.valueOf(g.payload().get("loai")))).count();
    }
}
