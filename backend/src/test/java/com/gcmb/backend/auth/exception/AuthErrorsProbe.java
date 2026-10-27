/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Exposes package-private AuthErrors responses to the Personal Account manual runner.
 */
package com.gcmb.backend.auth.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;

public final class AuthErrorsProbe {
    private AuthErrorsProbe() {
    }

    public static ResponseEntity<?> mailUnavailable() {
        return new AuthErrors().mail();
    }

    public static String message(ResponseEntity<?> response) {
        if (response.getBody() instanceof Map<?, ?> body) {
            return String.valueOf(body.get("message"));
        }
        return null;
    }
}
