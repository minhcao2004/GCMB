package com.gcmb.backend.booking.exception;

import com.gcmb.backend.administration.auth.exception.AuthFailure;

public class BookingFailure extends AuthFailure {
    public BookingFailure(int status, String field, String message) {
        super(status, field, message);
    }
}
