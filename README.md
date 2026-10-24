# Hệ thống quản lý trạm sạc xe điện quy mô khuôn viên

Đồ án phân tích – thiết kế hướng đối tượng. Backend Java 21 + Spring Boot, PostgreSQL 16, chạy bằng Docker Compose.
Thiết kế: `docs/superpowers/specs/2026-10-13-tram-sac-xe-dien-design.md`. Kế hoạch giai đoạn 1: `docs/superpowers/plans/`.

**Trạng thái:** giai đoạn 1–4 đã có kết quả kiểm thử đạt. Giai đoạn 5 có backend sự cố, API báo cáo và giao diện đăng nhập/tổng quan/sự cố/báo cáo; chưa đủ các màn hình trong thiết kế. Giai đoạn 6 đã bổ sung kiểm thử, tracing OpenTelemetry, Jaeger và bảng truy vết; suite Java, React và E2E với simulator thật đã đạt. Chi tiết và giới hạn: [Giai đoạn 6](docs/GIAI_DOAN_6.md). Báo cáo đồ án còn cần hoàn thiện.

## Yêu cầu

- Docker Desktop (đang chạy), JDK 21, Maven 3.9.

## Chạy hệ thống

```powershell
copy .env.example .env      # rồi sửa DB_PASSWORD và JWT_SECRET (ít nhất 32 ký tự) trong .env
docker compose --profile dev up -d --build  # bật cả device-sim để demo cổng sạc
curl.exe http://localhost:8080/actuator/health      # {"status":"UP"}
start http://localhost:3000                         # giao diện React
docker compose down                                  # tắt (thêm -v để xóa luôn dữ liệu)
```

Xem log: `docker compose logs app`. Xem CSDL bằng DBeaver/pgAdmin: host `localhost`, cổng `5433`, tên CSDL, người dùng và
mật khẩu theo `.env`.

Nếu trước đó chạy `docker compose up` không có `--profile dev`, thiết bị mô phỏng chưa khởi động. Bật riêng bằng
`docker compose --profile dev up -d --build device-sim` và kiểm tra `docker compose --profile dev ps`.
Sau khi mô phỏng kết nối lại, cổng “Mất kết nối” chỉ tự cập nhật khi nhận heartbeat (mỗi 30 giây) và phản hồi trạng thái thực;
việc đánh dấu sự cố đã xử lý không tự đổi trạng thái thiết bị. Với cổng “Bảo trì”, kỹ thuật viên chọn “Mở lại cổng”;
backend chỉ cho mở khi thiết bị trả lời an toàn. Trong demo có thể dùng:

```powershell
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/reconnect' # cổng mất kết nối
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-02/safe'      # cổng bảo trì; sau đó bấm Mở lại cổng bằng tài khoản KTV
```

Nếu API mô phỏng không trả lời, xem `docker compose --profile dev logs device-sim mosquitto app`. Không đổi trạng thái
cổng trực tiếp trong cơ sở dữ liệu, vì trạng thái thiết bị và kiểm tra an toàn sẽ không còn khớp.

## Tài khoản và cổng sạc mẫu (profile `dev`, mật khẩu `Demo1234`)

| Email | Vai trò |
|---|---|
| admin@tramsac.local | Quản trị |
| ktv@tramsac.local | Kỹ thuật viên |
| truc@tramsac.local | Nhân viên trực |
| user@tramsac.local | Người dùng (có xe 59A-123.45, cổng AC) |

Cổng mẫu: `C-AC-01`, `C-AC-02` (AC) và `C-DC-01`, `C-DC-02` (DC), cùng nhóm công suất `CH-MAC-DINH` (đấu nối 100 kW).

Profile `dev` chỉ dùng khi phát triển; không bật ở môi trường thật vì có tài khoản với mật khẩu công khai.

## Thử nhanh bằng script

Sau khi `docker compose up`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

Script đăng nhập, xem hồ sơ, đổi vai trò, đặt phiên, đặt trùng lịch, hủy đặt chỗ, và kiểm tra khóa tài khoản sau 5 lần sai.

## Chạy test

Kiểm tra toàn bộ giai đoạn 6 (Java, React, build, simulator và trace Jaeger):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-phase6.ps1
```

Xem yêu cầu môi trường và cách chạy riêng từng bước tại [Giai đoạn 6](docs/GIAI_DOAN_6.md).

Cần Docker đang chạy (test tự bật một PostgreSQL tạm bằng Testcontainers):

```powershell
mvn -pl app -am clean test
```

## API giai đoạn 1

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/auth/dang-ky` | công khai |
| POST | `/api/auth/dang-nhap` | công khai |
| GET, PUT | `/api/ho-so` | đã đăng nhập |
| POST | `/api/ho-so/xe` | đã đăng nhập |
| PUT, DELETE | `/api/ho-so/xe/{maXe}` | đã đăng nhập (chỉ xe của mình) |
| PUT | `/api/nguoi-dung/{maND}/vai-tro` | Quản trị |
| GET | `/api/nguoi-dung/{maND}/lich-su-phan-quyen` | Quản trị |
| GET | `/actuator/health` | công khai |

### Quản lý sự cố và báo cáo (giai đoạn 5)

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/su-co` | Người dùng đã đăng nhập |
| GET | `/api/su-co` | Người dùng xem sự cố của mình; nhân viên, kỹ thuật viên và quản trị xem danh sách |
| GET | `/api/su-co/{id}/lich-su` | Người báo sự cố hoặc nhân viên vận hành |
| PUT | `/api/su-co/{id}/giao-viec` | Nhân viên trực, Quản trị |
| PUT | `/api/su-co/{id}/trang-thai` | Kỹ thuật viên được giao, Quản trị |
| GET | `/api/bao-cao?tuNgay=yyyy-MM-dd&denNgay=yyyy-MM-dd` | Quản trị |

Giao diện phân theo vai trò; người dùng gửi sự cố trong mục Hỗ trợ, nhân viên trực/quản trị giao kỹ thuật viên, kỹ thuật viên cập nhật nguyên nhân và cách xử lý. Báo cáo tổng hợp phiên, điện năng, giao dịch thành công, trạng thái cổng và sự cố trong tối đa hai năm.

Khi nhân viên trực, kỹ thuật viên hoặc quản trị báo sự cố, cổng chuyển sang **Lỗi** ngay: hệ thống dừng và hủy phiên đang chạy, không nhận đặt chỗ mới, đồng thời chuyển đặt chỗ tương lai sang cổng khác nếu có (nếu không thì hủy). Báo cáo từ người dùng chỉ tạo sự cố để nhân viên xác minh, không tự khóa cổng. Sau khi khắc phục, kỹ thuật viên/quản trị xác nhận an toàn thiết bị qua `/api/cong/{maCong}/xac-nhan-xu-ly` để mở lại cổng.

### Không gian người dùng

Đăng nhập bằng `user@tramsac.local` / `Demo1234` trong môi trường dev:

- **Trang chủ:** lịch sắp tới, phiên đang sạc, khoản chờ thanh toán và nút đặt lịch.
- **Đặt lịch sạc:** chọn xe và giờ địa phương; hệ thống phân cổng theo AC/DC. Có đổi/hủy lịch kèm lý do.
- **Phiên sạc của tôi:** bắt đầu/dừng, lọc trạng thái, điện năng ước lượng và phí tạm tính. Phiên chưa hoàn tất tự cập nhật mỗi 5 giây; lệnh được xác nhận bất đồng bộ bởi thiết bị.
- **Thanh toán:** xem chi tiết hóa đơn và thanh toán giả lập bằng QR, ví hoặc thẻ thử nghiệm. Đơn đang chờ tự cập nhật mỗi 3 giây; mã đơn được nhớ trong tab trình duyệt để mở lại sau khi tải trang.
- **Hồ sơ & xe:** sửa thông tin cá nhân, thêm/sửa/xóa xe.
- **Hỗ trợ:** gửi sự cố và xem tiến độ xử lý.

Sau khi sửa frontend, cập nhật container bằng `docker compose up -d --build web`, rồi tải lại trang. Kiểm thử giao diện: `cd web` rồi `npm test`; build: `npm run build`.

### Không gian nhân viên trực

Đăng nhập bằng `truc@tramsac.local` / `Demo1234` trong môi trường dev:

- **Tổng quan vận hành:** chỉ số cổng sạc, sự cố khẩn cấp và cảnh báo chưa phản hồi; bấm chỉ số để mở danh sách đã lọc.
- **Giám sát cổng sạc:** tìm và lọc cổng theo trạng thái, AC/DC; xem công suất, tín hiệu thiết bị, tình trạng xe, phiên liên quan và báo sự cố tại cổng.
- **Quản lý phiên sạc:** tìm theo mã phiên/cổng, lọc trạng thái và khoảng ngày UTC theo API; xem điện năng ước lượng, phí tạm tính hoặc phí chính thức. Dừng phiên cần xác nhận; giao diện chờ thiết bị phản hồi, không tự đánh dấu phiên đã dừng. Kiểm tra lại lệnh dùng cùng khóa yêu cầu để tránh tạo lệnh trùng.
- **Cảnh báo chiếm cổng:** xem lịch sử nhắc, người nhận, tình trạng xe hiện tại và ghi nhận phản hồi. Phản hồi dừng các lần nhắc tiếp theo của phiên, không xác nhận xe đã rời cổng.
- **Điều phối sự cố:** tạo báo cáo; lọc chưa phân công, khẩn cấp, quá hạn; giao bằng mã kỹ thuật viên và hạn xử lý theo giờ địa phương; xem nguyên nhân, cách xử lý và lịch sử tự cập nhật.

Dữ liệu được đọc lại sau mỗi 5 giây kể từ khi lần đọc trước hoàn tất. Khi tải thất bại, giao diện báo lỗi và giữ dữ liệu cũ. Nhân viên trực không có thao tác bắt đầu phiên, khóa/mở cổng, xác nhận an toàn hoặc cập nhật kết quả sửa chữa.

Kiểm thử riêng: `cd web` rồi `npm test -- src/OperationsWorkspace.test.jsx`. Sau khi sửa frontend, chạy `docker compose up -d --build web` và tải lại trang.

### Đặt phiên (giai đoạn 2)

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/dat-phien` (bienSo, loaiCong, gioBatDau, gioKetThuc) | đã đăng nhập |
| GET | `/api/dat-phien`, `/api/dat-phien/{maDatPhien}` | đã đăng nhập (chỉ của mình) |
| POST | `/api/dat-phien/{maDatPhien}/huy` (lyDo) | đã đăng nhập (chỉ của mình) |
| PUT | `/api/dat-phien/{maDatPhien}/doi-lich` (gioBatDau, gioKetThuc, lyDo) | đã đăng nhập (chỉ của mình) |
| POST | `/api/dat-phien/quet-qua-han`, `/api/dat-phien/quet-giu-cho` | Quản trị |

Thời gian dùng ISO-8601 **có múi giờ** (ví dụ `2026-10-16T08:00:00+07:00` hoặc `...Z`); hệ thống lưu và trả về theo UTC.
Quy định: mỗi lần đặt 30 phút–3 giờ, không đặt giờ đã qua, đặt trước tối đa 24 giờ, loại cổng phải khớp loại cổng của xe,
tối đa 2 đặt chỗ còn hiệu lực, đổi lịch tối đa 2 lần. Người không đến hoặc hủy sát giờ (dưới 15 phút) 3 lần trong 30 ngày
bị hạn chế đặt chỗ 24 giờ; người còn phiên "Chờ thanh toán" không đặt được chỗ mới. Hai tác vụ định kỳ (mỗi phút) chuyển
đặt chỗ quá 15 phút chưa sạc sang "Quá hạn không đến" và giữ cổng cho đặt chỗ từ 15 phút trước giờ bắt đầu; hai API
`quet-*` chạy ngay các tác vụ đó (tiện trình diễn).

### Lõi sạc (giai đoạn 3)

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| GET | `/api/phien`, `/api/phien/{maPhien}` | Người dùng (chỉ phiên của mình), Nhân viên trực, Quản trị (mọi phiên). Lọc: `tuNgay`, `denNgay` (yyyy-MM-dd), `trangThai`. Phiên đang sạc kèm `phiTamTinh` (nhãn "Tạm tính") |
| POST | `/api/phien/{maPhien}/bat-dau` | Người dùng sở hữu phiên |
| POST | `/api/phien/{maPhien}/dung` | Người dùng sở hữu phiên, Nhân viên trực, Quản trị |
| GET | `/api/cong`, SSE `/api/cong/stream` | Nhân viên trực, Kỹ thuật viên, Quản trị |
| POST | `/api/cong/{maCong}/khoa`, `/mo`, `/xac-nhan-xu-ly` | Kỹ thuật viên, Quản trị |
| PUT | `/api/cau-hinh-cong-suat/{maCauHinh}` | Quản trị |
| POST | `/api/bieu-gia` | Quản trị |
| GET, PUT | `/api/canh-bao-chiem-cong`, `/api/canh-bao-chiem-cong/{maCanhBao}` | Nhân viên trực, Kỹ thuật viên, Quản trị |

Lệnh bắt đầu/dừng cần header `Idempotency-Key` là UUID. API trả `202 Accepted` và trạng thái lệnh. MQTT dùng QoS 1; lệnh chờ ack được gửi lại sau 10 giây, tối đa 3 lần. Hệ thống hỏi thiết bị xác nhận an toàn trước khi gửi lệnh bắt đầu và từ chối cấp điện nếu thiết bị không phản hồi.

Mỗi biểu giá gửi lên cần phủ kín 24 giờ, không chồng lấn, cho cả AC/DC và bốn nhóm đối tượng. `gioBatDau`/`gioKetThuc` có thể để `null` để đặt một mức giá cả ngày; nếu khoảng giờ qua nửa đêm, ví dụ 22:00–05:00, giờ kết thúc nhỏ hơn giờ bắt đầu.

Trong profile dev, `device-sim` nhận lệnh MQTT và gửi heartbeat mỗi 30 giây. Bộ điều khiển mô phỏng chỉ bind vào `127.0.0.1:8090`:

```powershell
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/fault?type=RO_DIEN'
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/safe'
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/disconnect'
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/reconnect'
Invoke-RestMethod -Method Post 'http://localhost:8090/sim/devices/C-AC-01/vehicle-left'
```

Đổi `MQTT_PASSWORD` khỏi giá trị mẫu trước khi chạy; Compose tạo file mật khẩu Mosquitto từ biến này. Simulator chỉ nên bật trong Compose profile `dev`.

### Thanh toán giả lập (giai đoạn 4)

Mỗi đơn có hạn 15 phút, chỉ chủ phiên được tạo/xem đơn. `payment-sim` chạy riêng trên cổng `8091`, giữ trạng thái trong bộ nhớ và chỉ bind ra localhost trên máy dev. Thay `PAYMENT_SIM_API_TOKEN` và `PAYMENT_WEBHOOK_SECRET` trong `.env`; hai khóa này phải giống nhau giữa app và simulator.

```powershell
$headers = @{ Authorization = "Bearer <token>"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
$body = @{
  maPhien = "PS-..."
  phuongThuc = "THE_THU"
  kichBan = "THANH_CONG"
  cheDoCallback = "BINH_THUONG"
} | ConvertTo-Json
$don = Invoke-RestMethod -Method Post 'http://localhost:8080/api/thanh-toan/don' -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get "http://localhost:8080/api/thanh-toan/don/$($don.maDon)" -Headers @{ Authorization = "Bearer <token>" }
```

Kịch bản: `THANH_CONG`, `THAT_BAI`, `KHONG_DU_SO_DU`, `HET_HAN`, `CHAM_PHAN_HOI`. Callback: `BINH_THUONG`, `TRUNG` (gửi ba lần), `SAI_THU_TU`, `SAI_CHU_KY`. Với `CHAM_PHAN_HOI`, có thể gửi thêm `doTreGiay` từ 1 đến 60 và `ketQuaCuoi` là `THANH_CONG` hoặc `THAT_BAI`. Phương thức thanh toán là `THE_THU`, `VI_DIEN_TU` hoặc `MA_QR` (phản hồi có `maQr` giả lập). Với `THE_THU` có thể gửi thêm `soTheThuNghiem`: chỉ nhận số thẻ thử nghiệm `4111111111111111`, `4242424242424242`, `5555555555554444`; số thẻ chỉ được kiểm tra rồi bỏ đi, không lưu và không ghi log.

`GET /api/thanh-toan/phien/{maPhien}/hoa-don` trả tổng phí và chi tiết cách tính của phiên (chỉ chủ phiên xem được).

`payment-sim` có API riêng (header `X-Sim-Token`): `POST /sim/orders`, `GET /sim/orders/{maGiaoDich}`, `POST /sim/orders/{maGiaoDich}/gui-lai`, `POST /sim/refunds`, `GET /health` (không cần token).

Quản trị viên hoàn tiền qua `POST /api/hoan-tien` với `Idempotency-Key` mới và body `{"maGiaoDich":"GDT-...","soTienHoan":1000.00,"lyDo":"Hủy phiên"}`. Lý do hợp lệ: `Hủy phiên`, `Thiết bị lỗi` (cần có sự cố ghi nhận trên cùng cổng). Đơn thanh toán hết hạn được đóng tự động; phiên giữ trạng thái Chờ thanh toán để có thể thử lại.

Gửi token trong header `Authorization: Bearer <token>`. Mỗi phản hồi hợp lệ trả thêm header `X-New-Token`; client nên
thay token cũ bằng token mới để phiên không hết hạn khi còn hoạt động (hết hạn sau 30 phút không hoạt động).

Lỗi trả JSON `{"ma": "...", "thongBao": "...", "truong": {...}}`.

## Gỡ lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `client version 1.32 is too old` khi chạy test | Docker Desktop mới + thư viện Testcontainers cũ: tạo `%USERPROFILE%\.docker-java.properties` chứa `api.version=1.44` |
| `Tests run: 0` | Thiếu khối `maven-surefire-plugin` trong `app/pom.xml` (nhận hậu tố `IT`) |
| `JWT_SECRET phải được đặt...` | Thiếu hoặc quá ngắn `JWT_SECRET` trong `.env` |
| Cổng 8080 hoặc 5433 đang bị dùng | Đóng chương trình đang dùng, hoặc đổi `DB_HOST_PORT` trong `.env` |
