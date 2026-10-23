package com.gcmb.backend.booking.exception;

import com.gcmb.backend.auth.exception.AuthFailure;

// Lỗi booking dùng chung định dạng phản hồi lỗi của auth.
public class BookingFailure extends AuthFailure {
    // Kế thừa cấu trúc lỗi theo trường để frontend booking dùng cùng cách hiển thị.
    public BookingFailure(int status, String field, String message) {
        super(status, field, message);
    }
}
