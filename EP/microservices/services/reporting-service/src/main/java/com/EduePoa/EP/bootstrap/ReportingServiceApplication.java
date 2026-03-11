package com.EduePoa.EP.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.EduePoa.EP.Reports",
        "com.EduePoa.EP.Utils"
})
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}