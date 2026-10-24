package com.tramsac.phiensac;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhienSacRepository extends JpaRepository<PhienSac, String> {

    Optional<PhienSac> findByMaDatPhien(String maDatPhien);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhienSac> findForUpdateByMaPhien(String maPhien);

    @Query("select p.maDatPhien from PhienSac p where p.maPhien = :maPhien")
    Optional<String> timMaDatPhien(@Param("maPhien") String maPhien);

    @Query("select p from PhienSac p, DatPhien d where p.maDatPhien = d.maDatPhien "
            + "and p.maPhien = :maPhien and d.maND = :maND")
    Optional<PhienSac> findByMaPhienAndMaND(@Param("maPhien") String maPhien, @Param("maND") String maND);

    @Query("select p from PhienSac p, DatPhien d where p.maDatPhien = d.maDatPhien "
            + "and d.maND = :maND order by p.batDauLuc desc nulls last")
    java.util.List<PhienSac> findByMaND(@Param("maND") String maND);

    @Query("select p.maPhien from PhienSac p where p.trangThai = :trangThai and p.choLuc < :nguong "
            + "order by p.choLuc")
    java.util.List<String> timMaPhienChoQuaHan(@Param("trangThai") String trangThai,
                                               @Param("nguong") java.time.LocalDateTime nguong);

    @Query("select p.maPhien from PhienSac p where p.maCong = :maCong and p.trangThai in :trangThai")
    java.util.List<String> timMaPhienTheoCongVaTrangThai(@Param("maCong") String maCong,
                                                          @Param("trangThai") java.util.Collection<String> trangThai);

    @Query("select p from PhienSac p where p.trangThai in :trangThai and p.ketThucLuc is not null")
    java.util.List<PhienSac> findEndedByStatuses(@Param("trangThai") java.util.Collection<String> trangThai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhienSac> findForUpdateByMaDatPhien(String maDatPhien);

    /** Số phiên của một người dùng đang ở trạng thái cho trước (dùng cho quy tắc 13: còn Chờ thanh toán). */
    @Query("select count(p) from PhienSac p, DatPhien d where p.maDatPhien = d.maDatPhien "
            + "and d.maND = :maND and p.trangThai = :trangThai")
    long demTheoNguoiDungVaTrangThai(@Param("maND") String maND, @Param("trangThai") String trangThai);
}
