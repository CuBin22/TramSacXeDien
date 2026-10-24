import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App.jsx';

const issue = { maSuCo:'SC-1',maCong:'AC-1',loaiSuCo:'Giắc lỏng',moTa:'Cần kiểm tra',mucUuTien:'Bình thường',trangThai:'Đang xử lý',maKTV:'ND-1' };
function session(role) { localStorage.setItem('token','old'); localStorage.setItem('role',role); localStorage.setItem('name','ND-1'); }
function response(body, status=200) { return Promise.resolve({ok:status<400,status,headers:new Headers({'X-New-Token':'renewed'}),json:async()=>body}); }
beforeEach(()=>{localStorage.clear();sessionStorage.clear();vi.stubGlobal('fetch',vi.fn((url,options)=>{
  if(url==='/api/su-co')return response([issue]);
  if(url==='/api/ho-so')return response({hoTen:'An',email:'an@example.com',xe:[{maXe:'XE-1',bienSo:'59A-123.45',loaiXe:'Ô tô',loaiCongSuDung:'AC'}]});
  if(url==='/api/dat-phien')return response([]);
  if(url==='/api/phien')return response([]);
  if(url.includes('/api/cong'))return response([{maCong:'AC-1',loaiCong:'AC',trangThai:'Rảnh',congSuatDinhMuc:7.4}]);
  if(url.includes('/api/bao-cao'))return response({phien:{tong_phien:1},thanhToan:{doanh_thu:10000},suCo:[],congSac:[]});
  if(url.includes('/lich-su'))return response([{maLSSC:'LS1',trangThai:'Đang xử lý',ghiChu:'Đã giao',nguoiCapNhat:'ND-1',thoiDiem:'2026-10-18T10:00:00'}]);
  if(options?.method==='PUT')return response({...issue,...JSON.parse(options.body)});
  return response({});
}));});
afterEach(()=>{cleanup();vi.useRealTimers();vi.unstubAllGlobals();});

describe('Authenticated UI',()=>{
  it('does not overlap slow session polling and eventually renders the response',async()=>{
    vi.useFakeTimers();
    const base=fetch.getMockImplementation();let reads=0,finish;
    fetch.mockImplementation((url,o)=>{
      if(url==='/api/phien'){
        reads++;
        if(reads===1)return response([{maPhien:'PS-1',maCong:'AC-1',trangThai:'Chờ'}]);
        return new Promise(resolve=>{finish=()=>resolve(response([{maPhien:'PS-1',maCong:'AC-1',trangThai:'Đang sạc',dienNangKWh:0,phiTamTinh:{dienNangUocLuongKWh:7,phiTamTinh:28000}}]))});
      }
      return base(url,o);
    });
    session('Người dùng');await act(async()=>{render(<App/>)});
    fireEvent.click(screen.getByRole('button',{name:'Phiên sạc của tôi'}));
    await act(async()=>{await vi.advanceTimersByTimeAsync(16000)});
    expect(reads).toBe(2);
    await act(async()=>{finish()});
    expect(screen.getByText('7 kWh')).toBeTruthy();
  });
  it('shows estimated energy and fee while charging',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>url==='/api/phien'?response([{maPhien:'PS-1',maCong:'AC-1',trangThai:'Đang sạc',dienNangKWh:0,phiTamTinh:{dienNangUocLuongKWh:2.5,phiTamTinh:10000}}]):base(url,o));
    session('Người dùng');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Phiên sạc của tôi'}));
    expect(await screen.findByText('2.5 kWh')).toBeTruthy();
    expect(screen.getByText('Điện năng ước lượng')).toBeTruthy();
    expect(screen.getByText('Phí tạm tính')).toBeTruthy();
  });
  it('lets a user book with their vehicle and sends timezone-aware dates',async()=>{
    session('Người dùng');render(<App/>);
    await screen.findByText(/Xin chào, An/);
    fireEvent.click(screen.getByRole('button',{name:'Đặt lịch sạc ngay'}));
    fireEvent.change(screen.getByLabelText('Giờ bắt đầu'),{target:{value:'2026-10-21T08:00'}});
    fireEvent.change(screen.getByLabelText('Giờ kết thúc'),{target:{value:'2026-10-21T09:00'}});
    fireEvent.submit(screen.getByLabelText('Giờ bắt đầu').closest('form'));
    await waitFor(()=>expect(fetch.mock.calls.some(([url,o])=>url==='/api/dat-phien'&&o?.method==='POST')).toBe(true));
    const body=JSON.parse(fetch.mock.calls.find(([url,o])=>url==='/api/dat-phien'&&o?.method==='POST')[1].body);
    expect(body.bienSo).toBe('59A-123.45');
    expect(body.loaiCong).toBe('AC');
    expect(body.gioBatDau).toBe(new Date('2026-10-21T08:00').toISOString());
    expect(screen.queryByText('Sự cố đang mở')).toBeNull();
  });
  it('submits charging commands with an idempotency key and displays backend refusal',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>{
      if(url==='/api/phien')return response([{maPhien:'PS-1',maCong:'AC-1',trangThai:'Chờ'}]);
      if(url==='/api/phien/PS-1/bat-dau')return response({thongBao:'Thiết bị không xác nhận an toàn.'},409);
      return base(url,o);
    });
    session('Người dùng');render(<App/>);
    fireEvent.click(await screen.findByRole('button',{name:'Phiên sạc của tôi'}));
    fireEvent.click(await screen.findByRole('button',{name:'Bắt đầu sạc'}));
    expect(await screen.findByText('Thiết bị không xác nhận an toàn.')).toBeTruthy();
    const options=fetch.mock.calls.find(([url])=>url==='/api/phien/PS-1/bat-dau')[1];
    expect(options.headers['Idempotency-Key']).toMatch(/^[a-f0-9-]{36}$/);
  });
  it('supports rescheduling and canceling a booking with a reason',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>url==='/api/dat-phien'?response([{maDatPhien:'DP-1',bienSo:'59A-123.45',maCong:'AC-1',loaiCong:'AC',trangThai:'Đã đặt',gioBatDau:'2026-10-21T01:00:00Z',gioKetThuc:'2026-10-21T02:00:00Z',soLanDoiLich:0}]):base(url,o));
    session('Người dùng');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Đặt lịch sạc',exact:true}));
    fireEvent.click(await screen.findByRole('button',{name:'Đổi lịch',exact:true}));
    fireEvent.change(screen.getByLabelText('Giờ bắt đầu mới'),{target:{value:'2026-10-21T10:00'}});
    fireEvent.change(screen.getByLabelText('Giờ kết thúc mới'),{target:{value:'2026-10-21T11:00'}});
    fireEvent.change(screen.getByLabelText('Lý do'),{target:{value:'Đổi giờ đến'}});
    fireEvent.click(screen.getByRole('button',{name:'Xác nhận đổi lịch'}));
    await screen.findByText('Đã đổi lịch sạc.');
    const options=fetch.mock.calls.find(([url])=>url==='/api/dat-phien/DP-1/doi-lich')[1];
    expect(JSON.parse(options.body)).toEqual({lyDo:'Đổi giờ đến',gioBatDau:new Date('2026-10-21T10:00').toISOString(),gioKetThuc:new Date('2026-10-21T11:00').toISOString()});
    fireEvent.click(screen.getByRole('button',{name:'Hủy lịch',exact:true}));
    fireEvent.change(screen.getByLabelText('Lý do'),{target:{value:'Không cần sạc'}});
    fireEvent.click(screen.getByRole('button',{name:'Xác nhận hủy lịch'}));
    await screen.findByText('Đã hủy lịch sạc.');
    expect(JSON.parse(fetch.mock.calls.find(([url])=>url==='/api/dat-phien/DP-1/huy')[1].body)).toEqual({lyDo:'Không cần sạc'});
  });
  it('creates a simulated payment and waits for the callback instead of claiming success',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>{
      if(url==='/api/phien')return response([{maPhien:'PS-1',maCong:'AC-1',trangThai:'Chờ thanh toán',tongPhi:12000}]);
      if(url.endsWith('/hoa-don'))return response({maPhien:'PS-1',tongPhi:12000,chiTiet:[{khungGio:'Cả ngày',dienNangKWh:3,donGia:4000,thanhTien:12000}]});
      if(url==='/api/thanh-toan/don')return response({maDon:'DON-1',trangThai:'CHO',soTien:12000});
      return base(url,o);
    });
    session('Người dùng');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Thanh toán',exact:true}));
    fireEvent.click(await screen.findByRole('button',{name:'Xem hóa đơn'}));
    await screen.findByText('Cả ngày');
    fireEvent.click(screen.getByRole('button',{name:'Thanh toán giả lập',exact:true}));
    await screen.findByText('Đơn DON-1');
    expect(screen.queryByRole('button',{name:'Thanh toán giả lập',exact:true})).toBeNull();
    expect(screen.getByText(/Trạng thái: Chờ thanh toán/)).toBeTruthy();
    const options=fetch.mock.calls.find(([url])=>url==='/api/thanh-toan/don')[1];
    expect(JSON.parse(options.body)).toEqual({maPhien:'PS-1',phuongThuc:'MA_QR',kichBan:'THANH_CONG',cheDoCallback:'BINH_THUONG'});
    expect(options.headers['Idempotency-Key']).toMatch(/^[a-f0-9-]{36}$/);
  });
  it('saves vehicle and profile fields without sending privileged role changes',async()=>{
    session('Người dùng');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Hồ sơ & xe'}));
    fireEvent.click(await screen.findByRole('button',{name:'Thêm xe'}));
    fireEvent.change(screen.getByLabelText('Biển số xe'),{target:{value:'59B-999.99'}});
    fireEvent.change(screen.getByLabelText('Loại xe'),{target:{value:'Ô tô điện'}});
    fireEvent.change(screen.getByLabelText('Loại cổng sử dụng'),{target:{value:'DC'}});
    fireEvent.click(screen.getByRole('button',{name:'Lưu xe'}));
    await screen.findByText('Đã lưu thông tin xe.');
    expect(JSON.parse(fetch.mock.calls.find(([url])=>url==='/api/ho-so/xe')[1].body)).toEqual({bienSo:'59B-999.99',loaiXe:'Ô tô điện',loaiCongSuDung:'DC'});
    fireEvent.change(screen.getByLabelText('Họ tên'),{target:{value:'An Nguyễn'}});
    fireEvent.click(screen.getByRole('button',{name:'Lưu hồ sơ'}));
    await screen.findByText('Đã lưu hồ sơ.');
    const options=fetch.mock.calls.find(([url,o])=>url==='/api/ho-so'&&o?.method==='PUT')[1];
    expect(JSON.parse(options.body)).toEqual({hoTen:'An Nguyễn',email:'an@example.com',soDienThoai:''});
  });
  it('shows server login errors instead of entering the dashboard',async()=>{
    fetch.mockImplementation(()=>response({thongBao:'Thông tin đăng nhập không đúng.'},401));
    render(<App/>);
    fireEvent.change(screen.getByLabelText('Email hoặc số điện thoại'),{target:{value:'wrong@example.com'}});
    fireEvent.change(screen.getByLabelText('Mật khẩu'),{target:{value:'wrong123'}});
    fireEvent.submit(screen.getByLabelText('Mật khẩu').closest('form'));
    expect(await screen.findByText('Thông tin đăng nhập không đúng.')).toBeTruthy();
    expect(screen.queryByText('Chào mừng trở lại')).toBeNull();
  });
  it('uses limited charger endpoint for a user and hides admin reports',async()=>{
    session('Người dùng');render(<App/>);
    expect(await screen.findByText('AC-1')).toBeTruthy();
    expect(fetch.mock.calls.some(([url])=>url==='/api/cong/tom-tat')).toBe(true);
    expect(screen.queryByRole('button',{name:'Báo cáo'})).toBeNull();
    expect(localStorage.getItem('token')).toBe('renewed');
  });
  it('refreshes detail and history after technician saves a status',async()=>{
    session('Kỹ thuật viên');render(<App/>);
    await screen.findByText('Giắc lỏng');
    fireEvent.click(screen.getByRole('button',{name:/Công việc của tôi/}));
    fireEvent.click(await screen.findByRole('button',{name:'Chi tiết'}));
    await screen.findByText(/Đã giao/);
    const modal = screen.getByText('Chi tiết sự cố').closest('.modal');
    fireEvent.change(within(modal).getByRole('combobox'),{target:{value:'Chờ linh kiện'}});
    fireEvent.click(within(modal).getByRole('button',{name:'Lưu cập nhật'}));
    await waitFor(()=>expect(fetch.mock.calls.filter(([url])=>url==='/api/su-co/SC-1/lich-su').length).toBe(2));
    expect(within(modal).getAllByText('Chờ linh kiện').length).toBeGreaterThan(0);
  });
  it('separates assigned work from completed history and other technicians',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>url==='/api/su-co'?response([issue,{...issue,maSuCo:'SC-OTHER',maKTV:'ND-2',loaiSuCo:'Việc người khác'},{...issue,maSuCo:'SC-DONE',trangThai:'Đã đóng',loaiSuCo:'Đã sửa xong'}]):base(url,o));
    session('Kỹ thuật viên');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:/Công việc của tôi/}));
    await screen.findByText(/SC-1 ·/);
    expect(screen.queryByText(/SC-OTHER ·/)).toBeNull();
    expect(screen.queryByText(/SC-DONE ·/)).toBeNull();
    fireEvent.click(screen.getByRole('button',{name:'Lịch sử xử lý'}));
    expect(await screen.findByText(/SC-DONE ·/)).toBeTruthy();
    expect(screen.queryByText(/SC-1 ·/)).toBeNull();
  });
  it('filters overdue tasks from the overview shortcut',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>url==='/api/su-co'?response([{...issue,hanXuLy:'2020-01-01T00:00:00'},{...issue,maSuCo:'SC-FUTURE',hanXuLy:'2099-01-01T00:00:00'}]):base(url,o));
    session('Kỹ thuật viên');render(<App/>);
    await screen.findByText(/SC-FUTURE ·/);
    fireEvent.click(screen.getByRole('button',{name:/Đã quá hạn/}));
    expect(screen.getByText(/SC-1 ·/)).toBeTruthy();
    expect(screen.queryByText(/SC-FUTURE ·/)).toBeNull();
    fireEvent.change(screen.getByLabelText('Trạng thái'),{target:{value:''}});
    expect(screen.getByText(/SC-FUTURE ·/)).toBeTruthy();
  });
  it('locks an idle charger and displays the updated state',async()=>{
    const base=fetch.getMockImplementation();let locked=false;
    fetch.mockImplementation((url,o)=>{
      if(url==='/api/cong/AC-1/khoa'){locked=true;return response({maCong:'AC-1',trangThai:'Bảo trì'});}
      if(url==='/api/cong')return response([{maCong:'AC-1',loaiCong:'AC',trangThai:locked?'Bảo trì':'Rảnh',congSuatDinhMuc:7.4}]);
      return base(url,o);
    });
    session('Kỹ thuật viên');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Cổng sạc & bảo trì'}));
    fireEvent.click(await screen.findByRole('button',{name:'Khóa bảo trì'}));
    fireEvent.click(screen.getByRole('button',{name:'Xác nhận thao tác'}));
    expect(await screen.findByRole('button',{name:'Mở lại cổng'})).toBeTruthy();
    expect(fetch.mock.calls.find(([url])=>url==='/api/cong/AC-1/khoa')[1].method).toBe('POST');
  });
  it.each([
    ['Bảo trì','Mở lại cổng','mo'],
    ['Lỗi','Xác nhận xử lý an toàn','xac-nhan-xu-ly'],
  ])('restores a %s charger after safety approval',async(state,label,route)=>{
    const base=fetch.getMockImplementation();let restored=false;
    fetch.mockImplementation((url,o)=>{
      if(url===`/api/cong/AC-1/${route}`){restored=true;return response({maCong:'AC-1',trangThai:'Rảnh'});}
      if(url==='/api/cong')return response([{maCong:'AC-1',loaiCong:'AC',trangThai:restored?'Rảnh':state}]);
      return base(url,o);
    });
    session('Kỹ thuật viên');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Cổng sạc & bảo trì'}));
    fireEvent.click(await screen.findByRole('button',{name:label}));
    fireEvent.click(screen.getByRole('button',{name:'Xác nhận thao tác'}));
    expect(await screen.findByRole('button',{name:'Khóa bảo trì'})).toBeTruthy();
    expect(fetch.mock.calls.find(([url])=>url===`/api/cong/AC-1/${route}`)[1].method).toBe('POST');
  });
  it('keeps an unsafe charger in error when the device refuses confirmation',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>{
      if(url==='/api/cong')return response([{maCong:'AC-1',loaiCong:'AC',trangThai:'Lỗi'}]);
      if(url==='/api/cong/AC-1/xac-nhan-xu-ly')return response({thongBao:'Thiết bị chưa xác nhận an toàn.'},409);
      return base(url,o);
    });
    session('Kỹ thuật viên');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Cổng sạc & bảo trì'}));
    fireEvent.click(await screen.findByRole('button',{name:'Xác nhận xử lý an toàn'}));
    fireEvent.click(screen.getByRole('button',{name:'Xác nhận thao tác'}));
    expect(await screen.findByRole('alert')).toHaveProperty('textContent','Thiết bị chưa xác nhận an toàn.');
    expect(screen.queryByRole('button',{name:'Khóa bảo trì'})).toBeNull();
  });
  it('explains why a repaired disconnected or maintained port cannot be manually forced online',async()=>{
    const base=fetch.getMockImplementation();
    fetch.mockImplementation((url,o)=>url==='/api/cong'?response([
      {maCong:'AC-1',loaiCong:'AC',trangThai:'Mất kết nối'},
      {maCong:'AC-2',loaiCong:'AC',trangThai:'Bảo trì'},
    ]):base(url,o));
    session('Kỹ thuật viên');render(<App/>);
    fireEvent.click(screen.getByRole('button',{name:'Cổng sạc & bảo trì'}));
    expect(await screen.findByText(/heartbeat và trạng thái thực từ thiết bị/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button',{name:'Mở lại cổng'}));
    expect(screen.getByRole('dialog')).toHaveProperty('textContent',expect.stringContaining('Thiết bị phải đang kết nối và xác nhận an toàn'));
  });
  it('logging out preserves unrelated browser data',async()=>{
    session('Quản trị');localStorage.setItem('unrelated','keep');render(<App/>);
    await screen.findByText('AC-1');fireEvent.click(screen.getByTitle('Đăng xuất'));
    expect(screen.getByLabelText('Mật khẩu')).toBeTruthy();
    expect(localStorage.getItem('unrelated')).toBe('keep');
    expect(localStorage.getItem('token')).toBeNull();
  });
});
