package com.tramsac.suco;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LichSuSuCoRepository extends JpaRepository<LichSuSuCo, String> {
    java.util.List<LichSuSuCo> findByMaSuCoOrderByThoiDiemAsc(String maSuCo);
}
