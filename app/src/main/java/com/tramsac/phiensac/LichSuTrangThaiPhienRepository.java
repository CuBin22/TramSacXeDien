package com.tramsac.phiensac;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LichSuTrangThaiPhienRepository extends JpaRepository<LichSuTrangThaiPhien, String> {

    List<LichSuTrangThaiPhien> findByMaPhienOrderByThoiDiem(String maPhien);

    /**
     * Quy tắc 11: số lần "không đến" (Quá hạn không đến) hoặc "hủy sát giờ" của một người dùng từ mốc tu.
     * Các sự kiện này được ghi vào LyDo của bản ghi chuyển sang Hủy.
     */
    @Query("select count(l) from LichSuTrangThaiPhien l, PhienSac p, DatPhien d "
            + "where l.maPhien = p.maPhien and p.maDatPhien = d.maDatPhien and d.maND = :maND "
            + "and l.trangThaiMoi = 'Hủy' "
            + "and (l.lyDo like 'Quá hạn không đến%' or l.lyDo like 'Hủy sát giờ%') and l.thoiDiem >= :tu")
    long demViPham(@Param("maND") String maND, @Param("tu") LocalDateTime tu);

    @Query("select max(l.thoiDiem) from LichSuTrangThaiPhien l, PhienSac p, DatPhien d "
            + "where l.maPhien = p.maPhien and p.maDatPhien = d.maDatPhien and d.maND = :maND "
            + "and l.trangThaiMoi = 'Hủy' "
            + "and (l.lyDo like 'Quá hạn không đến%' or l.lyDo like 'Hủy sát giờ%') and l.thoiDiem >= :tu")
    LocalDateTime thoiDiemViPhamGanNhat(@Param("maND") String maND, @Param("tu") LocalDateTime tu);
}
