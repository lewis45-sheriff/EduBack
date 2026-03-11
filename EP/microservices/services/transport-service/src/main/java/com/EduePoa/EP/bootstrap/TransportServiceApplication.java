package com.EduePoa.EP.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.EduePoa.EP.Transport",
        "com.EduePoa.EP.Authentication.AuditLogs",
        "com.EduePoa.EP.Utils"
})
@EntityScan(basePackages = {
        "com.EduePoa.EP.Transport",
        "com.EduePoa.EP.StudentRegistration",
        "com.EduePoa.EP.Grade",
        "com.EduePoa.EP.FeeStructure",
        "com.EduePoa.EP.FeeStructure.FeeComponentConfig",
        "com.EduePoa.EP.FeeComponents",
        "com.EduePoa.EP.Authentication.User",
        "com.EduePoa.EP.Authentication.Role",
        "com.EduePoa.EP.Authentication.AuditLogs"
})
@EnableJpaRepositories(basePackages = {
        "com.EduePoa.EP.Transport",
        "com.EduePoa.EP.StudentRegistration",
        "com.EduePoa.EP.Grade",
        "com.EduePoa.EP.Authentication.AuditLogs"
})
public class TransportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportServiceApplication.class, args);
    }
}