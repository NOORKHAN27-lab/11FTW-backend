package com.elevenftw.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Spring Mail. Requires spring.mail.* (SMTP host/user/
 * password) to be configured — see application.properties and
 * SETUP_GUIDE.md. Without valid SMTP credentials this will throw when
 * actually asked to send, which AuthService deliberately doesn't let bubble
 * back to the caller (see forgotPassword) — an SMTP misconfiguration
 * shouldn't reveal anything about which emails exist in the system.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailService(
        JavaMailSender mailSender,
        @Value("${app.mail.from}") String fromAddress,
        @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your 11FTW password");
        message.setText(
            "Someone requested a password reset for this email on 11FTW.\n\n" +
            "Reset your password: " + resetLink + "\n\n" +
            "This link expires in 1 hour. If you didn't request this, you can ignore this email."
        );
        mailSender.send(message);
    }

    public void sendVerificationEmail(String toEmail, String verificationToken) {
        String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Confirm your 11FTW email");
        message.setText(
            "Welcome to 11FTW! Confirm this email address to activate your account.\n\n" +
            "Confirm your email: " + verifyLink + "\n\n" +
            "This link expires in 24 hours."
        );
        mailSender.send(message);
    }

    /** Weekly "new matches near you" email — see EmailDigestScheduler. */
    public void sendWeeklyDigest(String toEmail, java.util.List<com.elevenftw.entity.Match> matches) {
        StringBuilder body = new StringBuilder("New matches posted near you this week on 11FTW:\n\n");
        for (com.elevenftw.entity.Match m : matches) {
            body.append("- ").append(m.getSport().name()).append(" · ")
                .append(m.getMatchDate()).append(" ").append(m.getStartTime())
                .append(" · ").append(m.getAddressText()).append("\n")
                .append("  ").append(frontendUrl).append("/matches/").append(m.getId()).append("\n\n");
        }
        body.append("Open 11FTW to see more: ").append(frontendUrl);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("This week's new matches on 11FTW");
        message.setText(body.toString());
        mailSender.send(message);
    }
}
