package com.EduePoa.EP.Authentication.AuditLogs.AuditAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply this annotation to any service method you want automatically audit-logged.
 *
 * Usage examples:
 *
 *   @Audit(module = "SUPPLIER INVOICE", action = "APPROVE")
 *   public CustomResponse<?> approveInvoice(...) { ... }
 *
 *   @Audit(module = "EXPENSES", action = "DELETE")
 *   public CustomResponse<?> delete(Long id) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {

    /** The business module being acted on, e.g. "SUPPLIER INVOICE", "EXPENSES" */
    String module() default "";

    /** The action being performed, e.g. "CREATE", "APPROVE", "DELETE" */
    String action() default "";

    /**
     * Legacy single-value support — used as the activity description if module/action are empty.
     * @deprecated Prefer module() + action() for clarity.
     */
    @Deprecated
    String value() default "";
}
