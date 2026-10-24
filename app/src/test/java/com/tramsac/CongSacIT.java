package com.tramsac;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.datphien.DatPhienJobs;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.Xe;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Lớp cơ sở cho test đặt phiên: dọn toàn bộ cổng, đặt chỗ, phiên và lịch sử trước và sau mỗi test để kết quả phân
 * bổ cổng không phụ thuộc dữ liệu còn sót (ví dụ cổng mẫu của profile dev). Thời gian trong test luôn tính từ
 * bayGio() (đồng hồ test, UTC, làm tròn xuống phút).
 */
public abstract class CongSacIT extends AuthenticatedIT {

    @Autowired
    protected DatPhienJobs jobs;

    @BeforeEach
    void sachDuLieuCongTruoc() {
        xoaDuLieuCong();
    }

    @AfterEach
    void sachDuLieuCongSau() {
        xoaDuLieuCong();
    }

    private void xoaDuLieuCong() {
        // Thứ tự xóa theo khóa ngoại: bảng con (thanh toán, phí, lệnh, cảnh báo) trước PhienSac; sự cố và lịch sử
        // kết nối trước CongSac.
        jdbc.update("DELETE FROM CallbackLog");
        jdbc.update("DELETE FROM HoanTien");
        jdbc.update("DELETE FROM GiaoDichThanhToan");
        jdbc.update("DELETE FROM DonThanhToan");
        jdbc.update("DELETE FROM ChiTietPhi");
        jdbc.update("DELETE FROM PhiSac");
        jdbc.update("DELETE FROM LenhDieuKhien");
        jdbc.update("DELETE FROM CanhBaoChiemCong");
        jdbc.update("DELETE FROM LichSuTrangThaiPhien");
        jdbc.update("DELETE FROM PhienSac");
        jdbc.update("DELETE FROM DatPhien");
        jdbc.update("DELETE FROM LichSuSuCo");
        jdbc.update("DELETE FROM SuCo");
        jdbc.update("DELETE FROM LichSuKetNoi");
        jdbc.update("DELETE FROM CongSac");
        jdbc.update("DELETE FROM CauHinhCongSuat");
    }

    // ---------- dữ liệu cổng ----------

    protected void taoCauHinh(String maCauHinh, int congSuatDauNoi) {
        jdbc.update("INSERT INTO CauHinhCongSuat (MaCauHinh, CongSuatDauNoi, NguongGiamTai, NgayApDung, NguoiCauHinh) "
                + "VALUES (?, ?, 80, current_date, 'test')", maCauHinh, congSuatDauNoi);
    }

    protected void taoCong(String maCong, String loaiCong) {
        taoCong(maCong, loaiCong, "Rảnh", null, 0);
    }

    protected void taoCong(String maCong, String loaiCong, String trangThai, String maCauHinh, int congSuatDuocCap) {
        if (maCauHinh == null) {
            jdbc.update("INSERT INTO CongSac (MaCong, LoaiCong, CongSuatDinhMuc, CongSuatDuocCap, TrangThai) "
                    + "VALUES (?, ?, 7.40, ?, ?)", maCong, loaiCong, congSuatDuocCap, trangThai);
        } else {
            jdbc.update("INSERT INTO CongSac (MaCong, MaCauHinh, LoaiCong, CongSuatDinhMuc, CongSuatDuocCap, "
                    + "TrangThai) VALUES (?, ?, ?, 7.40, ?, ?)", maCong, maCauHinh, loaiCong, congSuatDuocCap,
                    trangThai);
        }
    }

    protected void datTrangThaiCong(String maCong, String trangThai) {
        jdbc.update("UPDATE CongSac SET TrangThai = ? WHERE MaCong = ?", trangThai, maCong);
    }

    protected String trangThaiCong(String maCong) {
        return jdbc.queryForObject("SELECT TrangThai FROM CongSac WHERE MaCong = ?", String.class, maCong);
    }

    // ---------- đặt chỗ ----------

    protected LocalDateTime bayGio() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    protected static String iso(LocalDateTime t) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t.atOffset(ZoneOffset.UTC));
    }

    protected ResultActions datPhien(String token, String bienSo, String loaiCong, LocalDateTime batDau,
                                     LocalDateTime ketThuc) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bienSo", bienSo);
        body.put("loaiCong", loaiCong);
        body.put("gioBatDau", iso(batDau));
        body.put("gioKetThuc", iso(ketThuc));
        return gui(HttpMethod.POST, "/api/dat-phien", token, body);
    }

    /** Đặt chỗ qua API, bắt buộc thành công (201); trả về mã đặt chỗ. */
    protected String datPhienThanhCong(String token, Xe xe, LocalDateTime batDau, LocalDateTime ketThuc)
            throws Exception {
        MvcResult r = datPhien(token, xe.getBienSo(), xe.getLoaiCongSuDung(), batDau, ketThuc)
                .andExpect(status().isCreated()).andReturn();
        return json(r).get("maDatPhien").asText();
    }

    protected ResultActions huy(String token, String maDatPhien, String lyDo) throws Exception {
        return gui(HttpMethod.POST, "/api/dat-phien/" + maDatPhien + "/huy", token, Map.of("lyDo", lyDo));
    }

    protected ResultActions doiLich(String token, String maDatPhien, LocalDateTime batDau, LocalDateTime ketThuc,
                                    String lyDo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gioBatDau", iso(batDau));
        body.put("gioKetThuc", iso(ketThuc));
        body.put("lyDo", lyDo);
        return gui(HttpMethod.PUT, "/api/dat-phien/" + maDatPhien + "/doi-lich", token, body);
    }

    /** Chèn thẳng đặt chỗ + phiên vào CSDL (bỏ qua mọi quy định), dùng để dựng tình huống trong quá khứ/biên. */
    protected String taoDatChoTrucTiep(NguoiDung nd, Xe xe, String maCong, LocalDateTime batDau,
                                       LocalDateTime ketThuc, String trangThaiDat, String trangThaiPhien) {
        String maDatPhien = "DP-" + uid();
        jdbc.update("INSERT INTO DatPhien (MaDatPhien, MaND, MaXe, MaCong, NgayDat, GioBatDau, GioKetThuc, "
                        + "LoaiCong, TrangThai) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                maDatPhien, nd.getMaND(), xe.getMaXe(), maCong, bayGio(), batDau, ketThuc,
                xe.getLoaiCongSuDung(), trangThaiDat);
        jdbc.update("INSERT INTO PhienSac (MaPhien, MaDatPhien, MaCong, TrangThai) VALUES (?, ?, ?, ?)",
                "PS-" + uid(), maDatPhien, maCong, trangThaiPhien);
        return maDatPhien;
    }

    protected String trangThaiDatPhien(String maDatPhien) {
        return jdbc.queryForObject("SELECT TrangThai FROM DatPhien WHERE MaDatPhien = ?", String.class, maDatPhien);
    }

    protected String trangThaiPhien(String maDatPhien) {
        return jdbc.queryForObject("SELECT TrangThai FROM PhienSac WHERE MaDatPhien = ?", String.class, maDatPhien);
    }

    protected String maCongCuaDatPhien(String maDatPhien) {
        return jdbc.queryForObject("SELECT MaCong FROM DatPhien WHERE MaDatPhien = ?", String.class, maDatPhien);
    }

    protected LocalDateTime gioBatDauTrongDb(String maDatPhien) {
        return jdbc.queryForObject("SELECT GioBatDau FROM DatPhien WHERE MaDatPhien = ?", LocalDateTime.class,
                maDatPhien);
    }

    protected int soNhatKy(NguoiDung nd, String hanhDong) {
        return jdbc.queryForObject("SELECT count(*) FROM NhatKyHeThong WHERE MaND = ? AND HanhDong = ?",
                Integer.class, nd.getMaND(), hanhDong);
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
