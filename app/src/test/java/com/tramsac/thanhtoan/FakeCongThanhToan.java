package com.tramsac.thanhtoan;

import com.tramsac.common.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;

/**
 * Cổng thanh toán giả cho test: ghi lại các lần app gọi sang cổng và có thể giả lập cổng đang lỗi. Test tự đóng vai
 * cổng thanh toán bằng cách gọi webhook của app (có ký HMAC) thay vì chờ payment-sim.
 */
public class FakeCongThanhToan implements CongThanhToan {

    public record Goi(String loai, String ma, String khoa, BigDecimal soTien) { }

    private final List<Goi> cacLanGoi = new CopyOnWriteArrayList<>();
    private volatile boolean loi;

    @Override
    public void taoDon(DonThanhToan don, GiaoDichThanhToan giaoDich) {
        cacLanGoi.add(new Goi("TAO_DON", giaoDich.getMaGiaoDich(), don.getKhoaIdempotency(), don.getSoTien()));
        kiemTraLoi();
    }

    @Override
    public void hoanTien(String khoaIdempotency, String maGiaoDich, BigDecimal soTien) {
        cacLanGoi.add(new Goi("HOAN_TIEN", maGiaoDich, khoaIdempotency, soTien));
        kiemTraLoi();
    }

    @Override
    public void guiLaiCallback(String maGiaoDich) {
        cacLanGoi.add(new Goi("GUI_LAI", maGiaoDich, null, null));
        kiemTraLoi();
    }

    private void kiemTraLoi() {
        if (loi) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_SIM_KHONG_KHA_DUNG",
                    "Không kết nối được payment-sim.");
        }
    }

    public void datLai() {
        cacLanGoi.clear();
        loi = false;
    }

    public void datLoi(boolean value) { this.loi = value; }

    public long soLanGoi(String loai) {
        return cacLanGoi.stream().filter(g -> g.loai().equals(loai)).count();
    }

    public List<Goi> danhSach() { return List.copyOf(cacLanGoi); }
}
