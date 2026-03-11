package com.EduePoa.EP.Authentication.Config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.modelmapper.ModelMapper;


@Configuration
public class BeansConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}