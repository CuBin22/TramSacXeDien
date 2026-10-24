package com.tramsac.congsac;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface CauHinhCongSuatRepository extends JpaRepository<CauHinhCongSuat, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<CauHinhCongSuat> findForUpdateByMaCauHinh(String maCauHinh);
}
