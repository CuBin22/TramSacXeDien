import React, { useEffect, useState } from 'react';
import { ArrowUpRight, ClipboardList, Clock, CircleCheck, PackageSearch, Search, ShieldCheck, TriangleAlert, Wrench, Plug, X } from 'lucide-react';

const finished = s => ['Đã xử lý','Đã đóng','Đã hủy'].includes(s.trangThai);
const utcDate = value => new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : value+'Z');
const dateLabel = value => value ? utcDate(value).toLocaleString('vi-VN') : 'Chưa có hạn xử lý';
const overdue = s => !finished(s) && s.hanXuLy && utcDate(s.hanXuLy).getTime() < Date.now();
const priorities = {'Khẩn cấp':0,'Cao':1,'Bình thường':2,'Trung bình':2,'Thấp':3};
const tones = {'Lỗi':'#FF5C7A','Khẩn cấp':'#FF5C7A','Cao':'#FFB547','Bảo trì':'#FFB547','Chờ linh kiện':'#FFB547','Đang xử lý':'#60C3FF','Mất kết nối':'#AAA4BC','Đã đóng':'#83DDBB','Đã xử lý':'#83DDBB'};
function Badge({value}){return <span className="tech-badge" style={{'--tone':tones[value]||'#B39DFF'}}><i/>{value}</span>}

export default function TechnicianWorkspace({api,tab,navigate,incidents,chargers,account,onSelect,reload,loading}){
  const [query,setQuery]=useState('');
  const [status,setStatus]=useState('');
  const [priority,setPriority]=useState('');
  const [command,setCommand]=useState(null);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  useEffect(()=>{setQuery('');setPriority('');setStatus(current=>{
    const valid=tab==='maintenance'?['Rảnh','Đã đặt','Đang sạc','Bảo trì','Lỗi','Mất kết nối']:tab==='history'?['Đã xử lý','Đã đóng','Đã hủy']:['Đang xử lý','Chờ linh kiện','Đã giao','Quá hạn'];
    return valid.includes(current)?current:'';
  })},[tab]);
  const mine=incidents.filter(s=>s.maKTV===account);
  const active=mine.filter(s=>!finished(s)).sort((a,b)=>(priorities[a.mucUuTien]??4)-(priorities[b.mucUuTien]??4)||(a.hanXuLy?utcDate(a.hanXuLy).getTime():Infinity)-(b.hanXuLy?utcDate(b.hanXuLy).getTime():Infinity));
  const history=mine.filter(finished);
  const attention=chargers.filter(c=>['Lỗi','Bảo trì','Mất kết nối'].includes(c.trangThai));
  const search=value=>value.toLocaleLowerCase('vi-VN').includes(query.trim().toLocaleLowerCase('vi-VN'));
  const rows=(tab==='history'?history:active).filter(s=>(!status||(status==='Quá hạn'?overdue(s):s.trangThai===status))&&(!priority||s.mucUuTien===priority)&&search(`${s.maSuCo} ${s.maCong} ${s.loaiSuCo} ${s.moTa}`));
  const ports=chargers.filter(c=>(!status||c.trangThai===status)&&search(`${c.maCong} ${c.loaiCong}`));
  function go(next,filter=''){setQuery('');setStatus(filter);setPriority('');navigate(next)}
  async function execute(){
    if(busy)return;
    setBusy(true);setError('');setNotice('');
    try{
      await api(`/api/cong/${encodeURIComponent(command.port.maCong)}/${command.route}`,{method:'POST'});
      setNotice(`${command.label}: ${command.port.maCong} thành công.`);
      setCommand(null);await reload();
    }catch(e){setError(e.message)}finally{setBusy(false)}
  }
  function taskCard(s){return <article className="tech-task" key={s.maSuCo}>
    <div className="tech-task-head"><span className="tech-code">{s.maSuCo} · {s.maCong}</span><Badge value={s.mucUuTien}/></div>
    <h3>{s.loaiSuCo}</h3><p className="tech-description">{s.moTa}</p>
    <div className={`tech-deadline ${overdue(s)?'late':''}`}><Clock size={14}/>{overdue(s)?'Quá hạn · ':''}{dateLabel(s.hanXuLy)}</div>
    <div className="tech-task-foot"><Badge value={s.trangThai}/><button className="text-btn" onClick={()=>onSelect(s)}>Chi tiết <ArrowUpRight size={15}/></button></div>
  </article>}
  const empty=<div className="customer-empty"><CircleCheck size={24}/><p>{loading?'Đang tải dữ liệu…':'Không có công việc phù hợp.'}</p></div>;
  return <div className="tech-workspace">
    {notice&&<div className="banner success" role="status">{notice}</div>}
    {error&&!command&&<div className="banner error" role="alert">{error}</div>}
    {tab==='overview'&&<>
      <section className="tech-hero"><div><span className="eyebrow">KHÔNG GIAN KỸ THUẬT VIÊN</span><h1>Sẵn sàng cho ca làm việc.</h1><p>Bạn có <b>{active.length} công việc</b> cần xử lý. Ưu tiên sự cố khẩn cấp và các việc sắp đến hạn.</p><button className="primary" onClick={()=>go('incidents')}>Xem công việc <ArrowUpRight size={17}/></button></div><div className="tech-hero-icon" aria-hidden="true"><Wrench size={56}/><span>BẢO TRÌ & XỬ LÝ SỰ CỐ</span></div></section>
      <div className="tech-stats">{[
        ['Công việc được giao',active.length,ClipboardList,'incidents',''],
        ['Đang xử lý',active.filter(s=>s.trangThai==='Đang xử lý').length,Wrench,'incidents','Đang xử lý'],
        ['Chờ linh kiện',active.filter(s=>s.trangThai==='Chờ linh kiện').length,PackageSearch,'incidents','Chờ linh kiện'],
        ['Đã quá hạn',active.filter(overdue).length,Clock,'incidents','Quá hạn'],
      ].map(([label,value,Icon,target,filter])=><button className="tech-stat" key={label} onClick={()=>go(target,filter)}><Icon size={20}/><strong>{loading?'—':value}</strong><span>{label}</span><ArrowUpRight size={15}/></button>)}</div>
      <div className="tech-columns"><section><div className="customer-section-title"><h3>Ưu tiên xử lý</h3><span className="muted">{active.length} công việc</span></div><div className="tech-task-list">{active.length?active.slice(0,4).map(taskCard):empty}</div></section>
        <aside className="tech-side"><section className="panel"><div className="panel-head"><div><h3>Cổng cần chú ý</h3><p>{attention.length} cổng cần theo dõi kỹ thuật</p></div><TriangleAlert size={20}/></div><div className="tech-port-summary">{attention.slice(0,5).map(c=><button key={c.maCong} onClick={()=>go('maintenance',c.trangThai)}><Plug size={17}/><b>{c.maCong}</b><Badge value={c.trangThai}/></button>)}{!attention.length&&<p className="muted">{loading?'Đang tải…':'Không có cổng cần chú ý.'}</p>}<button className="text-btn" onClick={()=>go('maintenance')}>Xem tất cả cổng <ArrowUpRight size={15}/></button></div></section>
        <section className="tech-guide"><ShieldCheck size={26}/><h3>Hoàn tất đúng quy trình</h3><ol><li>Kiểm tra cổng và xác định nguyên nhân.</li><li>Ghi nhận cách xử lý, cập nhật tiến độ.</li><li>Xác nhận an toàn thiết bị trước khi mở lại cổng.</li></ol></section></aside></div>
    </>}
    {['incidents','history','maintenance'].includes(tab)&&<>
      <div className="tech-toolbar"><label className="search"><Search size={17}/><input aria-label="Tìm kiếm kỹ thuật" placeholder={tab==='maintenance'?'Tìm mã cổng, loại AC / DC…':'Tìm mã sự cố, cổng hoặc mô tả…'} value={query} onChange={e=>setQuery(e.target.value)}/></label>
        <label className="tech-filter">Trạng thái<select value={status} onChange={e=>setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{(tab==='maintenance'?['Rảnh','Đã đặt','Đang sạc','Bảo trì','Lỗi','Mất kết nối']:tab==='history'?['Đã xử lý','Đã đóng','Đã hủy']:['Đang xử lý','Chờ linh kiện','Đã giao','Quá hạn']).map(s=><option key={s}>{s}</option>)}</select></label>
        {tab!=='maintenance'&&<label className="tech-filter">Mức ưu tiên<select value={priority} onChange={e=>setPriority(e.target.value)}><option value="">Tất cả mức ưu tiên</option>{['Khẩn cấp','Cao','Bình thường','Thấp'].map(s=><option key={s}>{s}</option>)}</select></label>}
      </div>
      {tab==='maintenance'?<><p className="muted">{ports.length} cổng · Mở lại và xác nhận xử lý cần thiết bị phản hồi an toàn.</p><div className="tech-port-grid">{ports.map(c=><article className="tech-port panel" key={c.maCong}>
        <div className="tech-task-head"><span className="tech-port-icon"><Plug size={23}/></span><Badge value={c.trangThai}/></div><h3>{c.maCong}</h3><p className="muted">{c.loaiCong} · {c.congSuatDinhMuc??'—'} kW định mức</p>
        <dl><div><dt>Công suất được cấp</dt><dd>{c.congSuatDuocCap??'—'} kW</dd></div><div><dt>Kết nối xe</dt><dd>{c.coXe==null?'Chưa có dữ liệu':c.coXe?'Có xe':'Không có xe'}</dd></div><div><dt>Tín hiệu gần nhất</dt><dd>{c.heartbeatCuoi?dateLabel(c.heartbeatCuoi):'Chưa ghi nhận'}</dd></div></dl>
        {(['Rảnh','Đã đặt'].includes(c.trangThai)?[['khoa','Khóa bảo trì']]:c.trangThai==='Bảo trì'?[['mo','Mở lại cổng']]:c.trangThai==='Lỗi'?[['xac-nhan-xu-ly','Xác nhận xử lý an toàn']]:[]).map(([route,label])=><button key={route} className="secondary" disabled={busy} onClick={()=>{setError('');setCommand({port:c,route,label})}}><Wrench size={15}/>{label}</button>)}
        {['Đang sạc','Mất kết nối'].includes(c.trangThai)&&<p className="tech-port-note">{c.trangThai==='Đang sạc'?'Chờ kết thúc phiên sạc để bảo trì.':'Sau khi sửa kết nối, cổng chỉ cập nhật khi nhận lại heartbeat và trạng thái thực từ thiết bị. Kiểm tra device-sim trong môi trường demo.'}</p>}
      </article>)}</div>{!ports.length&&<div className="customer-empty">{loading?'Đang tải cổng sạc…':'Không tìm thấy cổng phù hợp.'}</div>}</>:<><p className="muted">{rows.length} công việc {tab==='history'?'đã xử lý / kết thúc':'được giao cho bạn'}</p><div className="tech-task-grid">{rows.map(taskCard)}</div>{!rows.length&&empty}</>}
    </>}
    {command&&<div className="overlay"><section className="modal" role="dialog" aria-modal="true" aria-label={command.label}><div className="modal-head"><h3>{command.label} · {command.port.maCong}</h3><button className="icon-btn" disabled={busy} onClick={()=>setCommand(null)} aria-label="Đóng"><X size={18}/></button></div><div className="modal-body form">
      <p>{command.route==='khoa'?'Cổng sẽ chuyển sang bảo trì và không nhận phiên sạc mới. Lịch đặt bị ảnh hưởng sẽ được hệ thống phân bổ lại.':command.route==='mo'?'Thiết bị phải đang kết nối và xác nhận an toàn trước khi cổng bảo trì được mở lại. Nếu thiết bị không phản hồi, cổng vẫn bảo trì; kiểm tra device-sim trong môi trường demo.':'Chỉ xác nhận sau khi đã kiểm tra và xử lý lỗi. Nếu thiết bị xác nhận an toàn, cổng sẽ hoạt động lại và các sự cố đang mở của cổng sẽ được đóng.'}</p>
      {error&&<div className="banner error" role="alert">{error}</div>}<div className="modal-actions"><button className="secondary" disabled={busy} onClick={()=>setCommand(null)}>Hủy</button><button className="primary" disabled={busy} onClick={execute}>{busy?'Đang thực hiện…':'Xác nhận thao tác'}</button></div></div></section></div>}
  </div>;
}
