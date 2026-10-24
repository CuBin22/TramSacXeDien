package com.tramsac.nguoidung;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NguoiDungRepository extends JpaRepository<NguoiDung, String> {

    boolean existsByEmail(String email);

    boolean existsBySoDienThoai(String soDienThoai);

    Optional<NguoiDung> findByEmail(String email);

    Optional<NguoiDung> findBySoDienThoai(String soDienThoai);

    /**
     * Chỉ lấy mã (không nạp entity vào bộ nhớ đệm cấp 1). Nếu nạp entity trước rồi mới khóa dòng,
     * Hibernate sẽ trả về bản cũ đã nạp thay vì đọc lại dòng sau khi có khóa.
     */
    @Query("select n.maND from NguoiDung n where n.email = :email")
    Optional<String> timMaNDTheoEmail(@Param("email") String email);

    @Query("select n.maND from NguoiDung n where n.soDienThoai = :soDienThoai")
    Optional<String> timMaNDTheoSoDienThoai(@Param("soDienThoai") String soDienThoai);

    /** Chỉ lấy vai trò (không nạp entity), dùng trước khi khóa dòng để tránh đọc bản cũ. */
    @Query("select n.vaiTro from NguoiDung n where n.maND = :maND")
    Optional<String> timVaiTroTheoMaND(@Param("maND") String maND);

    /** Khóa toàn bộ tài khoản có vai trò này, luôn theo thứ tự MaND để hai giao dịch không bị deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NguoiDung> findForUpdateByVaiTroOrderByMaND(String vaiTro);

    /** Khóa dòng (SELECT ... FOR UPDATE) để các lần đăng nhập đồng thời được xử lý lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NguoiDung> findForUpdateByMaND(String maND);
}
