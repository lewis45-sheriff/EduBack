package com.EduePoa.EP.FileStorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;


@Configuration
public class FileStorageWebConfig implements WebMvcConfigurer {

    private final String uploadDir;
    private final String urlPrefix;

    public FileStorageWebConfig(
            @Value("${file.upload.dir:uploads}") String uploadDir,
            @Value("${file.upload.url-prefix:/uploads}") String urlPrefix) {
        this.uploadDir = uploadDir;
        String normalized = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        this.urlPrefix = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(location);
    }
}
