/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Biểu diễn lỗi nghiệp vụ xác thực với mã HTTP và thông báo lỗi theo trường.
 */

package com.gcmb.backend.administration.auth.exception;



import java.util.Map;

public class AuthFailure extends RuntimeException {
    final int status;
    final Map<String, String> errors;
    public int status() { return status; }
    public AuthFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.errors = Map.of(field, message);
    }
}
