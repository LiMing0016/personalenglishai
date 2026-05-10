package com.personalenglishai.backend.service.email.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpEmailServiceTest {

    @Test
    void sendDoesNotPropagateMailRuntimeFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenThrow(new MailSendException("smtp unavailable"));
        SmtpEmailService service = new SmtpEmailService(mailSender, "noreply@example.com");

        assertDoesNotThrow(() -> service.send("u1@example.com", "Verify", "<p>hello</p>"));
    }
}
