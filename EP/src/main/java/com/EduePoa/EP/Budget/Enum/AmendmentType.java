package com.EduePoa.EP.Budget.Enum;

/**
 * The kind of change a {@link com.EduePoa.EP.Budget.Alert.BudgetAlert budget} amendment makes
 * (design §9).
 * <ul>
 *   <li>{@link #INCREASE} / {@link #DECREASE} — adjust an existing line's allocation by a delta.</li>
 *   <li>{@link #SUPPLEMENTARY_LINE} — add a brand-new allocation (category + optional org unit) to
 *       an already-approved budget.</li>
 * </ul>
 */
public enum AmendmentType {
    INCREASE,
    DECREASE,
    SUPPLEMENTARY_LINE
}
