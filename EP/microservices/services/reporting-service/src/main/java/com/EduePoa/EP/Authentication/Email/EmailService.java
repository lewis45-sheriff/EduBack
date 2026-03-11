package com.EduePoa.EP.Authentication.Email;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
}
