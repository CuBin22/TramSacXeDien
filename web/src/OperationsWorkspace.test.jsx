import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import App from './App.jsx';

const port = {maCong:'AC-1',loaiCong:'AC',trangThai:'Đang sạc',congSuatDinhMuc:7.4,congSuatDuocCap:7,coXe:true,heartbeatCuoi:'2026-10-21T01:00:00'};
const charging = {maPhien:'PS-1',maCong:'AC-1',maDatPhien:'DP-1',trangThai:'Đang sạc',batDauLuc:'2026-10-21T01:00:00',dienNangKWh:0,phiTamTinh:{dienNangUocLuongKWh:3,phiTamTinh:12000}};
const incident = {maSuCo:'SC-1',maCong:'AC-1',loaiSuCo:'Cổng chập chờn',moTa:'Cần kiểm tra',mucUuTien:'Khẩn cấp',trangThai:'Mới',hanXuLy:'2020-01-01T00:00:00'};
const warning = {maCanhBao:'CB-1',maPhien:'PS-1',lanCanhBao:4,nguoiNhan:'Nhân viên trực',thoiDiem:'2026-10-21T01:00:00',phanHoi:null};
const response = (body,status=200) => Promise.resolve({ok:status<400,status,headers:new Headers(),json:async()=>body});
beforeEach(()=>{
  localStorage.clear(); sessionStorage.clear();
  localStorage.setItem('token','test');localStorage.setItem('role','Nhân viên trực');localStorage.setItem('name','NV-1');
  vi.stubGlobal('fetch',vi.fn((url,o)=>{
    if(url==='/api/cong')return response([port,{...port,maCong:'DC-2',loaiCong:'DC',trangThai:'Lỗi'}]);
    if(url==='/api/phien')return response([charging,{...charging,maPhien:'PS-2',maCong:'DC-2',trangThai:'Hoàn tất',phiTamTinh:null}]);
    if(url.startsWith('/api/phien?'))return response([charging]);
    if(url==='/api/su-co')return response(o?.method==='POST'?{...incident,...JSON.parse(o.body)}:[incident]);
    if(url==='/api/canh-bao-chiem-cong')return response([warning]);
    if(url.endsWith('/giao-viec'))return response({...incident,...JSON.parse(o.body),trangThai:'Đang xử lý'});
    if(url.endsWith('/lich-su'))return response([]);
    if(url==='/api/canh-bao-chiem-cong/CB-1')return response({...warning,...JSON.parse(o.body)});
    if(url.endsWith('/dung'))return response({maLenh:'L-1',maPhien:'PS-1',trangThai:'Chờ ack'},202);
    return response({});
  }));
});
afterEach(()=>{cleanup();vi.useRealTimers();vi.unstubAllGlobals()});

it('opens the faulty port filter from the dashboard without exposing technician controls',async()=>{
  render(<App/>);
  fireEvent.click(await screen.findByRole('button',{name:/Cổng lỗi \/ mất kết nối/}));
  expect(await screen.findByRole('button',{name:'Chi tiết cổng DC-2'})).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Chi tiết cổng AC-1'})).toBeNull();
  expect(screen.queryByRole('button',{name:/Khóa bảo trì|Mở lại cổng|Báo cáo$/})).toBeNull();
});

it('confirms stopping with an idempotency key and keeps the actual charging state while awaiting ack',async()=>{
  render(<App/>);
  fireEvent.click(screen.getByRole('button',{name:'Quản lý phiên sạc'}));
  fireEvent.click(await screen.findByRole('button',{name:'Dừng phiên PS-1'}));
  expect(fetch.mock.calls.some(([u])=>u.endsWith('/dung'))).toBe(false);
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận dừng'}));
  expect(await screen.findByText(/L-1.*chờ thiết bị phản hồi/i)).toBeTruthy();
  expect(screen.getAllByText('Đang sạc').length).toBeGreaterThan(0);
  const options=fetch.mock.calls.find(([u])=>u.endsWith('/dung'))[1];
  expect(options.headers['Idempotency-Key']).toMatch(/^[a-f0-9-]{36}$/);
});

it('preserves a failed stop dialog and shows the backend refusal',async()=>{
  const base=fetch.getMockImplementation();
  fetch.mockImplementation((u,o)=>u.endsWith('/dung')?response({thongBao:'Phiên không thể dừng.'},409):base(u,o));
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Quản lý phiên sạc'}));
  fireEvent.click(await screen.findByRole('button',{name:'Dừng phiên PS-1'}));
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận dừng'}));
  expect(await within(screen.getByRole('dialog')).findByText('Phiên không thể dừng.')).toBeTruthy();
});

it('records a warning response without claiming the port is free',async()=>{
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Cảnh báo chiếm cổng'}));
  fireEvent.click(await screen.findByRole('button',{name:'Phản hồi CB-1'}));
  fireEvent.change(screen.getByLabelText('Nội dung phản hồi'),{target:{value:'Đã liên hệ chủ xe'}});
  fireEvent.click(screen.getByRole('button',{name:'Lưu phản hồi'}));
  expect(await screen.findByText('Đã liên hệ chủ xe')).toBeTruthy();
  expect(fetch.mock.calls.some(([u,o])=>u==='/api/canh-bao-chiem-cong/CB-1'&&JSON.parse(o.body).phanHoi==='Đã liên hệ chủ xe')).toBe(true);
  expect(screen.getByText(/Phản hồi không xác nhận xe đã rời cổng/)).toBeTruthy();
});

it('creates a report prefilled from a port then assigns an incident with a UTC deadline',async()=>{
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Giám sát cổng sạc'}));
  fireEvent.click(await screen.findByRole('button',{name:'Chi tiết cổng AC-1'}));
  fireEvent.click(screen.getByRole('button',{name:'Báo sự cố tại cổng'}));
  expect(screen.getByLabelText('Cổng sạc').value).toBe('AC-1');
  fireEvent.change(screen.getByLabelText('Loại sự cố'),{target:{value:'Mất tín hiệu'}});
  fireEvent.change(screen.getByLabelText('Mô tả'),{target:{value:'Tín hiệu không ổn định'}});
  fireEvent.click(screen.getByRole('button',{name:'Gửi báo cáo'}));
  await screen.findByText('Đã báo sự cố và đưa cổng ra khỏi hoạt động.');
  fireEvent.click(screen.getByRole('button',{name:'Điều phối sự cố'}));
  fireEvent.click(await screen.findByRole('button',{name:'Chi tiết sự cố SC-1'}));
  fireEvent.change(screen.getByLabelText('Mã kỹ thuật viên'),{target:{value:'KTV-2'}});
  fireEvent.change(screen.getByLabelText('Hạn xử lý'),{target:{value:'2099-10-22T09:00'}});
  fireEvent.click(screen.getByRole('button',{name:'Giao việc'}));
  expect(await within(screen.getByRole('dialog')).findByText('KTV-2')).toBeTruthy();
  const body=JSON.parse(fetch.mock.calls.find(([u])=>u.endsWith('/giao-viec'))[1].body);
  expect(body).toEqual({maKTV:'KTV-2',hanXuLy:new Date('2099-10-22T09:00').toISOString().slice(0,-1)});
});

it('sends session date filters to the backend and searches the returned sessions',async()=>{
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Quản lý phiên sạc'}));
  await screen.findByRole('button',{name:'Dừng phiên PS-1'});
  fireEvent.change(screen.getByLabelText('Từ ngày (UTC)'),{target:{value:'2026-10-21'}});
  fireEvent.click(screen.getByRole('button',{name:'Áp dụng ngày'}));
  await screen.findByText('1 phiên sạc');
  expect(fetch.mock.calls.some(([u])=>u==='/api/phien?tuNgay=2026-10-21')).toBe(true);
  fireEvent.change(screen.getByLabelText('Tìm phiên sạc'),{target:{value:'không tồn tại'}});
  expect(screen.queryByRole('button',{name:'Dừng phiên PS-1'})).toBeNull();
});

it('polls without overlapping slow reads and stops polling on logout',async()=>{
  vi.useFakeTimers();let resolveRead;let reads=0;
  const base=fetch.getMockImplementation();
  fetch.mockImplementation((u,o)=>{
    if(u==='/api/phien'&&++reads===2)return new Promise(r=>{resolveRead=()=>r(response([charging]))});
    return base(u,o);
  });
  await act(async()=>{render(<App/>)});
  await act(async()=>{await vi.advanceTimersByTimeAsync(5000)});
  await act(async()=>{await vi.advanceTimersByTimeAsync(15000)});
  expect(reads).toBe(2);
  await act(async()=>{resolveRead()});
  fireEvent.click(screen.getByRole('button',{name:'Đăng xuất'}));
  await act(async()=>{await vi.advanceTimersByTimeAsync(10000)});
  expect(reads).toBe(2);
});

it('clears an applied date range when leaving sessions so blank controls do not hide results',async()=>{
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Quản lý phiên sạc'}));
  await screen.findByText('2 phiên sạc');
  fireEvent.change(screen.getByLabelText('Từ ngày (UTC)'),{target:{value:'2026-10-21'}});
  fireEvent.click(screen.getByRole('button',{name:'Áp dụng ngày'}));
  await screen.findByText('1 phiên sạc');
  fireEvent.click(screen.getByRole('button',{name:'Giám sát cổng sạc'}));
  fireEvent.click(screen.getByRole('button',{name:'Quản lý phiên sạc'}));
  expect(await screen.findByText('2 phiên sạc')).toBeTruthy();
  expect(screen.getByLabelText('Từ ngày (UTC)').value).toBe('');
});

it('does not overwrite saved feedback with an older pending poll',async()=>{
  vi.useFakeTimers();const base=fetch.getMockImplementation();let reads=0,finish;
  fetch.mockImplementation((u,o)=>{
    if(u==='/api/canh-bao-chiem-cong'&&++reads===2)return new Promise(resolve=>{finish=()=>resolve(response([warning]))});
    return base(u,o);
  });
  await act(async()=>{render(<App/>)});
  fireEvent.click(screen.getByRole('button',{name:'Cảnh báo chiếm cổng'}));
  await act(async()=>{await vi.advanceTimersByTimeAsync(5000)});
  fireEvent.click(screen.getByRole('button',{name:'Phản hồi CB-1'}));
  fireEvent.change(screen.getByLabelText('Nội dung phản hồi'),{target:{value:'Đã liên hệ chủ xe'}});
  await act(async()=>{fireEvent.click(screen.getByRole('button',{name:'Lưu phản hồi'}))});
  await act(async()=>{finish()});
  expect(screen.getByText('Đã liên hệ chủ xe')).toBeTruthy();
});

it('can finish assignment after navigating away without reopening the incident',async()=>{
  const base=fetch.getMockImplementation();let finish;
  fetch.mockImplementation((u,o)=>u.endsWith('/giao-viec')?new Promise(resolve=>{finish=()=>resolve(response({...incident,maKTV:'KTV-2',trangThai:'Đang xử lý'}))}):base(u,o));
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'Điều phối sự cố'}));
  fireEvent.click(await screen.findByRole('button',{name:'Chi tiết sự cố SC-1'}));
  fireEvent.change(screen.getByLabelText('Mã kỹ thuật viên'),{target:{value:'KTV-2'}});
  fireEvent.change(screen.getByLabelText('Hạn xử lý'),{target:{value:'2099-10-22T09:00'}});
  fireEvent.click(screen.getByRole('button',{name:'Giao việc'}));
  fireEvent.click(screen.getByRole('button',{name:'Giám sát cổng sạc'}));
  await act(async()=>{finish()});
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.getByRole('button',{name:'Chi tiết cổng AC-1'})).toBeTruthy();
});

it('refreshes incident history when polling brings an external technician update',async()=>{
  vi.useFakeTimers();const base=fetch.getMockImplementation();let updated=false;
  fetch.mockImplementation((u,o)=>{
    if(u==='/api/su-co')return response([{...incident,trangThai:updated?'Đã xử lý':'Mới'}]);
    if(u.endsWith('/lich-su'))return response(updated?[{maLSSC:'LS-2',trangThai:'Đã xử lý',ghiChu:'Đã thay giắc sạc',nguoiCapNhat:'KTV-2',thoiDiem:'2026-10-21T02:00:00'}]:[]);
    return base(u,o);
  });
  await act(async()=>{render(<App/>)});
  fireEvent.click(screen.getByRole('button',{name:'Điều phối sự cố'}));
  await act(async()=>{fireEvent.click(screen.getByRole('button',{name:'Chi tiết sự cố SC-1'}))});
  updated=true;
  await act(async()=>{await vi.advanceTimersByTimeAsync(5000)});
  expect(within(screen.getByRole('dialog')).getByText(/Đã thay giắc sạc/)).toBeTruthy();
});
