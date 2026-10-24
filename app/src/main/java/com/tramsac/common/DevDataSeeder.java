package com.tramsac.common;

import com.tramsac.congsac.CauHinhCongSuat;
import com.tramsac.congsac.CauHinhCongSuatRepository;
import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.NguoiDungRepository;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import com.tramsac.nguoidung.XeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp tài khoản mẫu cho môi trường dev (mật khẩu Demo1234). KHÔNG bao giờ bật profile dev ở môi trường thật.
 * Chạy lặp lại nhiều lần không tạo trùng.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    public static final String MAT_KHAU_MAU = "Demo1234";
    static final String MA_CAU_HINH_MAU = "CH-MAC-DINH";

    private final NguoiDungRepository nguoiDungRepository;
    private final XeRepository xeRepository;
    private final PasswordEncoder passwordEncoder;
    private final MaGenerator maGenerator;
    private final CongSacRepository congSacRepository;
    private final CauHinhCongSuatRepository cauHinhRepository;

    public DevDataSeeder(NguoiDungRepository nguoiDungRepository, XeRepository xeRepository,
                         PasswordEncoder passwordEncoder, MaGenerator maGenerator,
                         CongSacRepository congSacRepository, CauHinhCongSuatRepository cauHinhRepository) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.xeRepository = xeRepository;
        this.passwordEncoder = passwordEncoder;
        this.maGenerator = maGenerator;
        this.congSacRepository = congSacRepository;
        this.cauHinhRepository = cauHinhRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        taoTaiKhoan("admin@tramsac.local", "Quản trị viên mẫu", "Cán bộ", VaiTro.QUAN_TRI, null);
        taoTaiKhoan("ktv@tramsac.local", "Kỹ thuật viên mẫu", "Cán bộ", VaiTro.KY_THUAT_VIEN, null);
        taoTaiKhoan("truc@tramsac.local", "Nhân viên trực mẫu", "Cán bộ", VaiTro.NHAN_VIEN_TRUC, null);
        taoTaiKhoan("user@tramsac.local", "Người dùng mẫu", "Sinh viên", VaiTro.NGUOI_DUNG, "59A-123.45");

        if (!cauHinhRepository.existsById(MA_CAU_HINH_MAU)) {
            cauHinhRepository.save(new CauHinhCongSuat(MA_CAU_HINH_MAU, new BigDecimal("100.00"),
                    new BigDecimal("80.00"), LocalDate.now(), "Dữ liệu mẫu"));
        }
        taoCong("C-AC-01", "AC", "7.40");
        taoCong("C-AC-02", "AC", "7.40");
        taoCong("C-DC-01", "DC", "30.00");
        taoCong("C-DC-02", "DC", "30.00");
    }

    private void taoCong(String maCong, String loaiCong, String congSuatDinhMuc) {
        if (!congSacRepository.existsById(maCong)) {
            congSacRepository.save(new CongSac(maCong, MA_CAU_HINH_MAU, loaiCong, new BigDecimal(congSuatDinhMuc)));
        }
    }

    private void taoTaiKhoan(String email, String hoTen, String doiTuong, VaiTro vaiTro, String bienSo) {
        if (nguoiDungRepository.existsByEmail(email)) {
            return;
        }
        String maND = maGenerator.next("ND");
        nguoiDungRepository.save(new NguoiDung(maND, hoTen, email, null,
                passwordEncoder.encode(MAT_KHAU_MAU), doiTuong, null, vaiTro));
        if (bienSo != null && !xeRepository.existsByBienSo(bienSo)) {
            xeRepository.save(new Xe(maGenerator.next("XE"), maND, bienSo, "Ô tô điện", "AC"));
        }
    }
}
