package com.EduePoa.EP.Multitenancy.config;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HibernateFilterInterceptorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Filter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private HibernateFilterInterceptor interceptor;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("preHandle - Non-admin users")
    class NonAdminTests {

        @Test
        @DisplayName("should enable tenant filter for non-admin user with tenant context set")
        void enablesFilterForNonAdmin() {
            TenantContext.setCurrentTenant("bureti-high");
            setAuthentication("teacher@bureti.com", "ROLE_Teacher");

            when(entityManager.unwrap(Session.class)).thenReturn(session);
            when(session.enableFilter("tenantFilter")).thenReturn(filter);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(session).enableFilter("tenantFilter");
            verify(filter).setParameter("tenantId", "bureti-high");
        }

        @Test
        @DisplayName("should not enable filter when tenant context is not set")
        void doesNotEnableFilterWhenNoContext() {
            setAuthentication("teacher@bureti.com", "ROLE_Teacher");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verifyNoInteractions(entityManager);
        }
    }

    @Nested
    @DisplayName("preHandle - Platform_Admin without X-Tenant-ID header")
    class PlatformAdminNoCrossTests {

        @Test
        @DisplayName("should NOT enable tenant filter for Platform_Admin without X-Tenant-ID header")
        void bypassesFilterForPlatformAdmin() {
            TenantContext.setCurrentTenant("bureti-high");
            setAuthentication("admin@edupoa.com", "ROLE_Platform_Admin");
            when(request.getHeader("X-Tenant-ID")).thenReturn(null);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verifyNoInteractions(entityManager);
        }

        @Test
        @DisplayName("should NOT enable filter for Platform_Admin with blank X-Tenant-ID header")
        void bypassesFilterForPlatformAdminBlankHeader() {
            TenantContext.setCurrentTenant("bureti-high");
            setAuthentication("admin@edupoa.com", "ROLE_Platform_Admin");
            when(request.getHeader("X-Tenant-ID")).thenReturn("   ");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verifyNoInteractions(entityManager);
        }
    }

    @Nested
    @DisplayName("preHandle - Platform_Admin with X-Tenant-ID header")
    class PlatformAdminScopedTests {

        @Test
        @DisplayName("should enable tenant filter when Platform_Admin uses X-Tenant-ID header")
        void enablesFilterForPlatformAdminWithTenantHeader() {
            TenantContext.setCurrentTenant("nairobi-academy");
            setAuthentication("admin@edupoa.com", "ROLE_Platform_Admin");
            when(request.getHeader("X-Tenant-ID")).thenReturn("nairobi-academy");

            when(entityManager.unwrap(Session.class)).thenReturn(session);
            when(session.enableFilter("tenantFilter")).thenReturn(filter);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(session).enableFilter("tenantFilter");
            verify(filter).setParameter("tenantId", "nairobi-academy");
        }
    }

    @Nested
    @DisplayName("afterCompletion")
    class AfterCompletionTests {

        @Test
        @DisplayName("should clear tenant context after request completes")
        void clearsTenantContext() {
            TenantContext.setCurrentTenant("bureti-high");

            interceptor.afterCompletion(request, response, new Object(), null);

            assertThat(TenantContext.isSet()).isFalse();
        }
    }

    private void setAuthentication(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
