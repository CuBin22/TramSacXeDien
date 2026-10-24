package com.tramsac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra 22 bảng nghiệp vụ và bảng outbox công suất do Flyway tạo. */
@Transactional
class SchemaIT extends AbstractIntegrationTest {

    @Test
    void tableCount_is23IncludingPowerOutbox() {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(n).isEqualTo(23);
    }

    @Test
    void uniqueConstraints_exist() {
        assertUnique("nguoidung", "email");
        assertUnique("nguoidung", "sodienthoai");
        assertUnique("xe", "bienso");
        assertUnique("lenhdieukhien", "khoaidempotency");
        assertUnique("donthanhtoan", "khoaidempotency");
        assertUnique("hoantien", "khoaidempotency");
        assertUnique("phisac", "maphien");
        assertUnique("phiensac", "madatphien");
        assertUnique("hoantien", "magiaodich");
    }

    @Test
    void callbackLog_hasPartialUniqueIndexForFirstProcessing() {
        // Chỉ một callback "Mới" cho mỗi cặp (MaGiaoDich, SuKien); các callback trùng vẫn được ghi (KetQuaXuLy = 'Trùng').
        List<String> defs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'callbacklog'",
                String.class);
        assertThat(defs).anySatisfy(d -> assertThat(d)
                .contains("UNIQUE INDEX")
                .contains("(magiaodich, sukien)")
                .contains("WHERE")
                .contains("Mới"));
    }

    @Test
    void congSac_validRow_isAccepted() {
        int rows = jdbc.update("INSERT INTO CongSac (MaCong, LoaiCong, CongSuatDinhMuc, TrangThai) "
                + "VALUES ('C-OK', 'AC', 7.40, 'Rảnh')");
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void congSac_invalidLoaiCong_isRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO CongSac (MaCong, LoaiCong, CongSuatDinhMuc, TrangThai) "
                + "VALUES ('C-BAD', 'XX', 7.40, 'Rảnh')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nguoiDung_withEmailOnly_isAccepted() {
        int rows = jdbc.update("INSERT INTO NguoiDung (MaND, HoTen, Email, MatKhauHash, DoiTuong, VaiTro) "
                + "VALUES ('ND-OK', 'A', 'a@x.com', 'hash', 'Sinh viên', 'Người dùng')");
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void nguoiDung_withoutEmailAndPhone_isRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NguoiDung (MaND, HoTen, MatKhauHash, DoiTuong, VaiTro) "
                + "VALUES ('ND-BAD', 'A', 'hash', 'Sinh viên', 'Người dùng')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertUnique(String table, String... columns) {
        List<String> found = jdbc.queryForList("""
                SELECT string_agg(k.column_name, ',' ORDER BY k.ordinal_position)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage k
                  ON k.constraint_name = tc.constraint_name
                 AND k.constraint_schema = tc.constraint_schema
                 AND k.table_name = tc.table_name
                WHERE tc.constraint_type = 'UNIQUE'
                  AND tc.table_schema = 'public'
                  AND tc.table_name = ?
                GROUP BY tc.constraint_name
                """, String.class, table);
        assertThat(found).as("UNIQUE tren bang %s", table).contains(String.join(",", columns));
    }
}
