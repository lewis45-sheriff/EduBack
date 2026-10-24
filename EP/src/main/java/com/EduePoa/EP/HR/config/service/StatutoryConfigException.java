package com.EduePoa.EP.HR.config.service;

/**
 * Raised when the statutory configuration required to run payroll for a given period is
 * missing or incomplete for the current tenant. Surfaced as a blocking validation error
 * on the payroll run rather than a silent default, because running payroll on guessed
 * statutory figures would be a compliance failure.
 */
public class StatutoryConfigException extends RuntimeException {
    public StatutoryConfigException(String message) {
        super(message);
    }
}
