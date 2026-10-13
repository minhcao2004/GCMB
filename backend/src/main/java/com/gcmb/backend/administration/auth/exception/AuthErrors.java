/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chuyển các ngoại lệ xác thực, gửi email và xử lý yêu cầu thành phản hồi lỗi API.

 * Author: TrongNV
 * Created: 12/10/2026
 * Description: Chuẩn hóa phản hồi lỗi xác thực và phân quyền.

 */

package com.gcmb.backend.administration.auth.exception;

import com.gcmb.backend.administration.auth.service.RecoveryMailer;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthErrors {
    private static final Logger log = LoggerFactory.getLogger(AuthErrors.class);

    @ExceptionHandler(AuthFailure.class)
    // Chuyển lỗi nghiệp vụ thành mã HTTP và lỗi theo trường.
    ResponseEntity<?> failure(AuthFailure e) { return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage(), "errors", e.errors)); }
    @ExceptionHandler(RecoveryMailer.MailUnavailable.class)
    // Trả lỗi dịch vụ khi không gửi được email khôi phục.
    ResponseEntity<?> mail() { return error(503, "Gửi email thất bại. Vui lòng thử lại sau"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    // Trả lỗi dữ liệu JSON không đọc được.
    ResponseEntity<?> badRequest() { return error(400, "Dữ liệu gửi lên không hợp lệ"); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> responseStatus(ResponseStatusException e) {
        HttpStatusCode status=e.getStatusCode();
        String message=e.getReason()==null?"Yêu cầu không hợp lệ":e.getReason();
        return ResponseEntity.status(status).body(Map.of("message",message,"errors",Map.of("form",message)));
    }
    @ExceptionHandler(Exception.class)
    // Ghi stack trace phía server nhưng che chi tiết nội bộ khỏi client.
    ResponseEntity<?> unexpected(Exception e) {
        log.error("Unhandled API exception", e);
        return error(503, "Không thể hoàn tất thao tác do lỗi máy chủ. Vui lòng thử lại");
    }
    // Trả lỗi chung theo cùng cấu trúc message và errors.form.
    private ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message, "errors", Map.of("form", message))); }
}
