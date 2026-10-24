package com.tramsac.mqtt;

public final class MqttTopics {
    private MqttTopics() { }
    public static String lenh(String maCong) { return "tramsac/cong/" + maCong + "/lenh"; }
    public static String ack(String maCong) { return "tramsac/cong/" + maCong + "/ack"; }
    public static String trangThai(String maCong) { return "tramsac/cong/" + maCong + "/trangthai"; }
    public static String heartbeat(String maCong) { return "tramsac/cong/" + maCong + "/heartbeat"; }
    public static String loi(String maCong) { return "tramsac/cong/" + maCong + "/loi"; }
}
