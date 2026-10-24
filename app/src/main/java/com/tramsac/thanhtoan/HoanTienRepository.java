package com.tramsac.thanhtoan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoanTienRepository extends JpaRepository<HoanTien, String> {
    Optional<HoanTien> findByKhoaIdempotency(String khoaIdempotency);
    Optional<HoanTien> findByMaGiaoDich(String maGiaoDich);
}
