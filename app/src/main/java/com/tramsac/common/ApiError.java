package com.tramsac.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Thân phản hồi lỗi thống nhất của mọi API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String ma, String thongBao, Map<String, String> truong) {

    public static ApiError of(String ma, String thongBao) {
        return new ApiError(ma, thongBao, null);
    }
}
