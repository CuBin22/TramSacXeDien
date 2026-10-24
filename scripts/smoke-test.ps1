# Thử nhanh hệ thống đang chạy bằng Docker Compose (docker compose up -d --build)
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

function Goi($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json
        $params["Body"] = [Text.Encoding]::UTF8.GetBytes($json)
        $params["ContentType"] = "application/json; charset=utf-8"
    }
    try {
        $resp = Invoke-WebRequest @params
        # PowerShell 5.1 đọc JSON bằng Latin-1 nếu server không ghi charset; tự giải mã UTF-8 cho đúng tiếng Việt
        $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
        $data = $null
        if ($text) { $data = $text | ConvertFrom-Json }
        return @{ Ok = $true; Status = [int]$resp.StatusCode; Data = $data }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Ok = $false; Status = $code; Data = $null }
    }
}

function Kiem($ten, $dat) {
    if ($dat) { Write-Host "[OK]  $ten" -ForegroundColor Green }
    else { Write-Host "[LOI] $ten" -ForegroundColor Red; $script:coLoi = $true }
}
$coLoi = $false

# 1. Sức khỏe
$r = Goi "Get" "/actuator/health" $null $null
Kiem "Health trả UP" ($r.Ok -and $r.Data.status -eq "UP")

# 2. Đăng nhập quản trị và xem hồ sơ
$r = Goi "Post" "/api/auth/dang-nhap" @{ dinhDanh = "admin@tramsac.local"; matKhau = "Demo1234" } $null
Kiem "Đăng nhập admin" ($r.Ok -and $r.Data.vaiTro -eq "Quản trị")
$tokenAdmin = $r.Data.token
$r = Goi "Get" "/api/ho-so" $null $tokenAdmin
Kiem "Xem hồ sơ admin" ($r.Ok -and $r.Data.email -eq "admin@tramsac.local")

# 3. Không có token thì bị từ chối
$r = Goi "Get" "/api/ho-so" $null $null
Kiem "Không token trả 401" ($r.Status -eq 401)

# 4. Người dùng thường không được đổi vai trò; admin đổi được rồi trả lại
$r = Goi "Post" "/api/auth/dang-nhap" @{ dinhDanh = "user@tramsac.local"; matKhau = "Demo1234" } $null
$tokenUser = $r.Data.token
$maUser = $r.Data.maND
$r = Goi "Put" "/api/nguoi-dung/$maUser/vai-tro" @{ vaiTroMoi = "Quản trị" } $tokenUser
Kiem "Người dùng đổi vai trò bị 403" ($r.Status -eq 403)
$r = Goi "Put" "/api/nguoi-dung/$maUser/vai-tro" @{ vaiTroMoi = "Kỹ thuật viên" } $tokenAdmin
Kiem "Admin đổi vai trò thành Kỹ thuật viên" ($r.Ok -and $r.Data.thayDoi -eq $true)
$r = Goi "Put" "/api/nguoi-dung/$maUser/vai-tro" @{ vaiTroMoi = "Người dùng" } $tokenAdmin
Kiem "Admin trả lại vai trò Người dùng" ($r.Ok -and $r.Data.thayDoi -eq $true)
$r = Goi "Get" "/api/nguoi-dung/$maUser/lich-su-phan-quyen" $null $tokenAdmin
Kiem "Lịch sử phân quyền có 2 dòng mới nhất" ($r.Ok -and @($r.Data).Count -ge 2)

# 4b. Đặt phiên sạc (giai đoạn 2): đặt, đặt trùng lịch bị từ chối, hủy
$dinhDang = "yyyy-MM-dd'T'HH:mm:00'Z'"
$bd = [DateTime]::UtcNow.AddHours(2).ToString($dinhDang)
$kt = [DateTime]::UtcNow.AddHours(3).ToString($dinhDang)
$bdTrung = [DateTime]::UtcNow.AddHours(2).AddMinutes(30).ToString($dinhDang)
$ktTrung = [DateTime]::UtcNow.AddHours(3).AddMinutes(30).ToString($dinhDang)
$r = Goi "Post" "/api/dat-phien" @{ bienSo = "59A-123.45"; loaiCong = "AC"; gioBatDau = $bd; gioKetThuc = $kt } $tokenUser
Kiem "Đặt phiên AC, hệ thống tự phân bổ cổng" ($r.Ok -and $r.Data.trangThai -eq "Đã đặt" -and $r.Data.maCong -like "C-AC-*")
$maDat = $r.Data.maDatPhien
$r = Goi "Post" "/api/dat-phien" @{ bienSo = "59A-123.45"; loaiCong = "AC"; gioBatDau = $bdTrung; gioKetThuc = $ktTrung } $tokenUser
Kiem "Đặt trùng lịch của chính mình bị từ chối (409)" ($r.Status -eq 409)
$r = Goi "Post" "/api/dat-phien" @{ bienSo = "59A-123.45"; loaiCong = "DC"; gioBatDau = $bdTrung; gioKetThuc = $ktTrung } $tokenUser
Kiem "Loại cổng không khớp với xe bị từ chối (400)" ($r.Status -eq 400)
$r = Goi "Get" "/api/dat-phien" $null $tokenUser
Kiem "Danh sách đặt chỗ có đặt chỗ vừa tạo" ($r.Ok -and (@($r.Data | Where-Object { $_.maDatPhien -eq $maDat }).Count -eq 1))
$r = Goi "Post" "/api/dat-phien/$maDat/huy" @{ lyDo = "Thử nghiệm" } $tokenUser
Kiem "Hủy đặt chỗ" ($r.Ok -and $r.Data.trangThai -eq "Hủy")
$r = Goi "Post" "/api/dat-phien/quet-qua-han" $null $tokenAdmin
Kiem "Quản trị chạy tác vụ quét quá hạn" ($r.Ok -and $r.Data.soLuong -ge 0)

# 5. Đăng ký tài khoản mới rồi thử khóa sau 5 lần sai
$hau = [guid]::NewGuid().ToString("N").Substring(0, 8)
$email = "thu-$hau@demo.local"
$dangKy = @{
    hoTen = "Nguyễn Văn Thử"; email = $email; doiTuong = "Sinh viên"; maDinhDanh = "SV$hau"
    bienSo = "TH-$hau"; loaiXe = "Ô tô điện"; loaiCongSuDung = "AC"; matKhau = "Abcd1234"; xacNhanMatKhau = "Abcd1234"
}
$r = Goi "Post" "/api/auth/dang-ky" $dangKy $null
Kiem "Đăng ký tài khoản mới" ($r.Ok -and $r.Data.maND)
for ($i = 1; $i -le 5; $i++) {
    $r = Goi "Post" "/api/auth/dang-nhap" @{ dinhDanh = $email; matKhau = "Sai12345" } $null
}
Kiem "Lần sai thứ 5 vẫn trả 401" ($r.Status -eq 401)
$r = Goi "Post" "/api/auth/dang-nhap" @{ dinhDanh = $email; matKhau = "Abcd1234" } $null
Kiem "Sau 5 lần sai, đúng mật khẩu vẫn bị khóa (423)" ($r.Status -eq 423)

Write-Host ""
if ($coLoi) { Write-Host "CÓ LỖI - xem các dòng [LOI] ở trên." -ForegroundColor Red; exit 1 }
Write-Host "TẤT CẢ ĐỀU ĐẠT." -ForegroundColor Green
