package com.tramsac.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có mã máy đọc được (ma) và thông báo cho người dùng (thongBao). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String ma;
    private final Map<String, String> truong;

    public ApiException(HttpStatus status, String ma, String thongBao) {
        this(status, ma, thongBao, null);
    }

    /** truong: tên trường → thông báo lỗi của trường đó (có thể null). */
    public ApiException(HttpStatus status, String ma, String thongBao, Map<String, String> truong) {
        super(thongBao);
        this.status = status;
        this.ma = ma;
        this.truong = truong;
    }

    public Map<String, String> truong() {
        return truong;
    }

    public HttpStatus status() {
        return status;
    }

    public String ma() {
        return ma;
    }
}
