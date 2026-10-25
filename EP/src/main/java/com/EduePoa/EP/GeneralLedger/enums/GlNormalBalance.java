package com.EduePoa.EP.GeneralLedger.enums;

/**
 * The side on which an account normally carries its balance. A positive signed
 * balance (total debits minus total credits) presents as a positive number for
 * {@link #DEBIT} accounts and is negated for {@link #CREDIT} accounts so that a
 * well-behaved account shows a positive normal-balance figure.
 */
public enum GlNormalBalance {
    DEBIT,
    CREDIT
}
