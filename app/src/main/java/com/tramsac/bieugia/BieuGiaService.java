package com.tramsac.bieugia;

import com.tramsac.common.ApiException;
import com.tramsac.common.MaGenerator;
import com.tramsac.common.audit.AuditService;
import com.tramsac.common.security.NguoiDungHienTai;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BieuGiaService {
    private static final List<String> PORT_TYPES = List.of("AC", "DC");
    private static final List<String> USER_TYPES = List.of("Sinh viên", "Cán bộ", "Khách vãng lai", "Xe công vụ");
    private final BieuGiaRepository bieuGiaRepository;
    private final ChiTietBieuGiaRepository detailRepository;
    private final MaGenerator maGenerator;
    private final AuditService audit;
    public BieuGiaService(BieuGiaRepository bieuGiaRepository, ChiTietBieuGiaRepository detailRepository,
                          MaGenerator maGenerator, AuditService audit) {
        this.bieuGiaRepository = bieuGiaRepository; this.detailRepository = detailRepository;
        this.maGenerator = maGenerator; this.audit = audit;
    }
    @Transactional
    public BieuGiaResponse tao(BieuGiaRequest request) {
        validateCoverage(request.chiTiet());
        String id = maGenerator.next("BG");
        BieuGia rate = bieuGiaRepository.save(new BieuGia(id, request.tenBieuGia().trim(), request.ngayApDung(),
                NguoiDungHienTai.layMaND()));
        List<ChiTietBieuGia> details = request.chiTiet().stream().map(d -> new ChiTietBieuGia(
                maGenerator.next("CTBG"), id, d.khungGio().trim(), d.loaiCong(), d.doiTuong(), d.donGia(),
                d.gioBatDau(), d.gioKetThuc())).toList();
        detailRepository.saveAll(details);
        audit.ghi(NguoiDungHienTai.layMaND(), "CAP_NHAT_BIEU_GIA", "Tạo biểu giá " + id + " áp dụng từ " + request.ngayApDung());
        return new BieuGiaResponse(rate.getMaBieuGia(), rate.getTenBieuGia(), rate.getNgayApDung(), request.chiTiet());
    }
    private static void validateCoverage(List<ChiTietBieuGiaRequest> details) {
        for (ChiTietBieuGiaRequest d : details) {
            if (!PORT_TYPES.contains(d.loaiCong()) || !USER_TYPES.contains(d.doiTuong())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BIEU_GIA_KHONG_HOP_LE", "Loại cổng hoặc đối tượng không hợp lệ.");
            }
            if ((d.gioBatDau() == null) != (d.gioKetThuc() == null)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BIEU_GIA_KHONG_HOP_LE", "Cần nhập cả giờ bắt đầu và giờ kết thúc cho khung giá.");
            }
        }
        for (String port : PORT_TYPES) for (String user : USER_TYPES) {
            List<ChiTietBieuGiaRequest> group = details.stream()
                    .filter(d -> d.loaiCong().equals(port) && d.doiTuong().equals(user)).toList();
            for (int minute = 0; minute < 1440; minute++) {
                LocalTime time = LocalTime.of(minute / 60, minute % 60);
                long matches = group.stream().filter(d -> apDung(d.gioBatDau(), d.gioKetThuc(), time)).count();
                if (matches != 1) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "BIEU_GIA_KHONG_HOP_LE",
                            "Mỗi phút trong ngày phải có đúng một mức giá cho " + port + " / " + user + ".");
                }
            }
        }
    }
    private static boolean apDung(LocalTime start, LocalTime end, LocalTime time) {
        if (start == null || start.equals(end)) return true;
        return start.isBefore(end) ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
    }
}
