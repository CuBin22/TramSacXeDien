package com.tramsac.baocao;

import com.tramsac.common.ApiException;
import com.tramsac.nguoidung.VaiTro;
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bao-cao")
public class BaoCaoController {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public BaoCaoController(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @GetMapping
    public Map<String, Object> tongQuan(@RequestParam(required = false) LocalDate tuNgay,
                                        @RequestParam(required = false) LocalDate denNgay,
                                        Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + VaiTro.QUAN_TRI.name())))
            throw new ApiException(HttpStatus.FORBIDDEN, "KHONG_DU_QUYEN", "Chỉ quản trị viên được xem báo cáo.");
        LocalDate to = denNgay == null ? LocalDate.now(clock) : denNgay;
        LocalDate from = tuNgay == null ? to.minusDays(29) : tuNgay;
        if (to.isBefore(from) || from.isBefore(to.minusYears(2))) throw new ApiException(HttpStatus.BAD_REQUEST, "KHOANG_NGAY_KHONG_HOP_LE", "Khoảng ngày không hợp lệ (tối đa 2 năm).");
        var phien = jdbc.queryForMap("""
                select count(*) as tong_phien,
                       count(*) filter (where p.trangthai='Hoàn tất') as phien_hoan_tat,
                       count(*) filter (where p.trangthai='Hủy') as phien_huy,
                       coalesce(sum(diennangkwh),0) as tong_dien_kwh
                  from phiensac p join datphien d on d.madatphien=p.madatphien
                 where coalesce(p.ketthucluc, p.batdauluc, d.ngaydat)::date between ? and ?
                """, from, to);
        var payment = jdbc.queryForMap("""
                select coalesce(sum(case when gd.ketqua='THANH_CONG' then d.sotien else 0 end),0) as doanh_thu,
                       count(*) filter (where gd.ketqua='THANH_CONG') as giao_dich_thanh_cong
                  from giaodichthanhtoan gd join donthanhtoan d on d.madon=gd.madon
                 where gd.thoidiem::date between ? and ?
                """, from, to);
        List<Map<String, Object>> incidents = jdbc.queryForList("""
                select s.loaisuco as loai_su_co, s.trangthai, count(distinct s.masuco) as so_luong
                  from suco s join (select masuco, min(thoidiem) as tao_luc from LichSuSuCo group by masuco) h
                    on h.masuco=s.masuco
                 where h.tao_luc::date between ? and ?
                 group by s.loaisuco, s.trangthai order by s.loaisuco, s.trangthai
                """, from, to);
        var chargers = jdbc.queryForList("select trangthai, count(*) as so_luong from congsac group by trangthai order by trangthai");
        return Map.of("tuNgay", from, "denNgay", to, "phien", phien,
                "thanhToan", payment, "suCo", incidents, "congSac", chargers);
    }
}
