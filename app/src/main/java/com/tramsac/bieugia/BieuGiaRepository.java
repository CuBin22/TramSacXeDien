package com.tramsac.bieugia;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BieuGiaRepository extends JpaRepository<BieuGia, String> {
    Optional<BieuGia> findTopByNgayApDungLessThanEqualOrderByNgayApDungDesc(LocalDate ngay);
}
