import React, { useEffect, useRef, useState } from 'react';
import { Activity, ArrowUpRight, BatteryCharging, ClipboardList, Clock, Plug, Plus, Search, TriangleAlert, X } from 'lucide-react';

const portStates = ['Rảnh','Đã đặt','Đang sạc','Lỗi','Bảo trì','Mất kết nối'];
const sessionStates = ['Đã đặt','Chờ','Đang sạc','Kết thúc sạc','Chờ thanh toán','Hoàn tất','Hủy'];
const finished = s => ['Đã xử lý','Đã đóng','Đã hủy'].includes(s.trangThai);
const utc = value => new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : value+'Z');
const date = value => value ? utc(value).toLocaleString('vi-VN') : '—';
const number = value => value == null ? '—' : new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(value);
const overdue = s => !finished(s) && s.hanXuLy && utc(s.hanXuLy).getTime()<Date.now();
const tones = {'Rảnh':'#83DDBB','Đang sạc':'#FF7A45','Lỗi':'#FF5C7A','Khẩn cấp':'#FF5C7A','Mất kết nối':'#AAA4BC','Bảo trì':'#FFB547','Cao':'#FFB547','Đã xử lý':'#83DDBB','Hoàn tất':'#83DDBB'};
function Badge({value}) { return <span className="tech-badge" style={{'--tone':tones[value]||'#B39DFF'}}><i/>{value||'—'}</span> }

export default function OperationsWorkspace({api,tab,navigate,refresh}) {
  const [data,setData]=useState({ports:[],sessions:[],warnings:[],incidents:[],filtered:[]});
  const [readError,setReadError]=useState('');
  const [lastUpdate,setLastUpdate]=useState(null);
  const [loading,setLoading]=useState(true);
  const [revision,setRevision]=useState(0);
  const [query,setQuery]=useState('');
  const [filter,setFilter]=useState('');
  const [type,setType]=useState('');
  const [priority,setPriority]=useState('');
  const [dates,setDates]=useState('');
  const [modal,setModal]=useState(null);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);
  const [commands,setCommands]=useState({});
  const keys=useRef({});
  const saving=useRef(false);
  const writeVersion=useRef(0);
  const previousTab=useRef(tab);
  const shortcut=useRef(null);
  // Schedule the next read only after this one completes; abort obsolete reads on refresh/unmount.
  useEffect(()=>{
    const controller=new AbortController();let timer;
    async function read() {
      if(saving.current){timer=setTimeout(read,5000);return}
      const version=writeVersion.current;
      setLoading(true);
      const resources=[['ports','/api/cong','cổng sạc'],['sessions','/api/phien','phiên sạc'],['warnings','/api/canh-bao-chiem-cong','cảnh báo'],['incidents','/api/su-co','sự cố']];
      if(dates)resources.push(['filtered',`/api/phien?${dates}`,'phiên theo ngày']);
      const results=await Promise.allSettled(resources.map(async ([,url])=>{
        const rows=await api(url,{signal:controller.signal});
        if(!Array.isArray(rows))throw new Error('Dữ liệu trả về không hợp lệ.');
        return rows;
      }));
      if(controller.signal.aborted)return;
      if(version!==writeVersion.current||saving.current){setLoading(false);timer=setTimeout(read,5000);return}
      const next={},failures=[];
      results.forEach((result,i)=>{
        if(result.status==='fulfilled')next[resources[i][0]]=result.value;
        else failures.push(`${resources[i][2]}: ${result.reason.message}`);
      });
      setData(current=>({...current,...next}));
      setReadError(failures.length?`Không thể cập nhật ${failures.join('; ')} Dữ liệu cũ có thể đã thay đổi.`:'');
      if(!failures.length)setLastUpdate(new Date());
      setLoading(false);
      timer=setTimeout(read,5000);
    }
    read();
    return ()=>{controller.abort();clearTimeout(timer)};
  },[api,refresh,revision,dates]);

  useEffect(()=>{
    if(previousTab.current===tab)return;
    if(previousTab.current==='sessions')setDates('');
    previousTab.current=tab;
    const target=shortcut.current;
    setFilter(target?.tab===tab?target.filter:'');setQuery(target?.tab===tab?target.query:'');
    setType('');setPriority('');setModal(null);setError('');shortcut.current=null;
  },[tab]);

  function go(next,nextFilter='',nextQuery='') {
    shortcut.current={tab:next,filter:nextFilter,query:nextQuery};
    setFilter(nextFilter);setQuery(nextQuery);setModal(null);navigate(next);
  }
  function open(value) {setError('');setModal(value)}
  async function mutate(action) {
    if(saving.current)return;
    saving.current=true;writeVersion.current++;setBusy(true);setError('');setNotice('');
    try {await action()} catch(e){setError(e.message)} finally {writeVersion.current++;saving.current=false;setBusy(false)}
  }
  async function stop() {
    const id=modal.id;
    keys.current[id] ||= crypto.randomUUID();
    await mutate(async()=>{
      const command=await api(`/api/phien/${encodeURIComponent(id)}/dung`,{method:'POST',headers:{'Idempotency-Key':keys.current[id]}});
      setCommands(current=>({...current,[id]:command}));
      if(command.trangThai==='Thất bại') {
        delete keys.current[id];
        throw new Error(`Lệnh ${command.maLenh} thất bại. Kiểm tra thiết bị trước khi thử lại.`);
      }
      setNotice(command.trangThai==='Thành công'?`Lệnh ${command.maLenh} đã được thiết bị xác nhận. Đang cập nhật phiên.`:`Lệnh ${command.maLenh}: chờ thiết bị phản hồi. Phiên chưa được xác nhận đã dừng.`);
      setModal(null);setRevision(v=>v+1);
    });
  }

  const match = text => text.toLocaleLowerCase('vi-VN').includes(query.trim().toLocaleLowerCase('vi-VN'));
  const activeIncidents=data.incidents.filter(s=>!finished(s));
  const pendingWarnings=data.warnings.filter(w=>!w.phanHoi?.trim());
  const urgent=activeIncidents.filter(s=>s.mucUuTien==='Khẩn cấp');
  const attention=[...activeIncidents].sort((a,b)=>Number(b.mucUuTien==='Khẩn cấp')-Number(a.mucUuTien==='Khẩn cấp')||Number(!!overdue(b))-Number(!!overdue(a))||Number(!b.maKTV)-Number(!a.maKTV));
  const ports=data.ports.filter(c=>(!filter||(filter==='fault'?['Lỗi','Mất kết nối'].includes(c.trangThai):c.trangThai===filter))&&(!type||c.loaiCong===type)&&match(`${c.maCong} ${c.loaiCong}`));
  const sessions=(dates?data.filtered:data.sessions).filter(s=>(!filter||s.trangThai===filter)&&match(`${s.maPhien} ${s.maCong} ${s.maDatPhien||''}`));
  const warnings=data.warnings.filter(w=>(!filter||(filter==='pending'?!w.phanHoi?.trim():!!w.phanHoi?.trim()))&&match(`${w.maCanhBao} ${w.maPhien} ${w.phanHoi||''}`));
  const incidents=data.incidents.filter(s=>(!filter||(filter==='unassigned'?!s.maKTV&&!finished(s):filter==='overdue'?overdue(s):filter==='urgent'?!finished(s)&&s.mucUuTien==='Khẩn cấp':s.trangThai===filter))&&(!priority||s.mucUuTien===priority)&&match(`${s.maSuCo} ${s.maCong} ${s.loaiSuCo} ${s.moTa} ${s.maKTV||''}`));
  const currentPort=modal?.kind==='port'?data.ports.find(c=>c.maCong===modal.id):null;
  const currentIncident=modal?.kind==='incident'?data.incidents.find(s=>s.maSuCo===modal.id):null;
  const empty=<p className="customer-empty">{loading&&!lastUpdate?'Đang tải dữ liệu…':readError?'Chưa có dữ liệu phù hợp. Hãy thử làm mới.':'Không có mục phù hợp.'}</p>;
  const portGrid=items=><div className="ops-port-grid">{items.map(c=><button className="ops-port" key={c.maCong} style={{'--tone':tones[c.trangThai]||'#B39DFF'}} aria-label={`Chi tiết cổng ${c.maCong}`} onClick={()=>open({kind:'port',id:c.maCong})}><span className="ops-port-top"><Plug size={21}/><Badge value={c.trangThai}/></span><strong>{c.maCong}</strong><small>{c.loaiCong} · {number(c.congSuatDinhMuc)} kW</small><span className="ops-port-bottom">{c.coXe==null?'Chưa rõ tình trạng xe':c.coXe?'Có xe tại cổng':'Không có xe'}<ArrowUpRight size={16}/></span></button>)}</div>;
  const incidentCard=s=><article className="tech-task" key={s.maSuCo}>
    <div className="tech-task-head"><span className="tech-code">{s.maSuCo} · {s.maCong}</span><Badge value={s.mucUuTien}/></div>
    <h3>{s.loaiSuCo}</h3><p className="tech-description">{s.moTa}</p>
    <p className={`tech-deadline ${overdue(s)?'late':''}`}><Clock size={14}/>{overdue(s)?'Quá hạn · ':''}{s.hanXuLy?date(s.hanXuLy):'Chưa đặt hạn xử lý'}</p>
    <p className="ops-assignee">Kỹ thuật viên: {s.maKTV||'Chưa phân công'}</p>
    <div className="tech-task-foot"><Badge value={s.trangThai}/><button className="text-btn" aria-label={`Chi tiết sự cố ${s.maSuCo}`} onClick={()=>open({kind:'incident',id:s.maSuCo})}>Chi tiết <ArrowUpRight size={15}/></button></div>
  </article>;

  return <div className="ops-workspace">
    <div className="ops-update"><span><Activity size={14}/>{loading?'Đang cập nhật…':'Tự cập nhật mỗi 5 giây'}</span><span>{lastUpdate?`Cập nhật đầy đủ: ${lastUpdate.toLocaleTimeString('vi-VN')}`:'Chưa có lần cập nhật đầy đủ'}</span></div>
    {readError&&<div className="banner error" role="alert">{readError}<button onClick={()=>setRevision(v=>v+1)}>Thử lại</button></div>}
    {notice&&<div className="banner success" role="status">{notice}<button aria-label="Ẩn thông báo" onClick={()=>setNotice('')}><X size={16}/></button></div>}
    {error&&!modal&&<div className="banner error" role="alert">{error}</div>}

    {tab==='overview'&&<>
      <section className="ops-hero"><div><span className="eyebrow">KHÔNG GIAN NHÂN VIÊN TRỰC</span><h1>Nắm tình hình, xử lý kịp thời.</h1><p>Theo dõi cổng sạc, hỗ trợ phiên và điều phối sự cố tại trạm.</p><button className="primary" onClick={()=>open({kind:'create'})}><Plus size={16}/>Báo sự cố</button></div><div className="ops-hero-signal" aria-hidden="true"><Activity size={70}/><span>VẬN HÀNH TRẠM SẠC</span></div></section>
      <div className="ops-stats">{[
        ['Cổng rảnh',data.ports.filter(c=>c.trangThai==='Rảnh').length,Plug,'ports','Rảnh'],
        ['Cổng đang sạc',data.ports.filter(c=>c.trangThai==='Đang sạc').length,BatteryCharging,'ports','Đang sạc'],
        ['Cổng lỗi / mất kết nối',data.ports.filter(c=>['Lỗi','Mất kết nối'].includes(c.trangThai)).length,TriangleAlert,'ports','fault'],
        ['Sự cố khẩn cấp',urgent.length,ClipboardList,'incidents','urgent'],
        ['Cảnh báo chưa phản hồi',pendingWarnings.length,Clock,'warnings','pending'],
      ].map(([label,count,Icon,page,state])=><button key={label} className="ops-stat" onClick={()=>go(page,state)}><Icon size={19}/><strong>{!lastUpdate&&loading?'—':count}</strong><span>{label}</span><ArrowUpRight size={15}/></button>)}</div>
      <div className="ops-columns"><section><div className="panel-head"><div><h3>Cần xử lý ngay</h3><p>Ưu tiên khẩn cấp, quá hạn và chưa phân công</p></div><span className="nav-count">{activeIncidents.length+pendingWarnings.length}</span></div><div className="ops-attention">
        {!!pendingWarnings.length&&<button className="ops-warning-callout" onClick={()=>go('warnings','pending')}><TriangleAlert size={22}/><span><b>{pendingWarnings.length} cảnh báo chưa phản hồi</b><small>Kiểm tra xe còn chiếm cổng sau khi sạc</small></span><ArrowUpRight size={18}/></button>}
        {attention.slice(0,4).map(incidentCard)}{!attention.length&&!pendingWarnings.length&&empty}
        <button className="text-btn" onClick={()=>go('incidents')}>Xem tất cả sự cố <ArrowUpRight size={15}/></button>
      </div></section><section><div className="panel-head"><div><h3>Bãi sạc</h3><p>{data.ports.length} cổng · Chọn cổng để xem chi tiết</p></div></div>{data.ports.length?portGrid(data.ports):empty}</section></div>
    </>}

    {tab==='ports'&&<><div className="tech-toolbar"><SearchBox label="Tìm cổng sạc" value={query} change={setQuery}/><Filter label="Trạng thái cổng" value={filter} change={setFilter} options={portStates} extra={[['fault','Lỗi / mất kết nối']]}/><Filter label="Loại cổng" value={type} change={setType} options={['AC','DC']}/></div><p className="muted">{ports.length} cổng phù hợp</p>{ports.length?portGrid(ports):empty}</>}

    {tab==='sessions'&&<>
      <div className="tech-toolbar"><SearchBox label="Tìm phiên sạc" value={query} change={setQuery}/><Filter label="Trạng thái phiên" value={filter} change={setFilter} options={sessionStates}/></div>
      <form className="tech-toolbar" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);const from=f.get('tuNgay'),to=f.get('denNgay');if(from&&to&&from>to){setError('Đến ngày không được trước từ ngày.');return}setError('');const p=new URLSearchParams();if(from)p.set('tuNgay',from);if(to)p.set('denNgay',to);setDates(p.toString())}}>
        <label className="tech-filter">Từ ngày (UTC)<input type="date" name="tuNgay"/></label><label className="tech-filter">Đến ngày (UTC)<input type="date" name="denNgay"/></label><button className="secondary">Áp dụng ngày</button><button type="reset" className="text-btn" onClick={()=>{setDates('');setError('')}}>Xóa lọc ngày</button>
      </form><p className="muted">{sessions.length} phiên sạc</p>
      <section className="panel table-panel"><div className="table-wrap"><table><thead><tr><th>Phiên / cổng</th><th>Trạng thái</th><th>Bắt đầu sạc</th><th>Điện năng</th><th>Phí sạc</th><th>Thao tác</th></tr></thead><tbody>{sessions.map(s=><tr key={s.maPhien}><td><b>{s.maPhien}</b><small className="ops-cell-sub">{s.maCong} · {s.maDatPhien||'—'}</small></td><td><Badge value={s.trangThai}/></td><td>{date(s.batDauLuc)}</td><td>{number(s.phiTamTinh?.dienNangUocLuongKWh??s.dienNangKWh)} kWh{s.phiTamTinh&&<small className="ops-cell-sub">Ước lượng</small>}</td><td>{number(s.phiTamTinh?.phiTamTinh??s.tongPhi)} ₫<small className="ops-cell-sub">{s.phiTamTinh?'Tạm tính':s.tongPhi!=null?'Chính thức':'Chưa có phí'}</small></td><td>{s.trangThai==='Đang sạc'?<><button className="secondary" aria-label={`Dừng phiên ${s.maPhien}`} onClick={()=>open({kind:'stop',id:s.maPhien})}>{commands[s.maPhien]?.trangThai==='Chờ ack'?'Kiểm tra lệnh dừng':'Dừng phiên'}</button>{commands[s.maPhien]?.trangThai==='Chờ ack'&&<small className="ops-cell-sub">Chờ thiết bị xác nhận</small>}</>:<span className="muted">—</span>}</td></tr>)}</tbody></table></div>{!sessions.length&&empty}</section>
    </>}

    {tab==='warnings'&&<>
      <div className="tech-toolbar"><SearchBox label="Tìm cảnh báo" value={query} change={setQuery}/><Filter label="Phản hồi" value={filter} change={setFilter} options={[]} extra={[['pending','Chưa phản hồi'],['responded','Đã phản hồi']]}/></div>
      <p className="ops-explanation">Phản hồi không xác nhận xe đã rời cổng. Tình trạng xe được cập nhật từ thiết bị; lịch sử cảnh báo vẫn được giữ lại.</p>
      <div className="tech-task-grid">{warnings.map(w=>{const s=data.sessions.find(s=>s.maPhien===w.maPhien);const c=data.ports.find(c=>c.maCong===s?.maCong);return <article className="tech-task" key={w.maCanhBao}><div className="tech-task-head"><b>{w.maCanhBao}</b><Badge value={w.phanHoi?.trim()?'Đã phản hồi':'Chưa phản hồi'}/></div><h3>{s?.maCong||'Chưa rõ cổng'} · {w.maPhien}</h3><p className="muted">Lần {w.lanCanhBao} · Gửi {w.nguoiNhan}</p><p className="tech-deadline">{date(w.thoiDiem)}</p><p className="ops-assignee">Thiết bị hiện tại: {c?.coXe==null?'Chưa có dữ liệu':c.coXe?'Có xe tại cổng':'Không có xe tại cổng'}</p>{w.phanHoi&&<p className="ops-note">{w.phanHoi}</p>}<div className="tech-task-foot"><button className="text-btn" onClick={()=>{setDates('');go('sessions','',w.maPhien)}}>Xem phiên</button><button className="secondary" aria-label={`Phản hồi ${w.maCanhBao}`} onClick={()=>open({kind:'warning',id:w.maCanhBao})}>{w.phanHoi?'Sửa phản hồi':'Phản hồi'}</button></div></article>})}</div>{!warnings.length&&empty}
    </>}

    {tab==='incidents'&&<>
      <div className="tech-toolbar"><SearchBox label="Tìm sự cố" value={query} change={setQuery}/><Filter label="Trạng thái sự cố" value={filter} change={setFilter} options={['Mới','Đang xử lý','Chờ linh kiện','Đã xử lý','Đã đóng','Đã hủy']} extra={[['unassigned','Chưa phân công'],['urgent','Khẩn cấp đang mở'],['overdue','Quá hạn']]}/><Filter label="Mức ưu tiên" value={priority} change={setPriority} options={['Khẩn cấp','Cao','Bình thường','Thấp']}/><button className="primary" onClick={()=>open({kind:'create'})}><Plus size={16}/>Báo sự cố</button></div>
      <p className="muted">{incidents.length} sự cố</p><div className="tech-task-grid">{incidents.map(incidentCard)}</div>{!incidents.length&&empty}
    </>}

    {modal&&<Dialog title={{port:'Chi tiết cổng sạc',stop:'Xác nhận dừng phiên',warning:'Phản hồi cảnh báo',create:'Báo cáo sự cố',incident:'Chi tiết sự cố'}[modal.kind]} busy={busy} close={()=>setModal(null)}>
      {error&&<div className="banner error" role="alert">{error}</div>}
      {currentPort&&<><h3>{currentPort.maCong}</h3><Badge value={currentPort.trangThai}/><dl className="ops-detail"><dt>Loại cổng</dt><dd>{currentPort.loaiCong}</dd><dt>Công suất định mức / được cấp</dt><dd>{number(currentPort.congSuatDinhMuc)} / {number(currentPort.congSuatDuocCap)} kW</dd><dt>Tín hiệu gần nhất</dt><dd>{date(currentPort.heartbeatCuoi)}</dd><dt>Tình trạng xe</dt><dd>{currentPort.coXe==null?'Chưa có dữ liệu':currentPort.coXe?'Có xe':'Không có xe'}</dd></dl><h4>Phiên liên quan</h4>{data.sessions.filter(s=>s.maCong===currentPort.maCong).slice(0,5).map(s=><button className="ops-linked" key={s.maPhien} onClick={()=>{setDates('');go('sessions','',s.maPhien)}}>{s.maPhien}<Badge value={s.trangThai}/><ArrowUpRight size={15}/></button>)}{!data.sessions.some(s=>s.maCong===currentPort.maCong)&&<p className="muted">Chưa có phiên liên quan.</p>}<button className="primary" onClick={()=>open({kind:'create',port:currentPort.maCong})}>Báo sự cố tại cổng</button></>}
      {modal.kind==='stop'&&<><p>Dừng phiên <b>{modal.id}</b>. Hệ thống gửi lệnh đến thiết bị và cập nhật trạng thái sau khi nhận phản hồi.</p>{commands[modal.id]?.trangThai==='Chờ ack'&&<p className="muted">Kiểm tra lại dùng cùng mã yêu cầu, không tạo thêm lệnh dừng.</p>}<div className="modal-actions"><button className="secondary" disabled={busy} onClick={()=>setModal(null)}>Hủy</button><button className="primary" disabled={busy} onClick={stop}>{busy?'Đang gửi…':'Xác nhận dừng'}</button></div></>}
      {modal.kind==='warning'&&<form className="form" onSubmit={e=>{e.preventDefault();const phanHoi=new FormData(e.currentTarget).get('phanHoi').trim();if(!phanHoi){setError('Vui lòng nhập nội dung phản hồi.');return}mutate(async()=>{const updated=await api(`/api/canh-bao-chiem-cong/${encodeURIComponent(modal.id)}`,{method:'PUT',body:JSON.stringify({phanHoi})});setData(d=>({...d,warnings:d.warnings.map(w=>w.maCanhBao===modal.id?updated:w)}));setNotice('Đã ghi nhận phản hồi cảnh báo.');setModal(null)})}}><label>Nội dung phản hồi<textarea name="phanHoi" required maxLength={255} rows={4} defaultValue={data.warnings.find(w=>w.maCanhBao===modal.id)?.phanHoi||''}/></label><p className="muted">Ghi lại việc đã liên hệ hoặc kiểm tra thực tế. Phản hồi sẽ dừng các lần nhắc tiếp theo của phiên.</p><button className="primary" disabled={busy}>{busy?'Đang lưu…':'Lưu phản hồi'}</button></form>}
      {modal.kind==='create'&&<form className="form" onSubmit={e=>{e.preventDefault();const body=Object.fromEntries(new FormData(e.currentTarget));body.loaiSuCo=body.loaiSuCo.trim();body.moTa=body.moTa.trim();if(!body.loaiSuCo||!body.moTa){setError('Loại sự cố và mô tả không được để trống.');return}mutate(async()=>{await api('/api/su-co',{method:'POST',body:JSON.stringify(body)});setNotice('Đã báo sự cố và đưa cổng ra khỏi hoạt động.');setModal(null);setRevision(v=>v+1)})}}><label>Cổng sạc<select name="maCong" required defaultValue={modal.port||''}><option value="" disabled>Chọn cổng sạc</option>{data.ports.map(c=><option key={c.maCong}>{c.maCong}</option>)}</select></label><label>Loại sự cố<input name="loaiSuCo" required maxLength={30}/></label><label>Mức ưu tiên<select name="mucUuTien">{['Bình thường','Thấp','Cao','Khẩn cấp'].map(p=><option key={p}>{p}</option>)}</select></label><label>Mô tả<textarea name="moTa" required rows={4} maxLength={500}/></label><button className="primary" disabled={busy||!data.ports.length}>{busy?'Đang gửi…':'Gửi báo cáo'}</button></form>}
      {currentIncident&&<><div className="tech-task-head"><Badge value={currentIncident.trangThai}/><Badge value={currentIncident.mucUuTien}/></div><h3>{currentIncident.loaiSuCo}</h3><p className="ops-note">{currentIncident.moTa}</p><dl className="ops-detail"><dt>Mã sự cố / cổng</dt><dd>{currentIncident.maSuCo} / {currentIncident.maCong}</dd><dt>Kỹ thuật viên</dt><dd>{currentIncident.maKTV||'Chưa phân công'}</dd><dt>Hạn xử lý</dt><dd>{date(currentIncident.hanXuLy)}</dd></dl>
        {!['Đã đóng','Đã hủy'].includes(currentIncident.trangThai)&&<form className="form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);const maKTV=f.get('maKTV').trim();const deadline=f.get('hanXuLy');if(!maKTV){setError('Vui lòng nhập mã kỹ thuật viên.');return}if(deadline&&new Date(deadline)<=new Date()){setError('Hạn xử lý phải ở tương lai.');return}mutate(async()=>{const updated=await api(`/api/su-co/${encodeURIComponent(currentIncident.maSuCo)}/giao-viec`,{method:'PUT',body:JSON.stringify({maKTV,hanXuLy:deadline?new Date(deadline).toISOString().slice(0,-1):null})});setData(d=>({...d,incidents:d.incidents.map(s=>s.maSuCo===updated.maSuCo?updated:s)}));setNotice('Đã giao kỹ thuật viên.');})}}><h4>Giao kỹ thuật viên</h4><label>Mã kỹ thuật viên<input name="maKTV" required maxLength={20} defaultValue={currentIncident.maKTV||''}/></label><label>Hạn xử lý<input name="hanXuLy" type="datetime-local" defaultValue={currentIncident.hanXuLy?new Date(utc(currentIncident.hanXuLy).getTime()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16):''}/></label><button className="secondary" disabled={busy}>{busy?'Đang giao…':'Giao việc'}</button></form>}
        <div className="tech-resolution"><p><b>Nguyên nhân:</b> {currentIncident.nguyenNhan||'Chưa ghi nhận'}</p><p><b>Cách xử lý:</b> {currentIncident.cachXuLy||'Chưa ghi nhận'}</p></div><h4>Lịch sử xử lý</h4><IncidentHistory api={api} id={currentIncident.maSuCo} revision={currentIncident}/>
      </>}
    </Dialog>}
  </div>;
}

function SearchBox({label,value,change}) {return <label className="search"><Search size={17}/><input aria-label={label} placeholder={label} value={value} onChange={e=>change(e.target.value)}/></label>}
function Filter({label,value,change,options,extra=[]}) {return <label className="tech-filter">{label}<select value={value} onChange={e=>change(e.target.value)}><option value="">Tất cả</option>{extra.map(([v,l])=><option key={v} value={v}>{l}</option>)}{options.map(v=><option key={v}>{v}</option>)}</select></label>}

function Dialog({title,close,busy,children}) {
  const ref=useRef(null);
  useEffect(()=>{const previous=document.activeElement;ref.current?.focus();return ()=>previous?.focus()},[]);
  function keyboard(e) {
    if(e.key==='Escape'&&!busy){e.preventDefault();close()}
    if(e.key!=='Tab')return;
    const elements=[...ref.current.querySelectorAll('button,input,select,textarea,[tabindex="0"]')].filter(el=>!el.disabled);
    const first=elements[0],last=elements.at(-1);
    if(!first){e.preventDefault();return}
    if(e.shiftKey&&(document.activeElement===first||document.activeElement===ref.current)){e.preventDefault();last.focus()}
    else if(!e.shiftKey&&(document.activeElement===last||document.activeElement===ref.current)){e.preventDefault();first.focus()}
  }
  return <div className="overlay"><section ref={ref} tabIndex={-1} className="modal" role="dialog" aria-modal="true" aria-label={title} onKeyDown={keyboard}><div className="modal-head"><h3>{title}</h3><button className="icon-btn" aria-label="Đóng" disabled={busy} onClick={close}><X size={18}/></button></div><div className="modal-body ops-dialog">{children}</div></section></div>;
}

function IncidentHistory({api,id,revision}) {
  const [rows,setRows]=useState(null),[error,setError]=useState('');
  useEffect(()=>{const controller=new AbortController();setError('');setRows(null);api(`/api/su-co/${encodeURIComponent(id)}/lich-su`,{signal:controller.signal}).then(rows=>{if(!controller.signal.aborted)setRows(rows)}).catch(e=>{if(!controller.signal.aborted)setError(e.message)});return ()=>controller.abort()},[api,id,revision]);
  if(error)return <p role="alert" className="banner error">Không thể tải lịch sử: {error}</p>;
  if(!rows)return <p className="muted">Đang tải lịch sử…</p>;
  return rows.length?<div className="timeline">{rows.map(r=><div className="tl-item" key={r.maLSSC}><span className="tl-dot"/><div className="tl-body"><b>{r.trangThai}</b><small>{r.ghiChu} · {r.nguoiCapNhat} · {date(r.thoiDiem)}</small></div></div>)}</div>:<p className="muted">Chưa có lịch sử.</p>;
}
