package com.EduePoa.EP.BankIntergration.BankRequest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bank.callback")
@Getter
@Setter
public class BankCallbackAuthConfig {
    private String username;
    private String password;
}