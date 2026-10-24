package com.tramsac.nguoidung;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XeRepository extends JpaRepository<Xe, String> {

    boolean existsByBienSo(String bienSo);

    long countByMaND(String maND);

    Optional<Xe> findByBienSo(String bienSo);

    Optional<Xe> findByMaXeAndMaND(String maXe, String maND);

    List<Xe> findByMaNDOrderByBienSo(String maND);
}
