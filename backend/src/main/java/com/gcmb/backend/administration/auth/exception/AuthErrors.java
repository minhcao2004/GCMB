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
    private static final Logger log=LoggerFactory.getLogger(AuthErrors.class);
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<?> failure(AuthFailure e) { return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage(), "errors", e.errors)); }
    @ExceptionHandler(RecoveryMailer.MailUnavailable.class)
    ResponseEntity<?> mail() { return error(503, "Gửi email thất bại. Vui lòng thử lại sau"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> badRequest() { return error(400, "Dữ liệu gửi lên không hợp lệ"); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> responseStatus(ResponseStatusException e) {
        HttpStatusCode status=e.getStatusCode();
        String message=e.getReason()==null?"Yêu cầu không hợp lệ":e.getReason();
        return ResponseEntity.status(status).body(Map.of("message",message,"errors",Map.of("form",message)));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        log.error("Unhandled API error",e);
        return error(503, "Không thể hoàn tất thao tác do lỗi máy chủ. Vui lòng thử lại");
    }
    private ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message, "errors", Map.of("form", message))); }
}
