# Giai đoạn 1 – Nền tảng (UC01–UC04) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng khung dự án Spring Boot chạy bằng Docker Compose, schema PostgreSQL đủ 22 bảng theo ERD, và hoàn thiện đăng ký, đăng nhập, hồ sơ, phân quyền (UC01–UC04) kèm nhật ký kiểm toán.

**Architecture:** Modular monolith phân lớp (Controller → Service → Repository → Entity), package theo phân hệ. Schema tạo bằng Flyway, xác thực JWT bằng Spring Security, test tích hợp bằng Testcontainers (PostgreSQL thật).

**Tech Stack:** Java 21, Maven, Spring Boot 3.x (bản ổn định mới nhất 3.x khi tạo dự án), Spring Data JPA, Flyway, Spring Security + jjwt 0.12.x, PostgreSQL 16, Testcontainers, JUnit 5, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-13-tram-sac-xe-dien-design.md`. Schema chi tiết từng cột lấy từ tài liệu ERD `Chuong4_MoTa_ERD_TramSacXeDienQuyMoKhuonVien.docx`; đặc tả use case từ `Chuong2_Actor_UseCase_DacTa_UseCase_TramSac.docx` (UC01–UC04); biểu mẫu BM1–BM4 và quy định QĐ1–QĐ4 từ `Chuong1_Bieu_mau_va_quy_dinh_theo_chuc_nang.docx`.

## Global Constraints

- Gói gốc `com.tramsac`; module Maven `app` nằm trong dự án cha `tramsac` (sau này thêm `device-sim`, `payment-sim`).
- Tên bảng và cột giữ **đúng như ERD** (ví dụ `NguoiDung`, `MaND`). DDL viết không có dấu nháy kép (PostgreSQL tự đổi thành chữ thường); entity dùng `@Table(name = "NguoiDung")`; cấu hình `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl`.
- Khóa chính là mã chuỗi `varchar`; sinh bằng `MaGenerator` (tiền tố + `-` + 10 ký tự hex ngẫu nhiên).
- Vai trò (đúng một vai trò mỗi tài khoản, mặc định `Người dùng`): `Quản trị`, `Kỹ thuật viên`, `Nhân viên trực`, `Người dùng`.
- Đối tượng người dùng: `Sinh viên`, `Cán bộ`, `Khách vãng lai`, `Xe công vụ`.
- Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số; lưu BCrypt, không bao giờ lưu hoặc ghi log mật khẩu gốc (QĐ1).
- Email hoặc số điện thoại không được trùng; ít nhất một trong hai phải có (BM1).
- Mỗi tài khoản tối đa 3 xe; biển số xe duy nhất toàn hệ thống; `LoaiCongSuDung` ∈ {`AC`, `DC`} (QĐ3).
- Đăng nhập sai liên tiếp 5 lần thì tạm khóa 15 phút; phiên đăng nhập hết hạn sau 30 phút không hoạt động (QĐ2).
- Chỉ Quản trị được đổi vai trò; không tự nâng quyền cho chính mình; không đổi vai trò của Quản trị viên cuối cùng (QĐ4, quy tắc 15 của spec).
- Quyền được kiểm tra ở mọi API bằng Spring Security phía server, không chỉ ở giao diện (QĐ4).
- Mọi thao tác quan trọng ghi `NhatKyHeThong` (NFR08).
- Cần Docker đang chạy để chạy test (Testcontainers) và Docker Compose.

## Review Focus

1. **Email viết hoa/thường và khoảng trắng:** `A@x.com ` và `a@x.com` là cùng một email; đăng ký trùng phải bị từ chối. Test ở Task 4.
2. **Hai yêu cầu đăng ký đồng thời cùng email:** một cái thành công, cái còn lại nhận 409, không phải lỗi 500. Test ở Task 4.
3. **Đăng nhập đúng mật khẩu khi đang bị khóa:** vẫn bị từ chối; hết 15 phút thì vào được và bộ đếm về 0. Test ở Task 5.
4. **Đổi vai trò có hiệu lực ngay:** token cũ vẫn hợp lệ nhưng quyền phải lấy từ DB ở mỗi yêu cầu; token sai chữ ký hoặc hết hạn bị 401. Test ở Task 5.
5. **Hai Quản trị viên cùng lúc tự hạ quyền:** không được để hệ thống còn 0 Quản trị viên. Test ở Task 7.

---

### Task 1: Khung dự án và Docker Compose

**Files:**
- Create: `pom.xml`, `app/pom.xml`, `app/Dockerfile`, `docker-compose.yml`, `.env.example`, `.gitignore`
- Create: `app/src/main/java/com/tramsac/TramSacApplication.java`
- Create: `app/src/main/resources/application.yml`
- Test: `app/src/test/java/com/tramsac/AbstractIntegrationTest.java`, `app/src/test/java/com/tramsac/HealthIT.java`

**Interfaces:**
- Produces: `AbstractIntegrationTest` (lớp cơ sở `@SpringBootTest` + `@AutoConfigureMockMvc` + một `PostgreSQLContainer<?>("postgres:16")` dùng chung qua `@DynamicPropertySource`; trường `protected MockMvc mvc`, `protected JdbcTemplate jdbc`); mọi test tích hợp sau kế thừa lớp này. Endpoint `GET /actuator/health` công khai.

- [ ] **Step 1: Viết test lỗi** `HealthIT.healthEndpoint_returnsUp`: `mvc.perform(get("/actuator/health"))` mong đợi status 200 và `$.status` = `UP`.
- [ ] **Step 2: Chạy** `mvn -pl app test -Dtest=HealthIT` → FAIL (chưa có ứng dụng).
- [ ] **Step 3: Tạo khung:** `git init`; POM cha (Java 21, quản lý phiên bản Spring Boot) và `app/pom.xml` (starter web, data-jpa, validation, security, actuator, flyway-core + flyway-database-postgresql, postgresql, jjwt, test, testcontainers postgresql + junit-jupiter). `application.yml`: datasource đọc `DB_URL`, `DB_USER`, `DB_PASSWORD`; `spring.jpa.hibernate.ddl-auto=validate`; naming strategy theo Global Constraints; `management.endpoints.web.exposure.include=health`. `Dockerfile` nhiều tầng (build Maven → chạy JRE 21). `docker-compose.yml`: dịch vụ `postgres` (postgres:16, healthcheck `pg_isready`) và `app` (phụ thuộc `postgres` khỏe, biến môi trường từ `.env`, cổng 8080). `.env.example` liệt kê `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.
- [ ] **Step 4: Chạy** lại `HealthIT` → PASS; chạy `docker compose up -d --build` và `curl localhost:8080/actuator/health` trả `{"status":"UP"}`; `docker compose down`.
- [ ] **Step 5: Commit** `git add . && git commit -m "chore: khung du an Spring Boot va Docker Compose"`

---

### Task 2: Schema 22 bảng bằng Flyway

**Files:**
- Create: `app/src/main/resources/db/migration/V1__schema.sql`
- Test: `app/src/test/java/com/tramsac/SchemaIT.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest` (Task 1).
- Produces: 22 bảng theo ERD, đúng tên bảng, tên cột, kiểu dữ liệu và khóa ngoại của tài liệu ERD (mục 2.1–2.7).

- [ ] **Step 1: Viết test lỗi** trong `SchemaIT`:
  - `tableCount_is22`: đếm bảng trong schema `public` trừ `flyway_schema_history` bằng 22.
  - `uniqueConstraints_exist`: helper `assertUnique(table, columns...)` truy vấn `pg_index`; kiểm tra `nguoidung(email)`, `nguoidung(sodienthoai)`, `xe(bienso)`, `lenhdieukhien(khoaidempotency)`, `donthanhtoan(khoaidempotency)`, `hoantien(khoaidempotency)`, `phisac(maphien)`, `phiensac(madatphien)`, `hoantien(magiaodich)`, `callbacklog(magiaodich, sukien)`.
  - `checkConstraints_rejectInvalidValues`: chèn `CongSac` với `LoaiCong='XX'` và `NguoiDung` không có cả email lẫn số điện thoại đều ném `DataIntegrityViolationException`.
- [ ] **Step 2: Chạy** `mvn -pl app test -Dtest=SchemaIT` → FAIL.
- [ ] **Step 3: Viết `V1__schema.sql`:** 22 bảng theo ERD, mỗi khóa ngoại khai báo `REFERENCES`. Thêm các ràng buộc ngoài ERD: UNIQUE như danh sách test ở trên (cột nullable như `Email`, `SoDienThoai` vẫn cho nhiều giá trị NULL); `CHECK (Email IS NOT NULL OR SoDienThoai IS NOT NULL)` trên `NguoiDung`; `CHECK` giá trị hợp lệ cho `NguoiDung.VaiTro`, `NguoiDung.DoiTuong`, `Xe.LoaiCongSuDung`, `CongSac.LoaiCong`, `DatPhien.LoaiCong`; `CHECK (SoLanDoiLich BETWEEN 0 AND 2)` trên `DatPhien`. Kiểu `timestamp` giữ nguyên như ERD.
- [ ] **Step 4: Chạy** `SchemaIT` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat: schema 22 bang theo ERD va rang buoc idempotency"`

---

### Task 3: Nền chung (lỗi, mã, đồng hồ, nhật ký)

**Files:**
- Create: `app/src/main/java/com/tramsac/common/ApiException.java`, `ApiError.java`, `GlobalExceptionHandler.java`, `MaGenerator.java`, `ClockConfig.java`
- Create: `app/src/main/java/com/tramsac/common/audit/NhatKyHeThong.java`, `NhatKyRepository.java`, `AuditService.java`
- Test: `app/src/test/java/com/tramsac/common/AuditServiceIT.java`, `MaGeneratorTest.java`

**Interfaces:**
- Produces:
  - `class ApiException extends RuntimeException { ApiException(HttpStatus status, String ma, String thongBao); HttpStatus status(); String ma(); }`
  - `record ApiError(String ma, String thongBao, Map<String,String> truong)`; `GlobalExceptionHandler` trả `ApiError` cho `ApiException` (đúng status), `MethodArgumentNotValidException` (400, `ma="DU_LIEU_KHONG_HOP_LE"`, `truong` = tên trường → thông báo) và `DataIntegrityViolationException` (409, `ma="TRUNG_DU_LIEU"`).
  - `@Component class MaGenerator { String next(String tienTo); }` → `"ND-" + 10 hex`.
  - `@Bean Clock clock()` (UTC); mọi code cần thời gian inject `Clock`, không gọi `Instant.now()` trực tiếp.
  - `@Service class AuditService { void ghi(String maND, String hanhDong, String noiDung); }` — `maND` rỗng (`null`) với thao tác tự động; ghi một dòng `NhatKyHeThong` với `ThoiDiem` lấy từ `Clock`.

- [ ] **Step 1: Viết test lỗi:** `MaGeneratorTest.next_hasPrefixAndTenHex` (khớp regex `ND-[0-9a-f]{10}`; hai lần gọi khác nhau). `AuditServiceIT.ghi_persistsRowWithTimestamp`: gọi `ghi(null, "TEST", "noi dung")` rồi truy vấn `NhatKyHeThong` có đúng 1 dòng, `MaND` NULL, `HanhDong='TEST'`.
- [ ] **Step 2: Chạy** hai test → FAIL.
- [ ] **Step 3: Cài đặt** các lớp theo Interfaces. `AuditService` chạy trong giao dịch của người gọi (`Propagation.REQUIRED`) để nhật ký và thao tác cùng thành công hoặc cùng hoàn tác.
- [ ] **Step 4: Chạy** hai test → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat: nen chung gom loi API, sinh ma, dong ho va nhat ky he thong"`

---

### Task 4: UC01 – Đăng ký tài khoản

**Files:**
- Create: `app/src/main/java/com/tramsac/nguoidung/NguoiDung.java`, `Xe.java`, `VaiTro.java`, `NguoiDungRepository.java`, `XeRepository.java`
- Create: `app/src/main/java/com/tramsac/nguoidung/dto/DangKyRequest.java`, `app/src/main/java/com/tramsac/nguoidung/AuthController.java`, `DangKyService.java`
- Create: `app/src/main/java/com/tramsac/common/security/SecurityConfig.java` (bản tối thiểu: bean `PasswordEncoder` BCrypt, `/api/auth/**` và `/actuator/health` công khai)
- Test: `app/src/test/java/com/tramsac/nguoidung/DangKyIT.java`

**Interfaces:**
- Consumes: `ApiException`, `MaGenerator`, `AuditService`, `Clock` (Task 3); bảng `NguoiDung`, `Xe` (Task 2).
- Produces:
  - `enum VaiTro { QUAN_TRI("Quản trị"), KY_THUAT_VIEN("Kỹ thuật viên"), NHAN_VIEN_TRUC("Nhân viên trực"), NGUOI_DUNG("Người dùng"); String giaTri(); static VaiTro tuGiaTri(String) }`
  - `record DangKyRequest(String hoTen, String email, String soDienThoai, String doiTuong, String maDinhDanh, String bienSo, String loaiXe, String loaiCongSuDung, String matKhau, String xacNhanMatKhau)` với các ràng buộc `@NotBlank`/`@Pattern` theo Global Constraints. **Ghi chú:** BM1 chỉ có biển số, nhưng UC05 cần loại cổng của xe, nên form đăng ký bổ sung `loaiXe` và `loaiCongSuDung` (bắt buộc).
  - `POST /api/auth/dang-ky` → 201 `{"maND": "..."}`; `NguoiDung` mới có `VaiTro="Người dùng"`, `MatKhauHash` BCrypt, `SoLanSaiLienTiep=0`, kèm một `Xe`; ghi nhật ký `DANG_KY`.
  - `NguoiDungRepository`: `Optional<NguoiDung> findByEmail(String)`, `findBySoDienThoai(String)`.
  - Email được chuẩn hóa `trim().toLowerCase()` trước khi lưu và kiểm tra trùng; số điện thoại chuẩn hóa bỏ khoảng trắng.

- [ ] **Step 1: Viết test lỗi** trong `DangKyIT`:
  - `dangKy_valid_returns201AndStoresHashedPassword`: sau khi gọi, `MatKhauHash` khác `"Abcd1234"` và bắt đầu bằng `$2`; `VaiTro='Người dùng'`; có đúng 1 xe; có 1 dòng `NhatKyHeThong` `DANG_KY`.
  - `dangKy_weakPassword_returns400` với `"abcdefgh"` (không có số) và `"abc123"` (dưới 8 ký tự); `dangKy_passwordMismatch_returns400`.
  - `dangKy_noEmailNoPhone_returns400`.
  - `dangKy_duplicateEmailIgnoringCaseAndSpaces_returns409`: đăng ký `a@x.com`, rồi `" A@X.com "` → 409 `TRUNG_DU_LIEU`.
  - `dangKy_duplicatePlate_returns409` (khác email, cùng biển số).
  - `dangKy_concurrentSameEmail_oneCreatedOtherConflict`: hai luồng cùng lúc → đúng một 201 và một 409, không có 500.
- [ ] **Step 2: Chạy** `DangKyIT` → FAIL.
- [ ] **Step 3: Cài đặt** `DangKyService.dangKy(DangKyRequest): String` trong một giao dịch: chuẩn hóa → kiểm tra trùng email/số điện thoại/biển số (trả `ApiException` 409) → lưu `NguoiDung` và `Xe` → `AuditService.ghi`. Trùng đồng thời dựa vào UNIQUE của DB: `GlobalExceptionHandler` đã đổi `DataIntegrityViolationException` thành 409. `BCryptPasswordEncoder` khai báo là bean trong `SecurityConfig` tạm thời (Task 5 hoàn thiện cấu hình); tạm cho `/api/auth/**` công khai.
- [ ] **Step 4: Chạy** `DangKyIT` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat(UC01): dang ky tai khoan"`

---

### Task 5: UC02 – Đăng nhập, JWT và phân quyền theo vai trò

**Files:**
- Create: `app/src/main/java/com/tramsac/nguoidung/DangNhapService.java`, `dto/DangNhapRequest.java`, `dto/DangNhapResponse.java`
- Create: `app/src/main/java/com/tramsac/common/security/JwtService.java`, `JwtAuthFilter.java`, `NguoiDungHienTai.java`
- Modify: `app/src/main/java/com/tramsac/common/security/SecurityConfig.java`, `app/src/main/java/com/tramsac/nguoidung/AuthController.java`
- Create (test support): `app/src/test/java/com/tramsac/MutableClock.java` (lớp `Clock` cho phép `advance(Duration)`; đăng ký làm bean `@Primary` trong một `@TestConfiguration` mà `AbstractIntegrationTest` import)
- Test: `app/src/test/java/com/tramsac/nguoidung/DangNhapIT.java`, `app/src/test/java/com/tramsac/common/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: `NguoiDungRepository`, `VaiTro`, `Clock`, `AuditService`, `ApiException`.
- Produces:
  - `POST /api/auth/dang-nhap` body `DangNhapRequest(String dinhDanh, String matKhau)` (`dinhDanh` là email hoặc số điện thoại) → 200 `DangNhapResponse(String token, String maND, String vaiTro)`. Sai thông tin → 401 `ma="SAI_THONG_TIN"` và tăng `SoLanSaiLienTiep`; sai lần thứ 5 thì đặt `KhoaDenLuc = now + 15 phút`. Đang khóa → 423 `ma="TAM_KHOA"` kèm `conLaiGiay`, **kể cả khi mật khẩu đúng**. Hết khóa mà đúng mật khẩu → vào được, `SoLanSaiLienTiep=0`, `KhoaDenLuc=NULL`.
  - `class JwtService { String taoToken(String maND); Optional<String> docMaND(String token); }` — token hết hạn sau 30 phút, ký HS256 bằng `JWT_SECRET`; token sai chữ ký hoặc hết hạn → `Optional.empty()`.
  - `JwtAuthFilter`: đọc `Authorization: Bearer`, nạp `NguoiDung` từ DB **ở mọi yêu cầu** (vai trò lấy từ DB, không lấy từ token), cấp quyền `ROLE_QUAN_TRI` / `ROLE_KY_THUAT_VIEN` / `ROLE_NHAN_VIEN_TRUC` / `ROLE_NGUOI_DUNG`; sau mỗi yêu cầu hợp lệ trả header `X-New-Token` (token mới hạn 30 phút) để phiên trượt theo hoạt động.
  - `SecurityConfig` (mở rộng bản của Task 4): `@EnableMethodSecurity`; công khai `/api/auth/**` và `/actuator/health`; còn lại cần xác thực; lỗi quyền trả 403 và chưa xác thực trả 401 dạng `ApiError`.
  - `NguoiDungHienTai` (lớp tiện ích, phương thức tĩnh) `static String layMaND()` và `static VaiTro layVaiTro()` đọc từ `SecurityContext`.

- [ ] **Step 1: Viết test lỗi:**
  - `JwtServiceTest`: `taoToken` rồi `docMaND` trả đúng mã; token bị sửa một ký tự → rỗng; token tạo bằng `Clock` lùi 31 phút → rỗng.
  - `DangNhapIT.dangNhap_correct_returnsToken`; `dangNhap_byPhone_works`.
  - `dangNhap_wrongPasswordFiveTimes_locks15Minutes`: 4 lần đầu 401, lần thứ 5 vẫn 401 nhưng `KhoaDenLuc` được đặt; lần thứ 6 trả 423.
  - `dangNhap_lockedWithCorrectPassword_stillRejected`; sau khi `Clock` (dùng `MutableClock` trong test) tiến 15 phút 1 giây, đăng nhập đúng → 200 và `SoLanSaiLienTiep=0`.
  - `protectedEndpoint_withoutToken_returns401`, `..._withTamperedToken_returns401`.
  - `roleChange_takesEffectWithoutRelogin`: đăng nhập với vai trò `Người dùng`, đổi `VaiTro` trong DB thành `Quản trị` bằng JDBC, gọi một endpoint chỉ dành cho Quản trị (endpoint thử trong test) bằng token cũ → 200.
  - `response_includesRefreshedToken`.
- [ ] **Step 2: Chạy** các test trên → FAIL.
- [ ] **Step 3: Cài đặt** theo Interfaces. Thông báo lỗi đăng nhập không tiết lộ email có tồn tại hay không. Ghi nhật ký `DANG_NHAP_THANH_CONG`, `DANG_NHAP_SAI`, `TAM_KHOA`.
- [ ] **Step 4: Chạy** các test trên → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat(UC02): dang nhap, JWT, khoa tam thoi va phan quyen phia server"`

---

### Task 6: UC03 – Quản lý hồ sơ và xe

**Files:**
- Create: `app/src/main/java/com/tramsac/nguoidung/HoSoController.java`, `HoSoService.java`, `dto/HoSoResponse.java`, `dto/CapNhatHoSoRequest.java`, `dto/XeRequest.java`
- Test: `app/src/test/java/com/tramsac/nguoidung/HoSoIT.java`

**Interfaces:**
- Consumes: `NguoiDungHienTai`, `NguoiDungRepository`, `XeRepository`, `AuditService`, `MaGenerator`.
- Produces:
  - `GET /api/ho-so` → `HoSoResponse(maND, hoTen, email, soDienThoai, doiTuong, maDinhDanh, vaiTro, List<XeDto>)` của chính người gọi.
  - `PUT /api/ho-so` body `CapNhatHoSoRequest(String hoTen, String email, String soDienThoai, String doiTuong, String vaiTro)`; `doiTuong` và `vaiTro` chỉ để phát hiện cố đổi: nếu khác `null` thì 403 `ma="CHI_QUAN_TRI_DOI_DOI_TUONG"` (QĐ3).
  - `POST /api/ho-so/xe`, `PUT /api/ho-so/xe/{maXe}`, `DELETE /api/ho-so/xe/{maXe}` với `XeRequest(bienSo, loaiXe, loaiCongSuDung)`; quá 3 xe → 409 `VUOT_SO_XE`; biển số thuộc tài khoản khác → 409 `TRUNG_BIEN_SO`; sửa hoặc xóa xe của người khác → 404.
  - Chuẩn hóa biển số: `trim().toUpperCase()` và bỏ khoảng trắng trong biển số.

- [ ] **Step 1: Viết test lỗi:** `HoSoIT`: `getHoSo_returnsOnlyOwnData`; `updateProfile_changesNameAndPhone`; `updateProfile_duplicateEmail_returns409`; `updateProfile_tryChangeDoiTuong_returns403`; `addVehicle_fourth_returns409`; `addVehicle_plateOfOtherAccount_returns409`; `updateVehicle_ofAnotherUser_returns404`; `deleteVehicle_removesRow`; mỗi thay đổi hợp lệ ghi một dòng nhật ký.
- [ ] **Step 2: Chạy** `HoSoIT` → FAIL.
- [ ] **Step 3: Cài đặt** `HoSoService` theo Interfaces, chỉ thao tác trên `NguoiDungHienTai.layMaND()`; kiểm tra số xe và trùng biển số trong cùng giao dịch.
- [ ] **Step 4: Chạy** `HoSoIT` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat(UC03): quan ly ho so nguoi dung va danh sach xe"`

---

### Task 7: UC04 – Phân quyền theo vai trò

**Files:**
- Create: `app/src/main/java/com/tramsac/nguoidung/LichSuPhanQuyen.java`, `LichSuPhanQuyenRepository.java`, `PhanQuyenController.java`, `PhanQuyenService.java`, `dto/DoiVaiTroRequest.java`, `dto/LichSuPhanQuyenResponse.java`
- Test: `app/src/test/java/com/tramsac/nguoidung/PhanQuyenIT.java`

**Interfaces:**
- Consumes: `NguoiDungHienTai`, `AuditService`, `VaiTro`, `NguoiDungRepository` (thêm hai phương thức khóa bi quan `@Lock(PESSIMISTIC_WRITE)`: `Optional<NguoiDung> findForUpdateByMaND(String)` và `List<NguoiDung> findForUpdateByVaiTroOrderByMaND(String vaiTro)`).
- Produces:
  - `PUT /api/nguoi-dung/{maND}/vai-tro` body `DoiVaiTroRequest(String vaiTroMoi)`, chỉ `ROLE_QUAN_TRI`. Trả 200 `{"vaiTroCu","vaiTroMoi"}`; ghi một dòng `LichSuPhanQuyen(MaLSPQ, MaND, ThoiDiem, VaiTroCu, VaiTroMoi, NguoiThucHien)` và nhật ký `PHAN_QUYEN`.
  - `GET /api/nguoi-dung/{maND}/lich-su-phan-quyen` (chỉ Quản trị).
  - Lỗi: tự đổi vai trò của chính mình → 403 `KHONG_TU_NANG_QUYEN`; vai trò mới trùng vai trò hiện tại → 200 không ghi lịch sử (`{"thayDoi": false}`); hạ quyền khiến hệ thống còn 0 Quản trị viên → 409 `QUAN_TRI_CUOI_CUNG` (vì cấm tự đổi nên chỉ xảy ra khi hai Quản trị viên hạ nhau cùng lúc); tài khoản không tồn tại → 404; vai trò mới không hợp lệ → 400.
  - Việc "thu hồi" vai trò nghĩa là chuyển về `Người dùng` (giả định ở UC04, Open Issues).

- [ ] **Step 1: Viết test lỗi** `PhanQuyenIT`: `adminChangesUserRole_updatesAndWritesHistory`; `nonAdmin_returns403` (cho từng vai trò còn lại); `selfChange_returns403`; `sameRole_returnsNoChangeAndNoHistory`; `invalidRole_returns400`; `unknownAccount_returns404`; `concurrentDemoteEachOther_leavesAtLeastOneAdmin`: Quản trị A và B cùng lúc (hai luồng, dùng `CountDownLatch` để xuất phát đồng thời) hạ nhau thành `Người dùng`; kết quả cuối `count(VaiTro='Quản trị') >= 1`, đúng một yêu cầu nhận 200 và yêu cầu còn lại nhận 409 `QUAN_TRI_CUOI_CUNG`, không có deadlock hay lỗi 500; lặp lại 20 lần.
- [ ] **Step 2: Chạy** `PhanQuyenIT` → FAIL.
- [ ] **Step 3: Cài đặt** `PhanQuyenService.doiVaiTro(String maND, VaiTro vaiTroMoi): DoiVaiTroResult` trong một giao dịch: đọc đích không khóa; nếu đích đang là Quản trị và vai trò mới khác Quản trị thì khóa **toàn bộ** các dòng Quản trị bằng `findForUpdateByVaiTroOrderByMaND` (thứ tự `MaND` cố định để tránh deadlock), đọc lại đích, và nếu số Quản trị còn lại sau khi hạ bằng 0 thì ném `QUAN_TRI_CUOI_CUNG`; nếu đích không phải Quản trị thì chỉ khóa dòng đích bằng `findForUpdateByMaND`.
- [ ] **Step 4: Chạy** `PhanQuyenIT` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat(UC04): phan quyen theo vai tro, lich su va bao ve quan tri cuoi cung"`

---

### Task 8: Dữ liệu mẫu và chạy thử toàn bộ bằng Docker Compose

**Files:**
- Create: `app/src/main/java/com/tramsac/common/DevDataSeeder.java`, `README.md`
- Modify: `docker-compose.yml`, `.env.example`
- Test: `app/src/test/java/com/tramsac/DevDataSeederIT.java`

**Interfaces:**
- Consumes: `NguoiDungRepository`, `XeRepository`, `MaGenerator`, `PasswordEncoder`.
- Produces: `@Profile("dev") class DevDataSeeder implements ApplicationRunner` nạp 4 tài khoản (mật khẩu `Demo1234`): `admin@tramsac.local` (Quản trị), `ktv@tramsac.local` (Kỹ thuật viên), `truc@tramsac.local` (Nhân viên trực), `user@tramsac.local` (Người dùng, kèm 1 xe `59A-123.45`, loại cổng `AC`); chạy lặp lại nhiều lần không tạo trùng.

- [ ] **Step 1: Viết test lỗi** `DevDataSeederIT` (`@ActiveProfiles("dev")`): sau khi khởi động có đúng 4 tài khoản và 4 vai trò khác nhau; chạy `run` lần hai vẫn 4 tài khoản; mỗi tài khoản đăng nhập được bằng `Demo1234`.
- [ ] **Step 2: Chạy** `DevDataSeederIT` → FAIL.
- [ ] **Step 3: Cài đặt** `DevDataSeeder`; thêm `SPRING_PROFILES_ACTIVE=dev` cho dịch vụ `app` trong `docker-compose.yml`; `README.md` ghi cách chạy (`cp .env.example .env`, `docker compose up --build`), tài khoản mẫu và cách chạy test (`mvn test`).
- [ ] **Step 4: Chạy** `mvn test` toàn bộ → tất cả PASS; `docker compose up -d --build`, đăng nhập bằng `curl` với `admin@tramsac.local` nhận token, dùng token gọi `GET /api/ho-so` trả hồ sơ Quản trị; `docker compose down`.
- [ ] **Step 5: Commit** `git commit -am "feat: du lieu mau dev va huong dan chay"`

---

## Không thuộc kế hoạch này

HTTPS (profile `demo` ở giai đoạn 6), giao diện React (giai đoạn 5), xác minh email/số điện thoại bằng OTP (UC01 Open Issues, chưa quy định), màn hình hoặc API riêng để Quản trị đổi `DoiTuong` của người dùng (UC03 Open Issues; có thể thêm khi làm giao diện), OpenTelemetry (giai đoạn 6), MQTT và mọi use case từ UC05 trở đi.
