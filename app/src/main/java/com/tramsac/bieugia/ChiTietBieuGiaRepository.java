package com.tramsac.bieugia;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietBieuGiaRepository extends JpaRepository<ChiTietBieuGia, String> {
    List<ChiTietBieuGia> findByMaBieuGiaAndLoaiCongAndDoiTuong(String maBieuGia, String loaiCong, String doiTuong);
}
