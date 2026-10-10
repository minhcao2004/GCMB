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
