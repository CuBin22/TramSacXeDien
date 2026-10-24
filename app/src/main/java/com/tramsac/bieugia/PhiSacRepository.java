package com.tramsac.bieugia;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhiSacRepository extends JpaRepository<PhiSac, String> {
    Optional<PhiSac> findByMaPhien(String maPhien);
}
