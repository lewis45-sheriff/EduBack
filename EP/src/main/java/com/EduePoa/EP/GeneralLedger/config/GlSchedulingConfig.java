package com.EduePoa.EP.GeneralLedger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling for the GL posting sweeper. Scoped to this module so
 * turning on {@code @Scheduled} support is an explicit, auditable GL concern and
 * does not silently change behaviour elsewhere.
 */
@Configuration
@EnableScheduling
public class GlSchedulingConfig {
}
