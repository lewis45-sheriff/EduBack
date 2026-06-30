package com.EduePoa.EP.Multitenancy;

import com.EduePoa.EP.Multitenancy.config.TenantContext;

import java.util.function.Supplier;

/**
 * Test utility class providing helper methods for setting up and tearing down
 * TenantContext in tests. Ensures tenant context is always properly cleaned up
 * even when test code throws exceptions.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // For void operations
 * TenantTestSupport.withTenant("bureti-high", () -> {
 *     studentService.createStudent(newStudent);
 * });
 *
 * // For operations that return a value
 * List<Student> students = TenantTestSupport.withTenant("bureti-high", () -> {
 *     return studentRepository.findAll();
 * });
 * }</pre>
 *
 * @see TenantContext
 * @see TenantAwareTestBase
 */
public final class TenantTestSupport {

    private TenantTestSupport() {
        // Utility class — prevent instantiation
    }

    /**
     * Executes a block of code within the given tenant context.
     * The tenant context is guaranteed to be cleared after execution,
     * regardless of whether the block completes normally or throws an exception.
     *
     * @param tenantId the tenant identifier to set for the duration of the block
     * @param block    the code to execute within the tenant context
     */
    public static void withTenant(String tenantId, Runnable block) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            block.run();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Executes a block of code within the given tenant context and returns the result.
     * The tenant context is guaranteed to be cleared after execution,
     * regardless of whether the block completes normally or throws an exception.
     *
     * @param tenantId the tenant identifier to set for the duration of the block
     * @param block    the code to execute within the tenant context
     * @param <T>      the type of the result
     * @return the result produced by the block
     */
    public static <T> T withTenant(String tenantId, Supplier<T> block) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            return block.get();
        } finally {
            TenantContext.clear();
        }
    }
}
