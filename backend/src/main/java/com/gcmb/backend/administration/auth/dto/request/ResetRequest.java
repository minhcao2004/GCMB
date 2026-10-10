package com.gcmb.backend.administration.auth.dto.request;

// Avoid generated toString() methods containing credentials.
public class ResetRequest {
    public String email; public String challenge; public String code; public String password; public String confirmation;
}
