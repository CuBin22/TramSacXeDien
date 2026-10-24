package com.tramsac.phiensac;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LenhDieuKhienRepository extends JpaRepository<LenhDieuKhien, String> {
    @Query(value = "select distinct p.macong from lenhdieukhien l join phiensac p on p.maphien=l.maphien "
            + "where l.loailenh='BAT_DAU' and l.trangthai='Chờ ack' and p.trangthai='Chờ'", nativeQuery = true)
    List<String> timCongChoBatDau();
    Optional<LenhDieuKhien> findByKhoaIdempotency(String khoaIdempotency);
    boolean existsByMaPhienAndLoaiLenhAndTrangThai(String maPhien, String loaiLenh, String trangThai);

    @Query("select l.maPhien from LenhDieuKhien l where l.maLenh = :maLenh")
    Optional<String> timMaPhien(@Param("maLenh") String maLenh);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LenhDieuKhien l where l.maLenh = :maLenh")
    Optional<LenhDieuKhien> findForUpdate(@Param("maLenh") String maLenh);
    @Query("select l.maLenh from LenhDieuKhien l where l.trangThai = 'Chờ ack' and l.guiLuc <= :nguong "
            + "order by l.guiLuc")
    List<String> timLenhQuaHan(@Param("nguong") LocalDateTime nguong);
}
