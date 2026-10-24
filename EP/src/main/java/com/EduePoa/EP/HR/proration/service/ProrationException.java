package com.EduePoa.EP.HR.proration.service;

/**
 * Raised when an employee's employment dates are invalid for a payroll period (e.g. a leaving
 * date before the joining date, or a date that cannot be reconciled with the period). Surfaced
 * as a blocking validation error on the run rather than silently producing a wrong figure.
 */
public class ProrationException extends RuntimeException {
    public ProrationException(String message) {
        super(message);
    }
}
