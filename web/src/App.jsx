import React, { useEffect, useState } from 'react';
import {
  Activity, Banknote, BarChart3, BatteryCharging, CalendarClock, ChevronDown, ChevronsUp,
  CircleCheck, CircleDot, CircleX, ClipboardList, Clock, FlaskConical, Gauge, Loader, Lock, LogOut,
  Minus, PackageSearch, Plug, Plus, RefreshCw, Search, Send, ShieldAlert, TriangleAlert, UserRound,
  WifiOff, Wrench, X, Zap, LayoutDashboard
} from 'lucide-react';
import './style.css';
import UserWorkspace from './UserWorkspace.jsx';
import TechnicianWorkspace from './TechnicianWorkspace.jsx';
import OperationsWorkspace from './OperationsWorkspace.jsx';

const roles = { 'Quản trị':'admin', 'Nhân viên trực':'ops', 'Kỹ thuật viên':'tech', 'Người dùng':'user' };
const api = async (path, options={}) => {
  const token = localStorage.getItem('token');
  const response = await fetch(path, { ...options, headers: { ...(options.body ? {'Content-Type':'application/json'} : {}), ...(token ? {Authorization:`Bearer ${token}`} : {}), ...options.headers } });
  const fresh = response.headers.get('X-New-Token'); if (fresh) localStorage.setItem('token', fresh);
  const data = response.status===204 ? null : await response.json().catch(()=>null);
  if (!response.ok) throw new Error(data?.thongBao || data?.message || 'Không thể tải dữ liệu.'); return data;
};
const fmt = v => v == null ? '—' : new Intl.NumberFormat('vi-VN').format(v);
const num = v => new Intl.NumberFormat('vi-VN',{maximumFractionDigits:1}).format(Number(v)||0);

/* Màu theo trạng thái: mỗi trạng thái một màu và một biểu tượng riêng */
const CHARGER = {
  'Rảnh':{color:'#60C3FF',icon:Plug},
  'Đã đặt':{color:'#B39DFF',icon:CalendarClock},
  'Đang sạc':{color:'#FF7A45',icon:BatteryCharging},
  'Lỗi':{color:'#FF5C7A',icon:TriangleAlert},
  'Bảo trì':{color:'#FFB547',icon:Wrench},
  'Mất kết nối':{color:'#6E6A84',icon:WifiOff},
};
const INCIDENT = {
  'Mới':{color:'#FF7A45',icon:CircleDot},
  'Đã giao':{color:'#B39DFF',icon:Send},
  'Đang xử lý':{color:'#60C3FF',icon:Loader},
  'Chờ linh kiện':{color:'#FFB547',icon:PackageSearch},
  'Đã xử lý':{color:'#E2E0EE',icon:CircleCheck},
  'Đã đóng':{color:'#6E6A84',icon:Lock},
  'Đã hủy':{color:'#FF5C7A',icon:CircleX},
};
const PRIORITY = {
  'Khẩn cấp':{color:'#FF5C7A',icon:ShieldAlert},
  'Cao':{color:'#FF7A45',icon:ChevronsUp},
  'Trung bình':{color:'#FFB547',icon:Minus},
  'Thấp':{color:'#60C3FF',icon:ChevronDown},
  'Bình thường':{color:'#8F8BA8',icon:Minus},
};
const ISSUE_ICON = { 'An toàn điện':ShieldAlert, 'Mất kết nối':WifiOff, 'Hỏng phần cứng':Wrench };
const FALLBACK = { color:'#8F8BA8', icon:CircleDot };
const CLOSED = ['Đã đóng','Đã hủy'];
const ACTIVE = ['Rảnh','Đã đặt','Đang sạc'];
const FILTERS = [
  { id:'all', label:'Tất cả', test:()=>true },
  { id:'open', label:'Đang mở', test:s=>!CLOSED.includes(s.trangThai) },
  { id:'urgent', label:'Khẩn cấp', test:s=>s.mucUuTien==='Khẩn cấp' },
  { id:'closed', label:'Đã đóng', test:s=>CLOSED.includes(s.trangThai) },
];
const cfgOf = v => CHARGER[v] || INCIDENT[v] || FALLBACK;

export default function App(){
  const [session,setSession]=useState(()=>localStorage.getItem('token')?{role:localStorage.getItem('role'),name:localStorage.getItem('name')}:null);
  const [tab,setTab]=useState('overview');
  const [incidents,setIncidents]=useState([]);
  const [chargers,setChargers]=useState([]);
  const [report,setReport]=useState(null);
  const [query,setQuery]=useState('');
  const [filter,setFilter]=useState('all');
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(false);
  const [savingIncident,setSavingIncident]=useState(false);
  const [showCreate,setShowCreate]=useState(false);
  const [selected,setSelected]=useState(null);
  const [period,setPeriod]=useState(30);
  const [login,setLogin]=useState({dinhDanh:'',matKhau:''});
  const [refresh,setRefresh]=useState(0);
  const role=roles[session?.role]||'user';
  const isDuty=role==='ops';
  const isOps=['admin','ops'].includes(role), isTech=role==='tech', isUser=role==='user';

  async function load(){
    if(!session||isDuty) return; setError('');setLoading(true);
    try{
      const [s,c]=await Promise.all([api('/api/su-co'),api(isOps||isTech?'/api/cong':'/api/cong/tom-tat')]);
      setIncidents(s); setChargers(c);
      if(role==='admin') setReport(await api(`/api/bao-cao?tuNgay=${new Date(Date.now()-period*864e5).toISOString().slice(0,10)}&denNgay=${new Date().toISOString().slice(0,10)}`));
    }catch(e){ setError(e.message) }finally{setLoading(false)}
  }
  useEffect(()=>{load()},[session,period]);

  async function signIn(e){e.preventDefault();setBusy(true);setError('');try{const r=await api('/api/auth/dang-nhap',{method:'POST',body:JSON.stringify(login)});localStorage.setItem('token',r.token);localStorage.setItem('role',r.vaiTro);localStorage.setItem('name',r.maND);setSession({role:r.vaiTro,name:r.maND})}catch(e){setError(e.message)}finally{setBusy(false)}}
  function signOut(){['token','role','name'].forEach(k=>localStorage.removeItem(k));setSession(null);setTab('overview');setSelected(null);setShowCreate(false);setError('');setNotice('');setIncidents([]);setChargers([]);setReport(null)}
  async function createIncident(e){e.preventDefault();const f=new FormData(e.currentTarget);setBusy(true);try{await api('/api/su-co',{method:'POST',body:JSON.stringify(Object.fromEntries(f))});setNotice('Đã gửi báo cáo sự cố.');setShowCreate(false);load()}catch(e){setError(e.message)}finally{setBusy(false)}}
  async function updateIncident(id,route,body){if(savingIncident)return;setSavingIncident(true);setError('');try{const updated=await api(`/api/su-co/${id}/${route}`,{method:'PUT',body:JSON.stringify(body)});setNotice('Đã cập nhật sự cố.');setIncidents(rows=>rows.map(s=>s.maSuCo===id?updated:s));setSelected({...updated, revision: Date.now()})}catch(e){setError(e.message)}finally{setSavingIncident(false)}}

  if(!session) return <div className="login">
    <aside className="login-aside">
      <div className="brand"><span className="brand-mark"><Zap size={19}/></span><span className="brand-name">Trạm sạc</span></div>
      <div>
        <h2>Sạc xe thuận tiện, chủ động mỗi hành trình.</h2>
        <p>Đặt lịch, theo dõi phiên sạc và thanh toán. Không gian riêng phù hợp với từng vai trò.</p>
      </div>
      <div className="art" aria-hidden="true">
        {[['Rảnh',Plug],['Đang sạc',BatteryCharging],['Đã đặt',CalendarClock],['Bảo trì',Wrench],['Đang sạc',BatteryCharging],['Rảnh',Plug],['Lỗi',TriangleAlert],['Rảnh',Plug]].map(([name,Icon],i)=>{const c=cfgOf(name);return <div className="art-cell" key={i} style={{'--tone':c.color}}><span className="ic-box"><Icon size={18}/></span><small>{name}</small></div>})}
      </div>
    </aside>
    <div className="login-panel">
      <div className="login-card">
        <div className="brand brand-plain"><span className="brand-mark"><Zap size={19}/></span><span className="brand-name">Trạm sạc</span></div>
        <h1>Đăng nhập</h1>
        <p className="muted">Đăng nhập để sử dụng dịch vụ trạm sạc.</p>
        <form onSubmit={signIn}>
          <label>Email hoặc số điện thoại<input required autoFocus value={login.dinhDanh} onChange={e=>setLogin({...login,dinhDanh:e.target.value})}/></label>
          <label>Mật khẩu<input type="password" required value={login.matKhau} onChange={e=>setLogin({...login,matKhau:e.target.value})}/></label>
          {error&&<div className="banner error">{error}</div>}
          <button className="primary full" disabled={busy}>{busy?'Đang đăng nhập…':'Đăng nhập'}</button>
        </form>
        <p className="login-foot">Tài khoản người dùng demo<br/><code>user@tramsac.local</code> · <code>Demo1234</code></p>
        <p className="sim-note"><FlaskConical size={14}/> Chế độ giả lập, không xử lý thanh toán thật</p>
      </div>
    </div>
  </div>;

  const open=incidents.filter(s=>!CLOSED.includes(s.trangThai));
  const activeCount=chargers.filter(c=>ACTIVE.includes(c.trangThai)).length;
  const acCount=chargers.filter(c=>c.loaiCong==='AC').length, dcCount=chargers.filter(c=>c.loaiCong==='DC').length;
  const urgentOpen=open.filter(s=>s.mucUuTien==='Khẩn cấp').length;
  const inProgress=incidents.filter(s=>s.trangThai==='Đang xử lý').length;
  const statusRows=Object.keys(CHARGER).map(name=>({name,value:chargers.filter(c=>c.trangThai===name).length,...CHARGER[name]}));
  const current=FILTERS.find(f=>f.id===filter)||FILTERS[0];
  const visible=incidents.filter(s=>current.test(s)&&`${s.maSuCo} ${s.maCong} ${s.loaiSuCo} ${s.moTa}`.toLowerCase().includes(query.toLowerCase()));
  const today=new Date().toLocaleDateString('vi-VN',{weekday:'long',day:'numeric',month:'long',year:'numeric'});
  const pageTitle=isDuty?{
    overview:['Tổng quan vận hành','Theo dõi trạm và ưu tiên việc cần xử lý'],
    ports:['Giám sát cổng sạc','Trạng thái thiết bị và phiên sạc liên quan'],
    sessions:['Quản lý phiên sạc','Tra cứu và hỗ trợ dừng phiên sạc'],
    warnings:['Cảnh báo chiếm cổng','Theo dõi cảnh báo và ghi nhận phản hồi'],
    incidents:['Điều phối sự cố','Báo sự cố, giao việc và theo dõi tiến độ'],
  }[tab]:{
    overview:isTech?['Tổng quan công việc','Theo dõi công việc được giao và tình trạng thiết bị']:isUser?['Trang chủ','Đặt lịch, sạc xe và quản lý hành trình của bạn']:['Tổng quan','Theo dõi bãi sạc theo thời gian thực'],
    bookings:['Đặt lịch sạc','Chọn xe và thời gian phù hợp với bạn'],
    sessions:['Phiên sạc của tôi','Bắt đầu, dừng và theo dõi chi phí sạc'],
    payments:['Thanh toán','Hóa đơn và giao dịch của bạn · Chế độ giả lập'],
    profile:['Hồ sơ & xe','Thông tin cá nhân và phương tiện của bạn'],
    maintenance:['Cổng sạc & bảo trì','Kiểm tra thiết bị, bảo trì và xác nhận an toàn'],
    history:['Lịch sử xử lý','Tra cứu công việc đã xử lý và nhật ký cập nhật'],
    incidents:isTech?['Công việc của tôi','Ưu tiên, thời hạn và tiến độ xử lý sự cố']:[isUser?'Hỗ trợ':'Quản lý sự cố', isUser?'Gửi sự cố và theo dõi tiến độ hỗ trợ':'Phân loại, giao việc và theo dõi xử lý'],
    reports:['Báo cáo vận hành','Số liệu tổng hợp theo khoảng thời gian'],
  }[tab];
  const rep=report?.phien||{};
  const completionRate=rep.tong_phien?(rep.phien_hoan_tat||0)/rep.tong_phien:0;

  return <div className={`shell ${isUser?'user-shell':''} ${isTech?'tech-shell':''} ${isDuty?'ops-shell':''}`}>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark"><Zap size={19}/></span><span className="brand-name">Trạm sạc</span></div>
      <nav className="nav-list" aria-label="Điều hướng chính">
        {isDuty&&[['overview','Tổng quan vận hành',LayoutDashboard],['ports','Giám sát cổng sạc',Plug],['sessions','Quản lý phiên sạc',BatteryCharging],['warnings','Cảnh báo chiếm cổng',TriangleAlert],['incidents','Điều phối sự cố',ClipboardList]].map(([id,label,Icon])=><button key={id} title={label} className={`nav ${tab===id?'active':''}`} aria-current={tab===id?'page':undefined} onClick={()=>setTab(id)}><Icon size={18}/><span>{label}</span></button>)}
        {!isDuty&&<>
        <button title={isUser?'Trang chủ':'Tổng quan'} className={`nav ${tab==='overview'?'active':''}`} onClick={()=>setTab('overview')}><LayoutDashboard size={18}/><span>{isUser?'Trang chủ':'Tổng quan'}</span></button>
        {isUser&&[['bookings','Đặt lịch sạc',CalendarClock],['sessions','Phiên sạc của tôi',BatteryCharging],['payments','Thanh toán',Banknote],['profile','Hồ sơ & xe',UserRound]].map(([id,label,Icon])=><button key={id} title={label} className={`nav ${tab===id?'active':''}`} onClick={()=>setTab(id)}><Icon size={18}/><span>{label}</span></button>)}
        <button title={isTech?'Công việc của tôi':isUser?'Hỗ trợ':'Quản lý sự cố'} className={`nav ${tab==='incidents'?'active':''}`} onClick={()=>setTab('incidents')}><ClipboardList size={18}/><span>{isTech?'Công việc của tôi':isUser?'Hỗ trợ':'Quản lý sự cố'}</span>{!isUser&&<em className="nav-count">{isTech?incidents.filter(s=>s.maKTV===session.name&&!['Đã xử lý','Đã đóng','Đã hủy'].includes(s.trangThai)).length:open.length}</em>}</button>
        {isTech&&[['maintenance','Cổng sạc & bảo trì',Wrench],['history','Lịch sử xử lý',Clock]].map(([id,label,Icon])=><button key={id} title={label} className={`nav ${tab===id?'active':''}`} onClick={()=>setTab(id)}><Icon size={18}/><span>{label}</span></button>)}
        {role==='admin'&&<button className={`nav ${tab==='reports'?'active':''}`} onClick={()=>setTab('reports')}><BarChart3 size={18}/><span>Báo cáo</span></button>}
        </>}
      </nav>
      <div className="sidebar-bottom">
        <div className="user-card">
          <div className="avatar"><UserRound size={17}/></div>
          <div className="user-info"><b>{session.name}</b><small>{session.role}</small></div>
          <button className="icon-btn" title="Đăng xuất" onClick={signOut}><LogOut size={16}/></button>
        </div>
      </div>
    </aside>

    <main className="main">
      <header className="topbar">
        <div>
          <h2>{pageTitle[0]}</h2>
          <p className="muted">{pageTitle[1]}</p>
        </div>
        <div className="top-actions">
          <span className="sim-badge"><FlaskConical size={14}/>Chế độ giả lập</span>
          {!isDuty&&<span className="live-chip"><i/>{isUser?'Phiên sạc tự cập nhật':'Cập nhật thủ công'}</span>}
          <button className="icon-btn bordered" onClick={()=>{load();setRefresh(v=>v+1)}} title="Làm mới"><RefreshCw size={16}/></button>
        </div>
      </header>

      <div className="content">
        {error&&<div className="banner error">{error}<button onClick={()=>setError('')} aria-label="Đóng thông báo"><X size={16}/></button></div>}
        {notice&&<div className="banner success">{notice}<button onClick={()=>setNotice('')} aria-label="Đóng thông báo"><X size={16}/></button></div>}

        {isUser&&<UserWorkspace api={api} tab={tab} navigate={setTab} refresh={refresh} chargers={chargers} account={session.name}/>}
        {isTech&&<TechnicianWorkspace api={api} tab={tab} navigate={setTab} incidents={incidents} chargers={chargers} account={session.name} onSelect={s=>{setError('');setSelected(s)}} reload={load} loading={loading}/>}
        {isDuty&&<OperationsWorkspace api={api} tab={tab} navigate={setTab} refresh={refresh}/>}
        {role==='admin'&&tab==='overview'&&<>
          <section className="hero">
            <div>
              <p className="date">{today}</p>
              <h1>Chào mừng trở lại</h1>
              <p className="summary">{chargers.length?`${activeCount} trên ${chargers.length} cổng đang hoạt động, ${open.length} sự cố chưa đóng.`:'Chưa có dữ liệu cổng sạc.'}</p>
            </div>
            {isUser&&<button className="primary" onClick={()=>setShowCreate(true)}><Plus size={17}/>Báo sự cố</button>}
          </section>

          <div className="kpis">
            <Kpi label="Tổng số cổng" value={chargers.length} icon={Plug} color="#60C3FF" note={`${acCount} AC · ${dcCount} DC`}/>
            <Kpi label="Đang hoạt động" value={activeCount} icon={BatteryCharging} color="#FF7A45" meter={chargers.length?activeCount/chargers.length:0} note={`${chargers.length?Math.round(activeCount/chargers.length*100):0}% tổng số cổng`}/>
            <Kpi label="Sự cố đang mở" value={open.length} icon={TriangleAlert} color="#FFB547" note={`${urgentOpen} khẩn cấp`}/>
            <Kpi label="Đang xử lý" value={inProgress} icon={Loader} color="#B39DFF" note="Kỹ thuật viên đang làm"/>
          </div>

          <div className="overview-grid">
            <section className="panel">
              <PanelHead title="Bãi sạc" desc="Mỗi ô là một cổng. Ô có viền sáng đang cấp điện."/>
              {chargers.length?<Yard chargers={chargers}/>:<Empty icon={Plug} text="Chưa có dữ liệu cổng sạc."/>}
            </section>
            <div className="side-col">
              <section className="panel">
                <PanelHead title="Phân bố trạng thái" desc="Tỷ lệ theo trạng thái hiện tại"/>
                <div className="donut-wrap">
                  <Donut rows={statusRows} total={chargers.length} label="cổng"/>
                  <ul className="legend">{statusRows.map(r=><li key={r.name} style={{'--tone':r.color}}><i/><span>{r.name}</span><b>{r.value}</b></li>)}</ul>
                </div>
              </section>
              <section className="panel">
                <PanelHead title="Sự cố gần đây" desc="Các báo cáo cần theo dõi"/>
                {incidents.length?<div className="recent-list">{incidents.slice(0,5).map(s=>{const P=PRIORITY[s.mucUuTien]||PRIORITY['Bình thường'];const PI=P.icon;return <button className="recent" key={s.maSuCo} onClick={()=>{setSelected(s);setTab('incidents')}}>
                  <span className="ic-box" style={{'--tone':P.color}}><PI size={16}/></span>
                  <span className="recent-body"><b>{s.loaiSuCo}</b><small>{s.maCong} · {s.maSuCo}</small></span>
                  <StatusPill value={s.trangThai}/>
                </button>})}</div>:<Empty icon={CircleCheck} text="Chưa có sự cố nào."/>}
              </section>
            </div>
          </div>
        </>}

        {!isTech&&!isDuty&&tab==='incidents'&&<>
          <div className="toolbar">
            <label className="search"><Search size={17}/><input placeholder="Tìm theo mã, cổng hoặc loại sự cố" value={query} onChange={e=>setQuery(e.target.value)}/></label>
            {isUser&&<button className="primary" onClick={()=>setShowCreate(true)}><Plus size={17}/>Báo sự cố</button>}
          </div>
          <div className="chips">{FILTERS.map(f=><button key={f.id} className={`chip ${filter===f.id?'on':''}`} aria-pressed={filter===f.id} onClick={()=>setFilter(f.id)}>{f.label}<em>{incidents.filter(f.test).length}</em></button>)}</div>
          <section className="panel table-panel">
            <div className="table-wrap">
              <table>
                <thead><tr><th>Mã sự cố</th><th>Loại sự cố</th><th>Cổng sạc</th><th>Ưu tiên</th><th>Trạng thái</th><th>Kỹ thuật viên</th><th><span className="sr-only">Thao tác</span></th></tr></thead>
                <tbody>{visible.map(s=>{const I=ISSUE_ICON[s.loaiSuCo]||CircleDot;const ic=cfgOf(s.trangThai).color;return <tr key={s.maSuCo}>
                  <td><b>{s.maSuCo}</b></td>
                  <td><span className="issue-cell"><span className="ic-box" style={{'--tone':ic}}><I size={16}/></span>{s.loaiSuCo}</span></td>
                  <td>{s.maCong}</td>
                  <td><Priority value={s.mucUuTien}/></td>
                  <td><StatusPill value={s.trangThai}/></td>
                  <td>{s.maKTV||<span className="muted">Chưa giao</span>}</td>
                  <td className="td-action"><button className="text-btn" onClick={()=>setSelected(s)}>Chi tiết</button></td>
                </tr>})}</tbody>
              </table>
              {!visible.length&&<Empty icon={Search} text="Không tìm thấy sự cố phù hợp."/>}
            </div>
          </section>
          <p className="table-note">Hiển thị {visible.length} trên {incidents.length} sự cố</p>
        </>}

        {tab==='reports'&&role==='admin'&&<>
          <div className="report-head">
            <p className="muted">Số liệu tổng hợp trong {period} ngày gần nhất</p>
            <div className="segmented" role="group" aria-label="Khoảng thời gian">
              {[7,30,90].map(d=><button key={d} className={period===d?'on':''} aria-pressed={period===d} onClick={()=>setPeriod(d)}>{d} ngày</button>)}
            </div>
          </div>
          <div className="kpis">
            <Kpi label="Phiên sạc" value={fmt(rep.tong_phien)} icon={Activity} color="#60C3FF" note={`${fmt(rep.phien_hoan_tat||0)} phiên hoàn tất`}/>
            <Kpi label="Tỷ lệ hoàn tất" value={`${Math.round(completionRate*100)}%`} icon={CircleCheck} color="#E2E0EE" meter={completionRate} note="Trên tổng số phiên"/>
            <Kpi label="Điện năng" value={`${num(rep.tong_dien_kwh)} kWh`} icon={Gauge} color="#B39DFF" note="Thiết bị báo khi kết thúc"/>
            <Kpi label="Doanh thu" value={`${fmt(Math.round(Number(report?.thanhToan?.doanh_thu||0)))} ₫`} icon={Banknote} color="#FF7A45" note="Phiên hoàn tất, đã trừ hoàn tiền"/>
          </div>
          <div className="overview-grid">
            <section className="panel">
              <PanelHead title="Phân bố trạng thái cổng" desc="Số lượng theo trạng thái hiện tại"/>
              <Bars rows={(report?.congSac||[]).map(x=>({name:x.trangthai,value:Number(x.so_luong),...cfgOf(x.trangthai)}))}/>
            </section>
            <section className="panel">
              <PanelHead title="Sự cố theo loại và trạng thái" desc="Ghi nhận trong khoảng đã chọn"/>
              {report?.suCo?.length?<div className="report-list">{report.suCo.map((s,i)=>{const I=ISSUE_ICON[s.loai_su_co]||CircleDot;const ic=cfgOf(s.trangthai).color;return <div className="report-row" key={i}>
                <span className="ic-box" style={{'--tone':ic}}><I size={16}/></span>
                <span>{s.loai_su_co}</span>
                <StatusPill value={s.trangthai}/>
                <b>{s.so_luong}</b>
              </div>})}</div>:<Empty icon={CircleCheck} text="Không có sự cố trong khoảng này."/>}
            </section>
          </div>
        </>}
      </div>
    </main>

    {showCreate&&<Modal title="Báo cáo sự cố" close={()=>setShowCreate(false)}>
      <form className="modal-body form" onSubmit={createIncident}>
        <label>Cổng sạc<select name="maCong" required>{chargers.map(c=><option key={c.maCong} value={c.maCong}>{c.maCong} · {c.loaiCong}</option>)}</select></label>
        <label>Loại sự cố<input name="loaiSuCo" placeholder="Ví dụ: Cổng không nhận xe" required maxLength={30}/></label>
        <label>Mức ưu tiên<select name="mucUuTien"><option>Bình thường</option><option>Thấp</option><option>Cao</option><option>Khẩn cấp</option></select></label>
        <label>Mô tả<textarea name="moTa" rows={4} placeholder="Mô tả tình trạng bạn gặp phải" required maxLength={500}/></label>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={()=>setShowCreate(false)}>Hủy</button>
          <button className="primary" disabled={busy}>Gửi báo cáo</button>
        </div>
      </form>
    </Modal>}

    {selected&&<Modal title="Chi tiết sự cố" close={()=>setSelected(null)}>
      <div className="modal-body detail">
        <div className="detail-top"><Priority value={selected.mucUuTien}/><StatusPill value={selected.trangThai}/></div>
        <h3>{selected.loaiSuCo}</h3>
        <p>{selected.moTa}</p>
        <div className="detail-meta">
          <div><small>Mã sự cố</small><b>{selected.maSuCo}</b></div>
          <div><small>Cổng sạc</small><b>{selected.maCong}</b></div>
          <div><small>Kỹ thuật viên</small><b>{selected.maKTV||'Chưa giao'}</b></div>
        </div>
        {isOps&&<form className="inline-form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);updateIncident(selected.maSuCo,'giao-viec',{maKTV:f.get('maKTV'),hanXuLy:f.get('hanXuLy')?new Date(f.get('hanXuLy')).toISOString().slice(0,-1):null})}}>
          <h4>Giao kỹ thuật viên</h4>
          <label>Mã kỹ thuật viên<input name="maKTV" placeholder="Mã người dùng kỹ thuật viên" required/></label>
          <label>Hạn xử lý<input name="hanXuLy" type="datetime-local"/></label>
          <button className="secondary">Giao việc</button>
        </form>}
        {error&&<div className="banner error" role="alert">{error}</div>}
        {(role==='admin'||(isTech&&selected.maKTV===session.name))&&!CLOSED.includes(selected.trangThai)&&<form key={`${selected.maSuCo}-${selected.revision||0}`} className="inline-form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);updateIncident(selected.maSuCo,'trang-thai',Object.fromEntries(f))}}>
          <h4>Cập nhật xử lý</h4>
          <label>Trạng thái<select name="trangThai" defaultValue={selected.trangThai}>{(selected.trangThai==='Đã xử lý'?['Đang xử lý','Đã xử lý','Đã đóng']:['Đang xử lý','Chờ linh kiện','Đã xử lý']).map(s=><option key={s}>{s}</option>)}</select></label>
          <label>Nguyên nhân<textarea name="nguyenNhan" defaultValue={selected.nguyenNhan||''} maxLength={500} rows={2}/></label>
          <label>Cách xử lý<textarea name="cachXuLy" defaultValue={selected.cachXuLy||''} maxLength={500} rows={2}/></label>
          <label>Ghi chú lịch sử<input name="ghiChu" maxLength={255}/></label>
          <p className="muted">Cần ghi nguyên nhân và cách xử lý trước khi hoàn tất. Cổng lỗi cần được xác nhận an toàn trong mục bảo trì.</p>
          <button className="primary" disabled={savingIncident}>{savingIncident?'Đang lưu…':'Lưu cập nhật'}</button>
        </form>}
        <div className="tech-resolution"><p><b>Nguyên nhân:</b> {selected.nguyenNhan||'Chưa ghi nhận'}</p><p><b>Cách xử lý:</b> {selected.cachXuLy||'Chưa ghi nhận'}</p></div>
        <h4>Lịch sử</h4>
        <History key={selected.revision} id={selected.maSuCo}/>
      </div>
    </Modal>}
  </div>;
}

/* ---------- Thành phần giao diện ---------- */

function Kpi({label,value,icon:Icon,color,note,meter}){
  return <div className="kpi" style={{'--tone':color}}>
    <div className="kpi-top"><span className="ic-box"><Icon size={18}/></span><span className="kpi-label">{label}</span></div>
    <strong className="kpi-value">{value ?? '—'}</strong>
    {meter!=null&&<div className="meter" aria-hidden="true"><i style={{width:`${Math.round(meter*100)}%`}}/></div>}
    <small>{note}</small>
  </div>;
}

function PanelHead({title,desc,children}){
  return <div className="panel-head"><div><h3>{title}</h3>{desc&&<p>{desc}</p>}</div>{children}</div>;
}

function Yard({chargers}){
  const lanes=['AC','DC'].map(t=>({type:t,items:chargers.filter(c=>c.loaiCong===t)}));
  const other=chargers.filter(c=>!['AC','DC'].includes(c.loaiCong));
  if(other.length) lanes.push({type:'Khác',items:other});
  return <div className="lanes">{lanes.filter(l=>l.items.length).map(l=><div key={l.type}>
    <div className="lane-head"><span className="lane-tag">{l.type}</span><small>{l.items.length} cổng</small></div>
    <div className="tiles">{l.items.map(c=>{const cfg=cfgOf(c.trangThai);const I=cfg.icon;return <div key={c.maCong} className={`tile ${c.trangThai==='Đang sạc'?'live':''}`} style={{'--tone':cfg.color}}>
      <div className="tile-top"><span className="ic-box"><I size={16}/></span></div>
      <b>{c.maCong}</b>
      <span className="tile-sub">{c.loaiCong} · {c.congSuatDinhMuc ?? '—'} kW</span>
      <span className="tile-state"><i/>{c.trangThai||'—'}</span>
    </div>})}</div>
  </div>)}</div>;
}

function Donut({rows,total,label}){
  const r=58, C=2*Math.PI*r; let acc=0;
  return <svg viewBox="0 0 160 160" className="donut" role="img" aria-label={`${total} ${label}`}>
    <circle cx="80" cy="80" r={r} className="donut-track"/>
    {total>0&&rows.filter(x=>x.value>0).map(x=>{const len=x.value/total*C;const el=<circle key={x.name} cx="80" cy="80" r={r} fill="none" stroke={x.color} strokeWidth="14" strokeDasharray={`${len} ${C-len}`} strokeDashoffset={-acc} transform="rotate(-90 80 80)"/>;acc+=len;return el})}
    <text x="80" y="82" textAnchor="middle" className="donut-value">{total}</text>
    <text x="80" y="101" textAnchor="middle" className="donut-label">{label}</text>
  </svg>;
}

function Bars({rows}){
  if(!rows.length) return <Empty icon={BarChart3} text="Chưa có số liệu."/>;
  const max=Math.max(1,...rows.map(r=>r.value));
  return <div className="bars">{rows.map(r=>{const I=r.icon||CircleDot;return <div className="bar-row" key={r.name} style={{'--tone':r.color||FALLBACK.color}}>
    <span className="bar-label"><span className="ic-box"><I size={15}/></span>{r.name}</span>
    <div className="bar-bg"><i style={{width:`${r.value/max*100}%`}}/></div>
    <b>{r.value}</b>
  </div>})}</div>;
}

function StatusPill({value}){const cfg=cfgOf(value);const I=cfg.icon;return <span className="pill" style={{'--tone':cfg.color}}><I size={14}/>{value||'—'}</span>}

function Priority({value}){const cfg=PRIORITY[value]||PRIORITY['Bình thường'];const I=cfg.icon;return <span className="prio" style={{'--tone':cfg.color}}><I size={15}/>{value||'—'}</span>}

function Empty({icon:Icon=ClipboardList,text}){return <div className="empty"><span className="ic-box empty-ic"><Icon size={20}/></span><span>{text}</span></div>}

function Modal({title,close,children}){
  return <div className="overlay" onMouseDown={e=>e.target===e.currentTarget&&close()}>
    <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal-head"><h3>{title}</h3><button className="icon-btn" onClick={close} title="Đóng" aria-label="Đóng"><X size={18}/></button></div>
      {children}
    </div>
  </div>;
}

function History({id}){
  const [rows,setRows]=useState([]);
  useEffect(()=>{api(`/api/su-co/${id}/lich-su`).then(setRows).catch(()=>{})},[id]);
  if(!rows.length) return <Empty icon={Clock} text="Chưa có lịch sử."/>;
  return <div className="timeline">{rows.map(r=><div className="tl-item" key={r.maLSSC}>
    <span className="tl-dot"/>
    <div className="tl-body">
      <b>{r.trangThai}</b>
      <small>{r.ghiChu||'Cập nhật trạng thái'} · {r.nguoiCapNhat} · {new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(r.thoiDiem)?r.thoiDiem:r.thoiDiem+'Z').toLocaleString('vi-VN')}</small>
    </div>
  </div>)}</div>;
}
