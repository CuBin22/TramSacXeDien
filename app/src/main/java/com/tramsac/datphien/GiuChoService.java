package com.tramsac.datphien;

import com.tramsac.congsac.CongSac;
import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.TrangThaiCong;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đối chiếu trạng thái giữ chỗ của cổng với các đặt chỗ (quy tắc 2): cổng chỉ ở "Đã đặt" khi có đặt chỗ đã
 * đến cửa sổ giờ (15 phút trước giờ bắt đầu) và chưa kết thúc. Chỉ đụng tới cổng đang Rảnh hoặc Đã đặt;
 * các trạng thái khác (Đang sạc, Lỗi, Bảo trì, Mất kết nối) do giai đoạn 3 quản lý.
 * Hàm idempotent: gọi bao nhiêu lần cũng cho cùng kết quả, nên vừa dùng khi hủy/đổi lịch/quá hạn
 * vừa là bộ định thời tự sửa lệch.
 */
@Service
public class GiuChoService {

    private final CongSacRepository congSacRepository;
    private final DatPhienRepository datPhienRepository;

    public GiuChoService(CongSacRepository congSacRepository, DatPhienRepository datPhienRepository) {
        this.congSacRepository = congSacRepository;
        this.datPhienRepository = datPhienRepository;
    }

    /** Trả về true nếu trạng thái cổng vừa thay đổi. Khóa dòng cổng (thứ tự khóa: người dùng → đặt chỗ → cổng). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean doiChieu(String maCong, LocalDateTime now) {
        CongSac cong = congSacRepository.findForUpdateByMaCong(maCong).orElse(null);
        if (cong == null) {
            return false;
        }
        TrangThaiCong trangThai = cong.layTrangThai();
        if (trangThai != TrangThaiCong.RANH && trangThai != TrangThaiCong.DA_DAT) {
            return false;
        }
        List<DatPhien> chuGiuCho = datPhienRepository.timChuGiuCho(maCong, TrangThaiDatPhien.DA_DAT.giaTri(),
                now.plus(QuyDinhDatPhien.CUA_SO_GIU_CHO), now);
        boolean coChu = !chuGiuCho.isEmpty();

        if (coChu && trangThai == TrangThaiCong.RANH) {
            cong.ganCong(now);
            return true;
        }
        if (!coChu && trangThai == TrangThaiCong.DA_DAT) {
            cong.giaiPhongCong(now);
            return true;
        }
        return false;
    }
}
