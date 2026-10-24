package com.tramsac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.NguoiDungRepository;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import com.tramsac.nguoidung.XeRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Lớp cơ sở cho test cần người dùng đã đăng nhập. Mọi tài khoản test có email kết thúc bằng @u.test
 * hoặc số điện thoại bắt đầu bằng 0777 và được dọn sau mỗi test.
 */
public abstract class AuthenticatedIT extends AbstractIntegrationTest {

    protected static final String MAT_KHAU = "Abcd1234";

    @Autowired
    protected NguoiDungRepository nguoiDungRepository;
    @Autowired
    protected XeRepository xeRepository;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected ObjectMapper om;

    @AfterEach
    void xoaNguoiDungTest() {
        List<String> ids = jdbc.queryForList(
                "SELECT MaND FROM NguoiDung WHERE Email LIKE '%@u.test' OR SoDienThoai LIKE '0777%'", String.class);
        for (String id : ids) {
            jdbc.update("DELETE FROM LichSuTrangThaiPhien WHERE MaPhien IN (SELECT p.MaPhien FROM PhienSac p "
                    + "JOIN DatPhien d ON d.MaDatPhien = p.MaDatPhien WHERE d.MaND = ?)", id);
            jdbc.update("DELETE FROM PhienSac WHERE MaDatPhien IN (SELECT MaDatPhien FROM DatPhien WHERE MaND = ?)", id);
            jdbc.update("DELETE FROM DatPhien WHERE MaND = ?", id);
            jdbc.update("DELETE FROM Xe WHERE MaND = ?", id);
            jdbc.update("DELETE FROM LichSuPhanQuyen WHERE MaND = ?", id);
            jdbc.update("DELETE FROM NhatKyHeThong WHERE MaND = ?", id);
            jdbc.update("DELETE FROM NguoiDung WHERE MaND = ?", id);
        }
    }

    protected NguoiDung taoNguoiDung(VaiTro vaiTro) {
        String id = uid();
        return nguoiDungRepository.save(new NguoiDung("ND-" + id, "Người Test", "u" + id + "@u.test", null,
                encoder.encode(MAT_KHAU), "Sinh viên", null, vaiTro));
    }

    protected Xe themXe(NguoiDung nd, String bienSo, String loaiCong) {
        return xeRepository.save(new Xe("XE-" + uid(), nd.getMaND(), bienSo, "Ô tô điện", loaiCong));
    }

    protected String dangNhapLayToken(NguoiDung nd) throws Exception {
        String body = gui(HttpMethod.POST, "/api/auth/dang-nhap", null,
                Map.of("dinhDanh", nd.getEmail(), "matKhau", MAT_KHAU))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(body).get("token").asText();
    }

    protected ResultActions gui(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder rb = MockMvcRequestBuilders.request(method, url);
        if (token != null) {
            rb.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            rb.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
                    .content(om.writeValueAsString(body));
        }
        return mvc.perform(rb);
    }

    protected static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String bienSoMoi() {
        return "T" + uid().toUpperCase();
    }
}
