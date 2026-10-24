package com.tramsac.bieugia;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietPhiRepository extends JpaRepository<ChiTietPhi, String> {
    List<ChiTietPhi> findByMaPhi(String maPhi);
}
