package com.tramsac.suco;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuCoRepository extends JpaRepository<SuCo, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<SuCo> findForUpdateByMaSuCo(String maSuCo);
    boolean existsByMaCong(String maCong);
    Optional<SuCo> findFirstByMaCongAndLoaiSuCoAndTrangThaiNotInOrderByMaSuCoAsc(String maCong, String loai, java.util.Collection<String> trangThai);
    default Optional<SuCo> timSuCoDangMo(String maCong, String loai) {
        return findFirstByMaCongAndLoaiSuCoAndTrangThaiNotInOrderByMaSuCoAsc(maCong, loai, java.util.List.of("Đã đóng", "Đã hủy"));
    }
    @Query("select s from SuCo s where s.maCong = :maCong and s.trangThai not in ('Đã đóng','Đã hủy')")
    java.util.List<SuCo> timDanhSachSuCoDangMo(@Param("maCong") String maCong);
}
