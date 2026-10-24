package com.tramsac.datphien;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatPhienRepository extends JpaRepository<DatPhien, String> {

    Optional<DatPhien> findByMaDatPhienAndMaND(String maDatPhien, String maND);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DatPhien> findForUpdateByMaDatPhienAndMaND(String maDatPhien, String maND);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DatPhien> findForUpdateByMaDatPhien(String maDatPhien);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DatPhien d where d.maCong = :maCong and d.trangThai = :trangThai "
            + "and d.gioBatDau > :now order by d.gioBatDau")
    List<DatPhien> timDatPhienTuongLaiTheoCongForUpdate(@Param("maCong") String maCong,
                                                        @Param("trangThai") String trangThai,
                                                        @Param("now") LocalDateTime now);

    @Query("select d.maDatPhien from DatPhien d where d.maCong = :maCong and d.trangThai = :trangThai "
            + "and d.gioBatDau > :now order by d.gioBatDau")
    List<String> timMaDatPhienTuongLaiTheoCong(@Param("maCong") String maCong,
                                                @Param("trangThai") String trangThai,
                                                @Param("now") LocalDateTime now);

    List<DatPhien> findByMaNDOrderByGioBatDauDesc(String maND);

    /** Sơ đồ tuần tự: demDatChoHieuLuc(maND). */
    long countByMaNDAndTrangThaiIn(String maND, Collection<String> trangThai);

    /** Sơ đồ tuần tự: timDatChoTrung(maND, khungGio, maDatPhienLoaiTru). loaiTru = "" nghĩa là không loại trừ. */
    @Query("select d from DatPhien d where d.maND = :maND and d.trangThai in :trangThai "
            + "and d.gioBatDau < :ketThuc and d.gioKetThuc > :batDau and d.maDatPhien <> :loaiTru")
    List<DatPhien> timDatChoTrung(@Param("maND") String maND, @Param("trangThai") Collection<String> trangThai,
                                  @Param("batDau") LocalDateTime batDau, @Param("ketThuc") LocalDateTime ketThuc,
                                  @Param("loaiTru") String loaiTru);

    /** Sơ đồ tuần tự: layLichDat(dsCong, khungGio) → dsCongTrungLich. */
    @Query("select distinct d.maCong from DatPhien d where d.maCong in :dsCong and d.trangThai in :trangThai "
            + "and d.gioBatDau < :ketThuc and d.gioKetThuc > :batDau and d.maDatPhien <> :loaiTru")
    List<String> timCongTrungLich(@Param("dsCong") Collection<String> dsCong,
                                  @Param("trangThai") Collection<String> trangThai,
                                  @Param("batDau") LocalDateTime batDau, @Param("ketThuc") LocalDateTime ketThuc,
                                  @Param("loaiTru") String loaiTru);

    /** Số đặt chỗ của một cổng bắt đầu trong khoảng [tuNgay, denNgay) (tiêu chí "ít phiên đã đặt trong ngày"). */
    @Query("select count(d) from DatPhien d where d.maCong = :maCong and d.trangThai in :trangThai "
            + "and d.gioBatDau >= :tuNgay and d.gioBatDau < :denNgay and d.maDatPhien <> :loaiTru")
    long demTrongKhoang(@Param("maCong") String maCong, @Param("trangThai") Collection<String> trangThai,
                        @Param("tuNgay") LocalDateTime tuNgay, @Param("denNgay") LocalDateTime denNgay,
                        @Param("loaiTru") String loaiTru);

    /** Mã các đặt chỗ ở trạng thái cho trước đã quá giờ bắt đầu trước mốc nguong (ứng viên quá hạn). */
    @Query("select d.maDatPhien from DatPhien d where d.trangThai = :trangThai and d.gioBatDau < :nguong "
            + "order by d.gioBatDau")
    List<String> timMaDatChoQuaHan(@Param("trangThai") String trangThai, @Param("nguong") LocalDateTime nguong);

    /** Đặt chỗ đang "giữ" cổng: còn Đã đặt, cửa sổ giờ đã mở (batDau <= moCua) và chưa kết thúc. Sớm nhất trước. */
    @Query("select d from DatPhien d where d.maCong = :maCong and d.trangThai = :trangThai "
            + "and d.gioBatDau <= :moCua and d.gioKetThuc > :bayGio order by d.gioBatDau")
    List<DatPhien> timChuGiuCho(@Param("maCong") String maCong, @Param("trangThai") String trangThai,
                                @Param("moCua") LocalDateTime moCua, @Param("bayGio") LocalDateTime bayGio);
}
