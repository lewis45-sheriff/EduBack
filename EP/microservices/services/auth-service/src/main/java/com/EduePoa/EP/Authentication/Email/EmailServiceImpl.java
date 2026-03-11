package com.EduePoa.EP.Authentication.Email;

import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;


@Service
@RequiredArgsConstructor

@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;


    @Async
    public void sendEmail(String to, String subject, String body) {
        System.out.println(to+subject+body);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("lewiskipkemoi765@gmail.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("✅ Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("❌ Unexpected error while sending email to {}: {}", to, e.getMessage());
        }
    }
}
