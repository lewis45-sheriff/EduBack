package com.EduePoa.EP.Multitenancy;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base test class for tests that require a tenant context.
 * <p>
 * Sets up TenantContext before each test and guarantees it is cleared after each test,
 * preventing tenant leakage between test methods.
 * <p>
 * Subclasses can override {@link #getTestTenantId()} to provide a custom tenant identifier.
 * The default tenant identifier is "test-tenant".
 *
 * <p>Usage:
 * <pre>{@code
 * class StudentServiceTest extends TenantAwareTestBase {
 *
 *     @Override
 *     protected String getTestTenantId() {
 *         return "bureti-high";
 *     }
 *
 *     @Test
 *     void shouldCreateStudentWithinTenant() {
 *         // TenantContext is already set to "bureti-high"
 *         studentService.createStudent(newStudent);
 *         assertEquals("bureti-high", newStudent.getTenantId());
 *     }
 * }
 * }</pre>
 *
 * @see TenantContext
 * @see TenantTestSupport
 */
public abstract class TenantAwareTestBase {

    /**
     * Default tenant identifier used in tests.
     */
    protected static final String DEFAULT_TEST_TENANT = "test-tenant";

    /**
     * Returns the tenant identifier to use for this test class.
     * Override this method to provide a custom tenant identifier.
     *
     * @return the tenant identifier for the test
     */
    protected String getTestTenantId() {
        return DEFAULT_TEST_TENANT;
    }

    /**
     * Sets up the TenantContext before each test method.
     */
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setCurrentTenant(getTestTenantId());
    }

    /**
     * Clears the TenantContext after each test method to prevent tenant leakage.
     */
    @AfterEach
    void tearDownTenantContext() {
        TenantContext.clear();
    }
}
