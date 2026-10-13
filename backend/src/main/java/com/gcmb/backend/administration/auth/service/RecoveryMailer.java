package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.config.AuthSettings;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class RecoveryMailer {
    private final ObjectProvider<JavaMailSender> senders;
    private final AuthSettings policy;
    // Cho phép ứng dụng đăng nhập khởi động dù SMTP chưa được cấu hình.
    public RecoveryMailer(ObjectProvider<JavaMailSender> senders, AuthSettings policy) { this.senders = senders; this.policy = policy; }
    // Gửi mã khôi phục; che thông tin lỗi SMTP khỏi phản hồi người dùng.
    public void send(String email, String code) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || policy.mailFrom() == null || policy.mailFrom().isBlank()) throw new MailUnavailable();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(policy.mailFrom()); message.setTo(email);
        message.setSubject("GenZ Cinema & Music Box — Mã xác nhận đặt lại mật khẩu");
        message.setText("Mã xác nhận của bạn là: " + code + "\nMã chỉ sử dụng một lần. Khi yêu cầu mã mới, mã cũ không còn hiệu lực.\nKhông chia sẻ mã này. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        try { sender.send(message); } catch (RuntimeException e) { throw new MailUnavailable(); }
    }
    // Không giữ nội dung lỗi SMTP vì nhà cung cấp có thể lặp lại nội dung email.
    public static class MailUnavailable extends RuntimeException {}
}
