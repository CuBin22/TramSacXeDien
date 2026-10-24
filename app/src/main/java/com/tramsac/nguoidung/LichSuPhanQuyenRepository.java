package com.tramsac.nguoidung;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LichSuPhanQuyenRepository extends JpaRepository<LichSuPhanQuyen, String> {

    List<LichSuPhanQuyen> findByMaNDOrderByThoiDiemDesc(String maND);
}
