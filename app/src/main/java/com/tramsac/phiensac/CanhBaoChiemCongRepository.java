package com.tramsac.phiensac;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanhBaoChiemCongRepository extends JpaRepository<CanhBaoChiemCong, String> {
    Optional<CanhBaoChiemCong> findTopByMaPhienOrderByLanCanhBaoDesc(String maPhien);
    List<CanhBaoChiemCong> findAllByOrderByThoiDiemDesc();
    List<CanhBaoChiemCong> findByMaPhienOrderByLanCanhBaoAsc(String maPhien);
}
