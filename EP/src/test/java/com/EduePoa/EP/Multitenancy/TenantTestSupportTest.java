package com.EduePoa.EP.Multitenancy;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantTestSupport} and {@link TenantAwareTestBase}.
 */
class TenantTestSupportTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void withTenant_runnable_setsTenantDuringExecution() {
        TenantTestSupport.withTenant("bureti-high", () -> {
            assertEquals("bureti-high", TenantContext.getCurrentTenant());
        });
    }

    @Test
    void withTenant_runnable_clearsTenantAfterExecution() {
        TenantTestSupport.withTenant("bureti-high", () -> {
            // no-op
        });
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void withTenant_runnable_clearsTenantAfterException() {
        assertThrows(RuntimeException.class, () ->
                TenantTestSupport.withTenant("bureti-high", () -> {
                    throw new RuntimeException("test error");
                })
        );
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void withTenant_supplier_setsTenantAndReturnsValue() {
        String result = TenantTestSupport.withTenant("nairobi-academy", () -> {
            assertEquals("nairobi-academy", TenantContext.getCurrentTenant());
            return "value-from-tenant";
        });
        assertEquals("value-from-tenant", result);
    }

    @Test
    void withTenant_supplier_clearsTenantAfterExecution() {
        TenantTestSupport.withTenant("nairobi-academy", () -> "result");
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void withTenant_supplier_clearsTenantAfterException() {
        assertThrows(RuntimeException.class, () ->
                TenantTestSupport.withTenant("nairobi-academy", () -> {
                    throw new RuntimeException("test error");
                })
        );
        assertNull(TenantContext.getCurrentTenant());
    }

    /**
     * Test that verifies TenantAwareTestBase properly sets and clears context.
     */
    static class TestableBase extends TenantAwareTestBase {
        @Override
        protected String getTestTenantId() {
            return "custom-tenant";
        }
    }

    @Test
    void tenantAwareTestBase_setsContextOnSetUp() {
        TestableBase base = new TestableBase();
        base.setUpTenantContext();
        assertEquals("custom-tenant", TenantContext.getCurrentTenant());
        base.tearDownTenantContext();
    }

    @Test
    void tenantAwareTestBase_clearsContextOnTearDown() {
        TestableBase base = new TestableBase();
        base.setUpTenantContext();
        base.tearDownTenantContext();
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void tenantAwareTestBase_usesDefaultTenantWhenNotOverridden() {
        TenantAwareTestBase base = new TenantAwareTestBase() {};
        base.setUpTenantContext();
        assertEquals("test-tenant", TenantContext.getCurrentTenant());
        base.tearDownTenantContext();
    }
}
