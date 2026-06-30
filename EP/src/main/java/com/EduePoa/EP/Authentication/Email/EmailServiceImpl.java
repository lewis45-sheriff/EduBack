package com.EduePoa.EP.Authentication.Email;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.service.TenantConfigurationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final TenantConfigurationService tenantConfigurationService;

    @Value("${spring.mail.username:lewiskipkemoi765@gmail.com}")
    private String defaultSenderAddress;

    @Value("${spring.mail.sender-name:EduPoa}")
    private String defaultSenderName;


    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Resolve tenant-specific sender configuration with fallback to application defaults
            String senderAddress = resolveSenderAddress();
            String senderName = resolveSenderName();

            helper.setFrom(new InternetAddress(senderAddress, senderName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent successfully to {} from {} <{}>", to, senderName, senderAddress);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Resolves the email sender address from tenant-specific configuration.
     * Falls back to the application-level default if no tenant config is set or TenantContext is absent.
     */
    private String resolveSenderAddress() {
        if (TenantContext.isSet()) {
            String tenantId = TenantContext.getCurrentTenant();
            String tenantAddress = tenantConfigurationService.getConfigOrDefault(tenantId, "email.sender_address", null);
            if (tenantAddress != null && !tenantAddress.isBlank()) {
                return tenantAddress;
            }
        }
        return defaultSenderAddress;
    }

    /**
     * Resolves the email sender name from tenant-specific configuration.
     * Falls back to the application-level default if no tenant config is set or TenantContext is absent.
     */
    private String resolveSenderName() {
        if (TenantContext.isSet()) {
            String tenantId = TenantContext.getCurrentTenant();
            String tenantName = tenantConfigurationService.getConfigOrDefault(tenantId, "email.sender_name", null);
            if (tenantName != null && !tenantName.isBlank()) {
                return tenantName;
            }
        }
        return defaultSenderName;
    }
}
