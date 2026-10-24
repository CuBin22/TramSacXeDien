package com.tramsac.nguoidung;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.nguoidung.dto.CapNhatHoSoRequest;
import com.tramsac.nguoidung.dto.HoSoResponse;
import com.tramsac.nguoidung.dto.XeDto;
import com.tramsac.nguoidung.dto.XeRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC03 – Quản lý hồ sơ người dùng và danh sách xe của chính mình (BM3, QĐ3). */
@Service
public class HoSoService {

    static final int TOI_DA_XE = 3;

    private final NguoiDungRepository nguoiDungRepository;
    private final XeRepository xeRepository;
    private final MaGenerator maGenerator;
    private final AuditService auditService;

    public HoSoService(NguoiDungRepository nguoiDungRepository, XeRepository xeRepository,
                       MaGenerator maGenerator, AuditService auditService) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.xeRepository = xeRepository;
        this.maGenerator = maGenerator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public HoSoResponse layHoSo() {
        String maND = NguoiDungHienTai.layMaND();
        NguoiDung nd = nguoiDungRepository.findById(maND).orElseThrow(HoSoService::khongTimThayTaiKhoan);
        return HoSoResponse.tu(nd, xeRepository.findByMaNDOrderByBienSo(maND));
    }

    @Transactional
    public HoSoResponse capNhatHoSo(CapNhatHoSoRequest req) {
        String maND = NguoiDungHienTai.layMaND();
        // Khóa dòng của chính mình (lấy đối tượng mới sau khi có khóa) để các lần sửa đồng thời xếp hàng
        NguoiDung nd = nguoiDungRepository.findForUpdateByMaND(maND).orElseThrow(HoSoService::khongTimThayTaiKhoan);

        if (req.doiTuong() != null && !req.doiTuong().equals(nd.getDoiTuong())) {
            throw chiQuanTriDoi();
        }
        if (req.vaiTro() != null && !req.vaiTro().equals(nd.layVaiTro().giaTri())) {
            throw chiQuanTriDoi();
        }

        String email = DangKyService.chuanHoaEmail(req.email());
        String sdt = DangKyService.chuanHoaSoDienThoai(req.soDienThoai());
        DangKyService.kiemTraLienHe(email, sdt);
        if (email != null) {
            kiemTraKhongTrung(nguoiDungRepository.timMaNDTheoEmail(email), maND, "Email đã được sử dụng.");
        }
        if (sdt != null) {
            kiemTraKhongTrung(nguoiDungRepository.timMaNDTheoSoDienThoai(sdt), maND,
                    "Số điện thoại đã được sử dụng.");
        }

        nd.capNhatHoSo(req.hoTen().trim(), email, sdt);
        nguoiDungRepository.saveAndFlush(nd);   // UNIQUE của CSDL là trọng tài cuối khi hai người đổi cùng lúc
        auditService.ghi(maND, "CAP_NHAT_HO_SO", "Cập nhật họ tên, email, số điện thoại");
        return HoSoResponse.tu(nd, xeRepository.findByMaNDOrderByBienSo(maND));
    }

    @Transactional
    public XeDto themXe(XeRequest req) {
        String maND = NguoiDungHienTai.layMaND();
        // Khóa dòng chủ xe trước khi đếm: nếu không, 5 yêu cầu đồng thời đều thấy "chưa đủ 3 xe"
        nguoiDungRepository.findForUpdateByMaND(maND).orElseThrow(HoSoService::khongTimThayTaiKhoan);

        if (xeRepository.countByMaND(maND) >= TOI_DA_XE) {
            throw new ApiException(HttpStatus.CONFLICT, "VUOT_SO_XE", "Mỗi tài khoản chỉ có tối đa 3 xe.");
        }
        String bienSo = DangKyService.chuanHoaBienSo(req.bienSo());
        if (xeRepository.existsByBienSo(bienSo)) {
            throw trungBienSo();
        }
        Xe xe = xeRepository.saveAndFlush(new Xe(maGenerator.next("XE"), maND, bienSo, req.loaiXe().trim(),
                req.loaiCongSuDung()));
        auditService.ghi(maND, "THEM_XE", "Thêm xe " + bienSo);
        return XeDto.tu(xe);
    }

    @Transactional
    public XeDto suaXe(String maXe, XeRequest req) {
        String maND = NguoiDungHienTai.layMaND();
        Xe xe = xeRepository.findByMaXeAndMaND(maXe, maND).orElseThrow(HoSoService::khongTimThayXe);

        String bienSo = DangKyService.chuanHoaBienSo(req.bienSo());
        Optional<Xe> trung = xeRepository.findByBienSo(bienSo);
        if (trung.isPresent() && !trung.get().getMaXe().equals(maXe)) {
            throw trungBienSo();
        }
        xe.capNhat(bienSo, req.loaiXe().trim(), req.loaiCongSuDung());
        xeRepository.saveAndFlush(xe);
        auditService.ghi(maND, "SUA_XE", "Sửa xe " + bienSo);
        return XeDto.tu(xe);
    }

    @Transactional
    public void xoaXe(String maXe) {
        String maND = NguoiDungHienTai.layMaND();
        Xe xe = xeRepository.findByMaXeAndMaND(maXe, maND).orElseThrow(HoSoService::khongTimThayXe);
        try {
            xeRepository.delete(xe);
            xeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "XE_DANG_DUOC_DUNG",
                    "Xe đã có đặt chỗ nên không thể xóa.");
        }
        auditService.ghi(maND, "XOA_XE", "Xóa xe " + xe.getBienSo());
    }

    private static void kiemTraKhongTrung(Optional<String> maNDKhac, String maND, String thongBao) {
        if (maNDKhac.isPresent() && !maNDKhac.get().equals(maND)) {
            throw new ApiException(HttpStatus.CONFLICT, "TRUNG_DU_LIEU", thongBao);
        }
    }

    private static ApiException trungBienSo() {
        return new ApiException(HttpStatus.CONFLICT, "TRUNG_BIEN_SO", "Biển số xe đã gắn với tài khoản khác.");
    }

    private static ApiException chiQuanTriDoi() {
        return new ApiException(HttpStatus.FORBIDDEN, "CHI_QUAN_TRI_DOI_DOI_TUONG",
                "Đối tượng và vai trò chỉ do Quản trị viên thay đổi.");
    }

    private static ApiException khongTimThayXe() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_XE", "Không tìm thấy xe.");
    }

    private static ApiException khongTimThayTaiKhoan() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CHUA_XAC_THUC", "Cần đăng nhập.");
    }
}
