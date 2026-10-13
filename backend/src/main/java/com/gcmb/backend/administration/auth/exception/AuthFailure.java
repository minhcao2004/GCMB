package com.gcmb.backend.administration.auth.exception;



import java.util.Map;

// Lỗi nghiệp vụ có mã HTTP và lỗi theo trường cho API.
public class AuthFailure extends RuntimeException {
    final int status;
    final Map<String, String> errors;
    // Trả mã HTTP tương ứng với lỗi nghiệp vụ.
    public int status() { return status; }
    // Gắn thông báo cho trường gây lỗi để frontend hiển thị đúng vị trí.
    public AuthFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.errors = Map.of(field, message);
    }
}
