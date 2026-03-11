package com.EduePoa.EP.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.EduePoa.EP.Authentication",
        "com.EduePoa.EP.Utils",
        "com.EduePoa.EP.BankIntergration.BankRequest"
})
@EntityScan(basePackages = {
        "com.EduePoa.EP.Authentication.User",
        "com.EduePoa.EP.Authentication.Role",
        "com.EduePoa.EP.Authentication.AuditLogs"
})
@EnableJpaRepositories(basePackages = {
        "com.EduePoa.EP.Authentication.User",
        "com.EduePoa.EP.Authentication.Role",
        "com.EduePoa.EP.Authentication.AuditLogs"
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}