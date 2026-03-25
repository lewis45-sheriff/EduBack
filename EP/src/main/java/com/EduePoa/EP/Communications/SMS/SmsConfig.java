package com.EduePoa.EP.Communications.SMS;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "africastalking")
@Getter
@Setter
public class SmsConfig {

    private String username;

    private String apiKey;

    private Sms sms = new Sms();

    @Getter
    @Setter
    public static class Sms {
        private String url;

        private String senderId;
    }
}
