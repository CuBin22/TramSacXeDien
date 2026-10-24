import React, {useEffect, useRef, useState} from 'react';
import {ArrowRight, BatteryCharging, CalendarClock, Car, CreditCard, Plug, X} from 'lucide-react';

const money = value => value == null ? '—' : new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value);
const date = value => value ? new Date(/Z$|[+-]\d\d:\d\d$/.test(value)?value:`${value}Z`).toLocaleString('vi-VN') : '—';
const localDate = value => {
  const d = new Date(value);
  return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);
};
const fields = event => Object.fromEntries(new FormData(event.currentTarget));
const pendingOrder = order => order && ['CHO','DANG_XU_LY'].includes(order.trangThai);
const paymentLabel = value => ({CHO:'Chờ thanh toán',DANG_XU_LY:'Đang xử lý',THANH_CONG:'Thành công',THAT_BAI:'Thất bại',HET_HAN:'Hết hạn',DA_HOAN:'Đã hoàn tiền'}[value]||value||'Đang chờ');

export default function UserWorkspace({api, tab, navigate, refresh, chargers, account}) {
  const [profile,setProfile] = useState(null);
  const [bookings,setBookings] = useState([]);
  const [sessions,setSessions] = useState([]);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState('');
  const [notice,setNotice] = useState('');
  const [busy,setBusy] = useState(false);
  const [editor,setEditor] = useState(null);
  const [invoice,setInvoice] = useState(null);
  const [order,setOrder] = useState(null);
  const [status,setStatus] = useState('all');
  const actionLock = useRef(false);
  const requestKeys = useRef({});
  const version = useRef(0);
  const loadsInFlight = useRef(0);

  async function load() {
    const current = ++version.current;
    loadsInFlight.current++;
    try {
      const [p,b,s] = await Promise.all([api('/api/ho-so'),api('/api/dat-phien'),api('/api/phien')]);
      if (current!==version.current) return;
      setProfile(p);setBookings(b);setSessions(s);
    } catch(e) {if(current===version.current)setError(e.message);}
    finally {loadsInFlight.current--;if(current===version.current)setLoading(false);}
  }
  useEffect(()=>{load();return()=>{version.current++}},[refresh]);
  // Device acknowledgements and payment callbacks arrive asynchronously.
  useEffect(()=>{
    if(!sessions.some(s=>!['Hoàn tất','Hủy'].includes(s.trangThai)))return;
    const timer=setInterval(()=>{if(!actionLock.current&&!loadsInFlight.current)load()},5000);
    return()=>clearInterval(timer);
  },[sessions.some(s=>!['Hoàn tất','Hủy'].includes(s.trangThai))]);
  useEffect(()=>{
    if(!order || !pendingOrder(order))return;
    let alive=true;
    const timer=setInterval(async()=>{
      try {
        const next=await api(`/api/thanh-toan/don/${encodeURIComponent(order.maDon)}`);
        if(alive){setOrder(next);if(!pendingOrder(next))load();}
      } catch(e){if(alive)setError(e.message);}
    },3000);
    return()=>{alive=false;clearInterval(timer)};
  },[order?.maDon,order?.trangThai]);
  useEffect(()=>{setEditor(null);setError('');setNotice('')},[tab]);
  useEffect(()=>{
    if(!editor)return;
    const previous=document.activeElement;
    const dialog=document.getElementById('customer-dialog-title')?.closest('[role="dialog"]');
    const controls=()=>Array.from(dialog?.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)')||[]);
    controls()[0]?.focus();
    const keydown=e=>{
      if(e.key==='Escape'&&!actionLock.current)setEditor(null);
      if(e.key==='Tab'){
        const items=controls(),first=items[0],last=items.at(-1);
        if(!items.length){e.preventDefault();return;}
        if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
        else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
      }
    };
    document.addEventListener('keydown',keydown);
    return()=>{document.removeEventListener('keydown',keydown);previous?.focus()};
  },[editor]);

  async function act(work,message) {
    if(actionLock.current)return;
    actionLock.current=true;setBusy(true);setError('');setNotice('');
    try {await work();setNotice(message);setEditor(null);await load();}
    catch(e){setError(e.message);}
    finally {actionLock.current=false;setBusy(false);}
  }
  const write=(path,method,body)=>api(path,{method,...(body===undefined?{}:{body:JSON.stringify(body)})});
  async function command(path,body) {
    const id=path+JSON.stringify(body);
    const key=requestKeys.current[id] ||= crypto.randomUUID();
    const result=await api(path,{method:'POST',headers:{'Idempotency-Key':key},...(body?{body:JSON.stringify(body)}:{})});
    delete requestKeys.current[id];
    return result;
  }
  function book(event) {
    event.preventDefault();
    const f=fields(event);
    const vehicle=profile.xe.find(x=>x.maXe===f.maXe);
    if(!vehicle){setError('Hãy thêm xe trước khi đặt lịch.');return;}
    act(()=>write('/api/dat-phien','POST',{bienSo:vehicle.bienSo,loaiCong:vehicle.loaiCongSuDung,gioBatDau:new Date(f.gioBatDau).toISOString(),gioKetThuc:new Date(f.gioKetThuc).toISOString()}),'Đã đặt lịch sạc. Xem cổng được phân trong Lịch đã đặt.');
  }
  async function showInvoice(session) {
    setError('');setInvoice(null);setOrder(null);
    await act(async()=>{
      const bill=await api(`/api/thanh-toan/phien/${encodeURIComponent(session.maPhien)}/hoa-don`);
      setInvoice(bill);
      const saved=sessionStorage.getItem(`payment:${account}:${session.maPhien}`);
      if(saved)setOrder(await api(`/api/thanh-toan/don/${encodeURIComponent(saved)}`));
    },'');
  }
  if(tab==='incidents')return null;
  if(loading)return <p role="status" className="empty">Đang tải dữ liệu của bạn…</p>;
  if(!profile)return <div role="alert" className="banner error">{error||'Không thể tải hồ sơ.'}<button onClick={load}>Thử lại</button></div>;
  const vehicles=profile.xe||[];
  const upcoming=bookings.filter(b=>b.trangThai==='Đã đặt').sort((a,b)=>new Date(a.gioBatDau)-new Date(b.gioBatDau));
  const active=sessions.filter(s=>s.trangThai==='Đang sạc');
  const unpaid=sessions.filter(s=>s.trangThai==='Chờ thanh toán');
  const payable=sessions.filter(s=>['Chờ thanh toán','Hoàn tất'].includes(s.trangThai));
  const alerts=<>{error&&<div className="banner error" role="alert">{error}</div>}{notice&&<div className="banner success" role="status">{notice}</div>}</>;
  const sessionCard=s=><article className="customer-card" key={s.maPhien}>
    <div className="customer-card-head"><span className="customer-icon"><BatteryCharging size={22}/></span><div><h3>{s.maPhien}</h3><p className="muted">Cổng {s.maCong}</p></div><Badge value={s.trangThai}/></div>
    <div className="customer-metrics"><div><small>{s.phiTamTinh?'Điện năng ước lượng':'Điện năng'}</small><b>{s.phiTamTinh?.dienNangUocLuongKWh??s.dienNangKWh??'—'} kWh</b></div><div><small>{s.phiTamTinh?'Phí tạm tính':'Tổng phí'}</small><b>{money(s.phiTamTinh?.phiTamTinh??s.tongPhi)}</b></div></div>
    {s.batDauLuc&&<p className="muted">Bắt đầu: {date(s.batDauLuc)}</p>}
    {s.khoaPhien&&<p className="muted">Phiên đang bị khóa. Vui lòng liên hệ hỗ trợ.</p>}
    <div className="customer-actions">
      {['Đã đặt','Chờ'].includes(s.trangThai)&&<button className="primary" disabled={busy||s.khoaPhien} onClick={()=>act(()=>command(`/api/phien/${encodeURIComponent(s.maPhien)}/bat-dau`),'Đã gửi lệnh bắt đầu. Đang chờ thiết bị xác nhận; trạng thái sẽ tự cập nhật.')}>Bắt đầu sạc</button>}
      {s.trangThai==='Đang sạc'&&<button className="secondary" disabled={busy} onClick={()=>setEditor({type:'stop',item:s})}>Dừng sạc</button>}
      {s.trangThai==='Chờ thanh toán'&&<button className="primary" onClick={()=>{navigate('payments');showInvoice(s)}} disabled={busy}>Thanh toán phiên này</button>}
    </div>
  </article>;

  return <div className="customer-workspace">
    {!editor&&alerts}
    {tab==='overview'&&<>
      <section className="customer-hero"><div><span className="eyebrow">KHÔNG GIAN CỦA BẠN</span><h1>Xin chào, {profile.hoTen}</h1><p>Một chỗ sạc sẵn sàng cho hành trình tiếp theo.</p><button className="primary" onClick={()=>navigate(vehicles.length?'bookings':'profile')}>{vehicles.length?'Đặt lịch sạc ngay':'Thêm xe để bắt đầu'}<ArrowRight size={18}/></button></div><BatteryCharging className="hero-battery" size={112} aria-hidden="true"/></section>
      <div className="customer-summary">{[[CalendarClock,upcoming.length,'Lịch sắp tới','bookings'],[BatteryCharging,active.length,'Phiên đang sạc','sessions'],[CreditCard,unpaid.length,'Chờ thanh toán','payments'],[Car,vehicles.length,'Xe của tôi','profile']].map(([Icon,n,label,dest])=><button className="customer-stat" onClick={()=>navigate(dest)} key={dest}><Icon size={20}/><strong>{n}</strong><span>{label}</span><ArrowRight size={16}/></button>)}</div>
      {unpaid.length>0&&<div className="customer-callout"><CreditCard size={21}/><p>Bạn có {unpaid.length} phiên cần thanh toán trước khi đặt lịch mới.</p><button className="secondary" onClick={()=>navigate('payments')}>Xem hóa đơn</button></div>}
      <div className="customer-columns"><section><SectionTitle title="Lịch sạc sắp tới" action="Xem lịch" onClick={()=>navigate('bookings')}/>{upcoming.length?<article className="customer-card"><Badge value={upcoming[0].trangThai}/><h3>{upcoming[0].bienSo} · {upcoming[0].maCong}</h3><p>{date(upcoming[0].gioBatDau)}</p><p className="muted">Đến {date(upcoming[0].gioKetThuc)}</p><button className="secondary" onClick={()=>navigate('sessions')}>Đi đến phiên sạc<ArrowRight size={16}/></button></article>:<Empty text="Bạn chưa có lịch sạc sắp tới."/>}</section><section><SectionTitle title="Phiên đang sạc" action="Xem phiên" onClick={()=>navigate('sessions')}/>{active.length?active.map(sessionCard):<Empty text="Chưa có phiên đang sạc. Đặt lịch và bắt đầu khi đến trạm."/>}</section></div>
      <SectionTitle title="Cổng sạc tại trạm"/><p className="muted">Trạng thái tham khảo. Hệ thống tự phân cổng phù hợp khi bạn đặt lịch.</p>
      <div className="customer-grid">{chargers.map(c=><article className="customer-card charger-preview" key={c.maCong}><Plug size={22}/><h3>{c.maCong}</h3><p className="muted">{c.loaiCong} · {c.congSuatDinhMuc} kW</p><Badge value={c.trangThai}/></article>)}</div>
      {!chargers.length&&<Empty text="Chưa có thông tin cổng sạc."/>}
    </>}
    {tab==='bookings'&&<>
      <div className="customer-columns"><section className="customer-card"><h3>Đặt một lịch sạc mới</h3><p className="muted">Đặt trước tối đa 24 giờ, mỗi phiên từ 30 phút đến 3 giờ. Giờ hiển thị theo múi giờ thiết bị của bạn.</p>
        {vehicles.length?<form className="form" onSubmit={book}>
          <label>Xe sử dụng<select name="maXe" required>{vehicles.map(v=><option key={v.maXe} value={v.maXe}>{v.bienSo} · {v.loaiCongSuDung}</option>)}</select></label>
          <label>Giờ bắt đầu<input name="gioBatDau" type="datetime-local" required/></label>
          <label>Giờ kết thúc<input name="gioKetThuc" type="datetime-local" required/></label>
          <button className="primary" disabled={busy||unpaid.length>0}>Xác nhận đặt lịch</button>
          {unpaid.length>0&&<p className="muted">Vui lòng thanh toán phiên còn nợ trước khi đặt lịch mới.</p>}
        </form>:<><Empty text="Thêm xe để hệ thống chọn loại cổng tương thích."/><button className="primary" onClick={()=>navigate('profile')}>Thêm xe</button></>}
      </section><section className="customer-card booking-guide"><CalendarClock size={32}/><h3>Chủ động thời gian sạc</h3><p>1. Chọn xe và khoảng thời gian.</p><p>2. Nhận cổng được phân trong lịch đặt.</p><p>3. Đến trạm, kết nối xe rồi vào Phiên sạc để bắt đầu.</p><small className="muted">Có thể bắt đầu từ 15 phút trước giờ đặt. Tối đa 2 lịch còn hiệu lực; mỗi lịch đổi tối đa 2 lần. Hủy sát giờ hoặc không đến nhiều lần có thể bị hạn chế đặt lịch.</small></section></div>
      <SectionTitle title="Lịch đã đặt"/><div className="customer-grid">{bookings.map(b=><article className="customer-card" key={b.maDatPhien}><div className="customer-card-head"><h3>{b.bienSo}</h3><Badge value={b.trangThai}/></div><p>{b.maCong} · {b.loaiCong} · {b.maDatPhien}</p><p>{date(b.gioBatDau)} → {date(b.gioKetThuc)}</p>{b.lyDoHuy&&<p className="muted">{b.lyDoHuy}</p>}{b.trangThai==='Đã đặt'&&<div className="customer-actions"><button className="secondary" disabled={busy||b.soLanDoiLich>=2} onClick={()=>setEditor({type:'reschedule',item:b})}>Đổi lịch</button><button className="text-btn" disabled={busy} onClick={()=>setEditor({type:'cancel',item:b})}>Hủy lịch</button></div>}</article>)}</div>
      {!bookings.length&&<Empty text="Chưa có lịch đặt. Tạo lịch đầu tiên ở biểu mẫu phía trên."/>}
    </>}
    {tab==='sessions'&&<>
      <div className="customer-callout"><BatteryCharging size={22}/><p>Kết nối xe với cổng được phân trước khi bắt đầu. Trạng thái phiên tự cập nhật mỗi 5 giây khi còn phiên chưa hoàn tất.</p></div>
      <label className="customer-filter">Trạng thái phiên<select value={status} onChange={e=>setStatus(e.target.value)}><option value="all">Tất cả phiên</option>{['Đã đặt','Chờ','Đang sạc','Kết thúc sạc','Chờ thanh toán','Hoàn tất','Hủy'].map(s=><option key={s}>{s}</option>)}</select></label>
      <div className="customer-grid">{sessions.filter(s=>status==='all'||s.trangThai===status).map(sessionCard)}</div>
      {!sessions.some(s=>status==='all'||s.trangThai===status)&&<Empty text="Chưa có phiên sạc phù hợp."/>}
    </>}
    {tab==='payments'&&<>
      <div className="customer-callout"><CreditCard size={22}/><p>Thanh toán trong chế độ giả lập. Chọn phiên để xem chi tiết điện năng, đơn giá và tổng phí.</p></div>
      <div className="customer-columns"><section><SectionTitle title="Hóa đơn của tôi"/>{payable.map(s=><article className="customer-card invoice-choice" key={s.maPhien}><Badge value={s.trangThai}/><h3>{s.maPhien} · {s.maCong}</h3><strong>{money(s.tongPhi)}</strong><button className="secondary" disabled={busy} onClick={()=>showInvoice(s)}>Xem hóa đơn</button></article>)}{!payable.length&&<Empty text="Hóa đơn sẽ xuất hiện sau khi kết thúc sạc."/>}</section>
      <section className="customer-card">{invoice?<><h3>Hóa đơn {invoice.maPhien}</h3><div className="table-wrap"><table><thead><tr><th>Khung giờ</th><th>kWh</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>{invoice.chiTiet.map((d,i)=><tr key={i}><td>{d.khungGio}</td><td>{d.dienNangKWh}</td><td>{money(d.donGia)}</td><td>{money(d.thanhTien)}</td></tr>)}</tbody></table></div><div className="invoice-total"><span>Tổng thanh toán</span><strong>{money(invoice.tongPhi)}</strong></div>
        {order&&<div className="customer-callout payment-result" role="status"><div><b>Đơn {order.maDon}</b><p>Trạng thái: {paymentLabel(order.trangThai)} · Kết quả: {paymentLabel(order.ketQuaGiaoDich)}</p><small>Hạn thanh toán: {date(order.hanThanhToan)}</small>{order.maQr&&<p className="qr-payload">Nội dung QR giả lập: {order.maQr}</p>}</div></div>}
        {sessions.find(s=>s.maPhien===invoice.maPhien)?.trangThai==='Chờ thanh toán'&&!pendingOrder(order)&&<form className="form" onSubmit={e=>{e.preventDefault();const f=fields(e);act(async()=>{const result=await command('/api/thanh-toan/don',{maPhien:invoice.maPhien,phuongThuc:f.phuongThuc,kichBan:'THANH_CONG',cheDoCallback:'BINH_THUONG'});setOrder(result);sessionStorage.setItem(`payment:${account}:${invoice.maPhien}`,result.maDon)},'Đã tạo yêu cầu thanh toán giả lập. Kết quả sẽ được cập nhật từ hệ thống.')}}><label>Phương thức thanh toán<select name="phuongThuc"><option value="MA_QR">Mã QR giả lập</option><option value="VI_DIEN_TU">Ví điện tử giả lập</option><option value="THE_THU">Thẻ thử nghiệm</option></select></label><button className="primary" disabled={busy}>Thanh toán giả lập</button></form>}
      </>:<Empty text="Chọn một hóa đơn để xem và thanh toán."/>}</section></div>
    </>}
    {tab==='profile'&&<div className="customer-columns"><section className="customer-card"><h3>Thông tin cá nhân</h3><form className="form" key={JSON.stringify([profile.hoTen,profile.email,profile.soDienThoai])} onSubmit={e=>{e.preventDefault();const f=fields(e);act(()=>write('/api/ho-so','PUT',f),'Đã lưu hồ sơ.')}}><label>Họ tên<input name="hoTen" defaultValue={profile.hoTen} required maxLength={120}/></label><label>Email<input name="email" type="email" defaultValue={profile.email||''} maxLength={120}/></label><label>Số điện thoại<input name="soDienThoai" type="tel" defaultValue={profile.soDienThoai||''} maxLength={30}/></label><p className="muted">Đối tượng: {profile.doiTuong||'—'} · Vai trò: Người dùng</p><button className="primary" disabled={busy}>Lưu hồ sơ</button></form></section>
      <section><SectionTitle title="Xe của tôi" action="Thêm xe" onClick={()=>setEditor({type:'vehicle',item:{}})}/>{vehicles.map(v=><article className="customer-card invoice-choice" key={v.maXe}><Car size={24}/><h3>{v.bienSo}</h3><p className="muted">{v.loaiXe} · Cổng {v.loaiCongSuDung}</p><div className="customer-actions"><button className="secondary" disabled={busy} onClick={()=>setEditor({type:'vehicle',item:v})}>Sửa xe</button><button className="text-btn" disabled={busy} onClick={()=>setEditor({type:'deleteVehicle',item:v})}>Xóa xe</button></div></article>)}{!vehicles.length&&<Empty text="Bạn chưa thêm xe nào."/>}</section></div>}
    {editor&&<div className="overlay"><section className="modal" role="dialog" aria-modal="true" aria-labelledby="customer-dialog-title"><div className="modal-head"><h3 id="customer-dialog-title">{{vehicle:'Thông tin xe',deleteVehicle:'Xóa xe',reschedule:'Đổi lịch sạc',cancel:'Hủy lịch sạc',stop:'Dừng phiên sạc'}[editor.type]}</h3><button className="icon-btn" aria-label="Đóng" disabled={busy} onClick={()=>setEditor(null)}><X size={20}/></button></div><div className="modal-body">{alerts}
      {editor.type==='vehicle'&&<form className="form" onSubmit={e=>{e.preventDefault();const f=fields(e);act(()=>write(`/api/ho-so/xe${editor.item.maXe?`/${encodeURIComponent(editor.item.maXe)}`:''}`,editor.item.maXe?'PUT':'POST',f),'Đã lưu thông tin xe.')}}><label>Biển số xe<input name="bienSo" defaultValue={editor.item.bienSo||''} required maxLength={20} autoFocus/></label><label>Loại xe<input name="loaiXe" defaultValue={editor.item.loaiXe||''} placeholder="Ô tô điện, xe máy điện…" required maxLength={50}/></label><label>Loại cổng sử dụng<select name="loaiCongSuDung" defaultValue={editor.item.loaiCongSuDung||'AC'}><option>AC</option><option>DC</option></select></label><button className="primary" disabled={busy}>Lưu xe</button></form>}
      {['cancel','reschedule'].includes(editor.type)&&<form className="form" onSubmit={e=>{e.preventDefault();const f=fields(e);const change=editor.type==='reschedule';act(()=>write(`/api/dat-phien/${encodeURIComponent(editor.item.maDatPhien)}/${change?'doi-lich':'huy'}`,change?'PUT':'POST',change?{lyDo:f.lyDo,gioBatDau:new Date(f.gioBatDau).toISOString(),gioKetThuc:new Date(f.gioKetThuc).toISOString()}:{lyDo:f.lyDo}),change?'Đã đổi lịch sạc.':'Đã hủy lịch sạc.')}}><p>{editor.item.bienSo} · {editor.item.maDatPhien}</p>{editor.type==='reschedule'?<><label>Giờ bắt đầu mới<input name="gioBatDau" type="datetime-local" defaultValue={localDate(editor.item.gioBatDau)} required autoFocus/></label><label>Giờ kết thúc mới<input name="gioKetThuc" type="datetime-local" defaultValue={localDate(editor.item.gioKetThuc)} required/></label></>:<p className="muted">Hủy dưới 15 phút trước giờ sạc được tính là hủy sát giờ.</p>}<label>Lý do<textarea name="lyDo" required maxLength={200}/></label><button className="primary" disabled={busy}>Xác nhận {editor.type==='reschedule'?'đổi lịch':'hủy lịch'}</button></form>}
      {editor.type==='deleteVehicle'&&<div className="form"><p>Xóa xe {editor.item.bienSo} khỏi hồ sơ?</p><button className="primary" disabled={busy} onClick={()=>act(()=>write(`/api/ho-so/xe/${encodeURIComponent(editor.item.maXe)}`,'DELETE'),'Đã xóa xe.')}>Xác nhận xóa xe</button></div>}
      {editor.type==='stop'&&<div className="form"><p>Dừng sạc tại cổng {editor.item.maCong}? Hóa đơn sẽ được tính sau khi thiết bị xác nhận kết thúc.</p><button className="primary" disabled={busy} onClick={()=>act(()=>command(`/api/phien/${encodeURIComponent(editor.item.maPhien)}/dung`),'Đã gửi lệnh dừng. Đang chờ thiết bị xác nhận và tính phí.')}>Xác nhận dừng sạc</button></div>}
    </div></section></div>}
  </div>;
}

function Badge({value}) {return <span className={`customer-badge ${['Đang sạc','Hoàn tất','Rảnh'].includes(value)?'positive':''}`}>{value}</span>}
function Empty({text}) {return <div className="customer-empty"><Plug size={26}/><p>{text}</p></div>}
function SectionTitle({title,action,onClick}) {return <div className="customer-section-title"><h3>{title}</h3>{action&&<button className="text-btn" onClick={onClick}>{action}<ArrowRight size={15}/></button>}</div>}
