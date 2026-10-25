package com.EduePoa.EP.GeneralLedger;

/**
 * Stable {@code systemKey} constants for the seeded chart of accounts. Posting
 * rules resolve accounts by these keys, never by database id, so the same rule
 * works for every tenant and never crosses a tenant boundary.
 */
public final class GlAccountKeys {

    private GlAccountKeys() {
    }

    public static final String CASH_BANK = "CASH_BANK";
    public static final String STUDENT_AR = "STUDENT_AR";
    public static final String SUPPLIER_AP = "SUPPLIER_AP";
    public static final String NET_PAY_PAYABLE = "NET_PAY_PAYABLE";
    public static final String PAYE_PAYABLE = "PAYE_PAYABLE";
    public static final String NSSF_PAYABLE = "NSSF_PAYABLE";
    public static final String SHIF_PAYABLE = "SHIF_PAYABLE";
    public static final String AHL_PAYABLE = "AHL_PAYABLE";
    public static final String NITA_PAYABLE = "NITA_PAYABLE";
    /** Dedicated liability for the aggregate "other deductions" with no per-category split. */
    public static final String OTHER_DEDUCTIONS_PAYABLE = "OTHER_DEDUCTIONS_PAYABLE";
    public static final String FEES_INCOME = "FEES_INCOME";
    public static final String TRANSPORT_INCOME = "TRANSPORT_INCOME";
    public static final String SALARIES_EXPENSE = "SALARIES_EXPENSE";
    public static final String EMPLOYER_STATUTORY_EXPENSE = "EMPLOYER_STATUTORY_EXPENSE";
    public static final String EXPENSE_GENERAL = "EXPENSE_GENERAL";
}
