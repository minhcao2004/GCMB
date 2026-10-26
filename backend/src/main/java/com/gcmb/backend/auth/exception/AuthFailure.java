/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Biểu diễn lỗi nghiệp vụ xác thực với mã HTTP và thông báo lỗi theo trường.
 */

package com.gcmb.backend.auth.exception;



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
