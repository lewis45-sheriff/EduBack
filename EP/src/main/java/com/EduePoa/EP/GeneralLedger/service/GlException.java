package com.EduePoa.EP.GeneralLedger.service;

/**
 * Domain exception for GL validation and posting failures. Carries a safe,
 * caller-facing message; controllers map it to a 4xx {@code CustomResponse}
 * without leaking internal database or stack details.
 */
public class GlException extends RuntimeException {
    public GlException(String message) {
        super(message);
    }
}
