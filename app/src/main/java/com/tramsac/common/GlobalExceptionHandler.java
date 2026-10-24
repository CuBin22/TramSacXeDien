package com.tramsac.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.ma(), e.getMessage(), e.truong()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> truong = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> truong.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ApiError body = new ApiError("DU_LIEU_KHONG_HOP_LE", "Dữ liệu không hợp lệ.", truong);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Vi phạm UNIQUE/ràng buộc của CSDL (ví dụ hai yêu cầu đăng ký đồng thời cùng email). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("TRUNG_DU_LIEU", "Dữ liệu bị trùng hoặc vi phạm ràng buộc."));
    }
}
