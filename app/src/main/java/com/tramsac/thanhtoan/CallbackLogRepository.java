package com.tramsac.thanhtoan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackLogRepository extends JpaRepository<CallbackLog, String> {
    boolean existsByMaGiaoDichAndSuKienAndKetQuaXuLy(String maGiaoDich, String suKien, String ketQuaXuLy);
}
