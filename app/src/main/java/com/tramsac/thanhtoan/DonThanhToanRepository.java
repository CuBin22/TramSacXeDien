package com.tramsac.thanhtoan;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DonThanhToanRepository extends JpaRepository<DonThanhToan, String> {
    Optional<DonThanhToan> findByKhoaIdempotency(String khoaIdempotency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DonThanhToan> findForUpdateByMaDon(String maDon);

    List<DonThanhToan> findByTrangThaiInAndHanThanhToanBefore(
            List<String> trangThai, LocalDateTime hanThanhToan);
}
