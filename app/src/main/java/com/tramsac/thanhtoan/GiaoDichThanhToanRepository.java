package com.tramsac.thanhtoan;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GiaoDichThanhToanRepository extends JpaRepository<GiaoDichThanhToan, String> {
    Optional<GiaoDichThanhToan> findByMaDon(String maDon);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GiaoDichThanhToan> findForUpdateByMaGiaoDich(String maGiaoDich);
}
