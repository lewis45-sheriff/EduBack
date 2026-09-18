package com.EduePoa.EP.Utils;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Jackson Hibernate6 module on the application's primary
 * {@link com.fasterxml.jackson.databind.ObjectMapper}.
 *
 * <p>Spring Boot auto-detects any {@link Module} bean and applies it to the
 * MVC {@code ObjectMapper}. This teaches Jackson how to handle Hibernate lazy
 * proxies: instead of failing with "No serializer found for ...
 * ByteBuddyInterceptor", an unloaded lazy association is serialized as
 * {@code null} rather than crashing the response.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Do NOT force-load lazy associations during serialization; leave them null.
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
