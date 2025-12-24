package com.example.auth_service.service.impl;

import com.example.auth_service.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:no-reply@qlkh.local}")
    private String from;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public EmailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendEmailVerification(String to, String token, String username) {
        String verifyUrl = frontendBaseUrl + "/verify-email?token=" + token;
        String subject = "Xác thực email tài khoản";
        String text = "Xin chào " + username + ",\n\n"
                + "Vui lòng xác thực email của bạn bằng link sau:\n"
                + verifyUrl + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này.";

        send(to, subject, text, "EMAIL_VERIFY");
    }

    @Override
    public void sendPasswordResetEmail(String to, String token, String username) {
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        String subject = "Đặt lại mật khẩu";
        String text = "Xin chào " + username + ",\n\n"
                + "Bạn đã yêu cầu đặt lại mật khẩu. Vui lòng dùng link sau:\n"
                + resetUrl + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này.";

        send(to, subject, text, "PASSWORD_RESET");
    }

    @Override
    public void sendUnlockCodeEmail(String to, String code, String username, java.util.Date expiry) {
        String subject = "Mã mở khóa tài khoản";
        String text = "Xin chào " + username + ",\n\n"
                + "Tài khoản của bạn vừa bị khóa tạm thời vì nhiều lần đăng nhập sai. "
                + "Để mở khóa, vui lòng nhập mã sau vào form mở khóa:\n\n"
                + code + "\n\n"
                + "Mã có hiệu lực đến: " + expiry + "\n\n"
                + "Nếu bạn không thực hiện yêu cầu này, vui lòng liên hệ quản trị hệ thống.";

        send(to, subject, text, "UNLOCK_CODE");
    }

    private void send(String to, String subject, String text, String category) {
        if (!emailEnabled) {
            log.info("[AUTH][MAIL][DISABLED] category={} to={} subject={} body={}", category, to, subject, text);
            return;
        }

        if (mailSender == null) {
            throw new IllegalStateException("Email is enabled but JavaMailSender is not configured. Please add mail config or disable app.email.enabled.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
        log.info("[AUTH][MAIL] Sent {} to={}", category, to);
    }
}


