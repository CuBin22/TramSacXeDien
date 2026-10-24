package com.tramsac.congsac;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CongSacRepository extends JpaRepository<CongSac, String> {

    interface CauHinhProjection { String getMaCauHinh(); }
    @Query("select c.maCauHinh as maCauHinh from CongSac c where c.maCong = :maCong")
    Optional<CauHinhProjection> findCauHinhByMaCong(@Param("maCong") String maCong);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CongSac> findForUpdateByMaCauHinhOrderByMaCong(String maCauHinh);

    /**
     * Khóa toàn bộ cổng cùng loại theo thứ tự MaCong. Phải là lần ĐẦU nạp CongSac trong giao dịch để
     * đối tượng được đọc mới sau khi có khóa (xem bài học đọc bản cũ ở đăng nhập).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CongSac> findForUpdateByLoaiCongOrderByMaCong(String loaiCong);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CongSac> findForUpdateByMaCong(String maCong);

    /** Chỉ lấy mã (không nạp entity) để không làm bẩn bộ nhớ đệm trước khi khóa. */
    @Query("select c.maCong from CongSac c where c.loaiCong = :loaiCong and c.trangThai not in :khongKhaDung "
            + "order by c.maCong")
    List<String> timMaCongKhaDung(@Param("loaiCong") String loaiCong,
                                  @Param("khongKhaDung") Collection<String> khongKhaDung);

    @Query("select c.maCong from CongSac c where c.trangThai in :trangThai order by c.maCong")
    List<String> timMaCongTheoTrangThai(@Param("trangThai") Collection<String> trangThai);

    /** Tổng công suất đang cấp của một nhóm cổng; null nếu nhóm chưa có cổng nào. */
    @Query("select sum(c.congSuatDuocCap) from CongSac c where c.maCauHinh = :maCauHinh")
    BigDecimal tongCongSuatDuocCap(@Param("maCauHinh") String maCauHinh);

    List<CongSac> findByMaCauHinhOrderByMaCong(String maCauHinh);

    @Query("select c.maCong from CongSac c where c.heartbeatCuoi is not null and c.heartbeatCuoi < :nguong "
            + "and c.trangThai not in :boQua order by c.maCong")
    List<String> timMaCongMatKetNoi(@Param("nguong") java.time.LocalDateTime nguong,
                                    @Param("boQua") Collection<String> boQua);

    @Query("select c.maCong from CongSac c where c.trangThai = :trangThai and c.capNhatLuc < :nguong")
    List<String> timMaCongMatKetNoiQuaHan(@Param("trangThai") String trangThai,
                                          @Param("nguong") java.time.LocalDateTime nguong);
}
