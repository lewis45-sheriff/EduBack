# Requirements Document

## Introduction

This document specifies the requirements for converting the EduPoa school management system from a single-tenant application into a multi-tenant platform. The multi-tenancy feature enables multiple schools (tenants) to share a single deployed application instance while maintaining strict data isolation, independent configuration, and per-tenant customization. The system uses a shared database with discriminator column approach, where a `tenant_id` column on every tenant-scoped entity ensures data segregation at the JPA/Hibernate level.

## Glossary

- **Tenant**: A single school organization registered on the EduPoa platform, uniquely identified by a tenant identifier
- **Tenant_Identifier**: A unique, immutable string (slug) assigned to each tenant, used for routing and data discrimination (e.g., "bureti-high", "nairobi-academy")
- **Tenant_Context**: A thread-local holder that stores the current tenant identifier for the duration of a request
- **Tenant_Filter**: A Hibernate filter that automatically appends a `WHERE tenant_id = :tenantId` clause to all queries on tenant-scoped entities
- **Platform_Admin**: A super-administrator with cross-tenant access who manages tenant provisioning and platform-wide configuration
- **School_Admin**: An administrator scoped to a single tenant who manages that school's data, users, and settings
- **Tenant_Resolver**: A component that extracts the tenant identifier from incoming requests (JWT claims, request headers, or subdomain)
- **Shared_Entity**: An entity that is not scoped to any tenant (e.g., platform configuration, tenant registry)
- **Tenant_Scoped_Entity**: An entity that belongs to exactly one tenant, identified by its `tenant_id` column
- **EduPoa_System**: The EduPoa backend application including all modules (Authentication, Students, Fees, Academics, Payments, Transport, Procurement, Communications, Reports)

## Requirements

### Requirement 1: Tenant Registration and Provisioning

**User Story:** As a Platform_Admin, I want to register new school tenants, so that each school gets an isolated workspace within the shared platform.

#### Acceptance Criteria

1. WHEN a Platform_Admin submits a valid tenant registration request, THE EduPoa_System SHALL create a new Tenant record with a unique Tenant_Identifier, school name, contact information, and subscription status
2. WHEN a new Tenant is created, THE EduPoa_System SHALL provision a default School_Admin user account for that tenant
3. WHEN a new Tenant is created, THE EduPoa_System SHALL initialize default configuration data for that tenant (academic terms, grading scales, fee components templates)
4. IF a tenant registration request contains a Tenant_Identifier that already exists, THEN THE EduPoa_System SHALL reject the request with a descriptive conflict error
5. THE EduPoa_System SHALL store tenant metadata including school name, physical address, email domain, phone number, logo URL, and subscription plan

### Requirement 2: Tenant Context Resolution

**User Story:** As a system component, I want to resolve the current tenant from each incoming request, so that all operations are scoped to the correct school.

#### Acceptance Criteria

1. WHEN an authenticated request arrives, THE Tenant_Resolver SHALL extract the Tenant_Identifier from the JWT token claims
2. WHEN the Tenant_Identifier is resolved, THE Tenant_Resolver SHALL store it in the Tenant_Context for the duration of the request
3. IF the Tenant_Identifier in a request does not correspond to an active tenant, THEN THE EduPoa_System SHALL reject the request with a 403 Forbidden response
4. WHEN the request processing completes, THE Tenant_Context SHALL be cleared to prevent tenant leakage between requests
5. WHILE a request is being processed, THE Tenant_Context SHALL remain immutable and consistent across all service layers within that request thread

### Requirement 3: Data Isolation via Discriminator Column

**User Story:** As a School_Admin, I want to access only my school's data, so that student records, financial data, and academic information remain private to each school.

#### Acceptance Criteria

1. THE EduPoa_System SHALL add a non-nullable `tenant_id` column to every Tenant_Scoped_Entity table (Student, User, Grade, FeeStructure, Invoice, Payment, Expense, Transport routes, Procurement records, Communications)
2. WHEN a new Tenant_Scoped_Entity is persisted, THE EduPoa_System SHALL automatically set the `tenant_id` field from the current Tenant_Context
3. WHEN a query is executed against a Tenant_Scoped_Entity, THE Tenant_Filter SHALL automatically append a tenant_id filter condition so only records belonging to the current tenant are returned
4. IF a request attempts to access or modify a Tenant_Scoped_Entity belonging to a different tenant, THEN THE EduPoa_System SHALL reject the operation with a 403 Forbidden response
5. THE EduPoa_System SHALL enforce tenant isolation at the Hibernate session level using Hibernate Filters enabled per-request
6. WHEN a Platform_Admin queries data, THE Tenant_Filter SHALL allow cross-tenant access based on the Platform_Admin role

### Requirement 4: Tenant-Aware Authentication and JWT

**User Story:** As a user, I want my login session to be scoped to my school, so that my credentials and permissions apply only within my school's context.

#### Acceptance Criteria

1. WHEN a user authenticates successfully, THE EduPoa_System SHALL include the user's Tenant_Identifier as a claim in the issued JWT access token
2. WHEN a user authenticates, THE EduPoa_System SHALL validate that the user belongs to the tenant specified in the login request
3. THE EduPoa_System SHALL ensure usernames (emails) are unique within a tenant but may exist across different tenants
4. WHEN a JWT token is validated, THE JwtAuthFilter SHALL extract the Tenant_Identifier claim and set it in the Tenant_Context before downstream processing
5. IF a JWT token contains an invalid or inactive Tenant_Identifier, THEN THE EduPoa_System SHALL reject the token and return a 401 Unauthorized response

### Requirement 5: Tenant-Scoped User and Role Management

**User Story:** As a School_Admin, I want to manage users and roles for my school independently, so that each school has its own staff accounts and permission structure.

#### Acceptance Criteria

1. WHEN a School_Admin creates a new user, THE EduPoa_System SHALL associate that user with the School_Admin's current tenant
2. THE EduPoa_System SHALL scope all Role and RolePermission entities to a tenant, allowing each school to define custom roles
3. WHEN a School_Admin queries the user list, THE EduPoa_System SHALL return only users belonging to that School_Admin's tenant
4. IF a user attempts to access another tenant's user records, THEN THE EduPoa_System SHALL deny the request with a 403 Forbidden response
5. WHEN a Platform_Admin creates a user, THE EduPoa_System SHALL allow specifying the target tenant for that user

### Requirement 6: Tenant-Specific Configuration

**User Story:** As a School_Admin, I want to configure my school's settings independently, so that fee structures, academic calendars, grading systems, and M-Pesa credentials differ per school.

#### Acceptance Criteria

1. THE EduPoa_System SHALL store per-tenant configuration including M-Pesa API credentials (shortcode, passkey, callback URLs), SMS gateway settings, email templates, and report headers
2. WHEN the M-Pesa payment module processes a transaction, THE EduPoa_System SHALL use the M-Pesa credentials configured for the current tenant
3. WHEN the Communications module sends an SMS or email, THE EduPoa_System SHALL use the sender credentials and templates configured for the current tenant
4. WHEN a report is generated via JasperReports, THE EduPoa_System SHALL inject the current tenant's school name, logo, and address into the report header
5. IF a tenant has not configured required credentials for a service, THEN THE EduPoa_System SHALL return a descriptive error indicating the missing configuration rather than falling back to default credentials

### Requirement 7: Tenant Data Migration for Existing Data

**User Story:** As a Platform_Admin, I want to migrate the existing single-tenant data to the multi-tenant schema, so that the current school (Bureti) continues operating seamlessly after the upgrade.

#### Acceptance Criteria

1. WHEN the multi-tenancy migration executes, THE EduPoa_System SHALL create a default tenant record for the existing school data
2. WHEN the multi-tenancy migration executes, THE EduPoa_System SHALL populate the `tenant_id` column on all existing records with the default tenant's identifier
3. WHEN the migration completes, THE EduPoa_System SHALL verify that all existing queries return the same result set as before migration for the default tenant
4. THE EduPoa_System SHALL provide a reversible migration script (rollback) that can remove multi-tenancy columns if needed
5. IF the migration encounters records that cannot be assigned a tenant, THEN THE EduPoa_System SHALL log the affected records and halt the migration with an error report

### Requirement 8: Tenant Lifecycle Management

**User Story:** As a Platform_Admin, I want to suspend, reactivate, or decommission tenants, so that I can manage the platform's school roster over time.

#### Acceptance Criteria

1. WHEN a Platform_Admin suspends a tenant, THE EduPoa_System SHALL reject all authentication attempts for users of that tenant with a message indicating the account is suspended
2. WHEN a Platform_Admin reactivates a suspended tenant, THE EduPoa_System SHALL restore normal access for all users of that tenant
3. WHEN a Platform_Admin decommissions a tenant, THE EduPoa_System SHALL soft-delete all data belonging to that tenant and prevent any future access
4. WHILE a tenant is in suspended state, THE EduPoa_System SHALL preserve all tenant data without modification
5. THE EduPoa_System SHALL maintain an audit log of all tenant lifecycle state changes including the actor, timestamp, and reason

### Requirement 9: Cross-Tenant Query Prevention

**User Story:** As a developer, I want the system to prevent accidental cross-tenant data access at the framework level, so that coding errors cannot lead to data leakage.

#### Acceptance Criteria

1. THE EduPoa_System SHALL implement a JPA EntityListener that validates the Tenant_Context is set before any Tenant_Scoped_Entity is persisted or updated
2. IF a Tenant_Scoped_Entity is about to be persisted without a matching Tenant_Context, THEN THE EduPoa_System SHALL throw an exception and abort the transaction
3. THE EduPoa_System SHALL log a security warning when a tenant_id mismatch is detected between an entity and the current Tenant_Context
4. WHEN integration tests execute, THE EduPoa_System SHALL provide test utilities that set up and tear down Tenant_Context for isolated testing
5. THE EduPoa_System SHALL enforce that all Repository interfaces for Tenant_Scoped_Entities extend a base TenantAwareRepository that applies tenant filtering

### Requirement 10: Tenant-Aware API Routing

**User Story:** As a frontend client, I want API endpoints to work transparently within my tenant context, so that I do not need to pass tenant identifiers in every request body.

#### Acceptance Criteria

1. THE EduPoa_System SHALL resolve tenant context from the authenticated user's JWT token, requiring no additional tenant parameter in request URLs or bodies for standard operations
2. WHEN a Platform_Admin needs to operate on a specific tenant, THE EduPoa_System SHALL accept an optional `X-Tenant-ID` header to override the tenant context
3. IF a non-Platform_Admin user sends an `X-Tenant-ID` header that differs from their JWT tenant claim, THEN THE EduPoa_System SHALL reject the request with a 403 Forbidden response
4. THE EduPoa_System SHALL include the current Tenant_Identifier in all API error responses for debugging purposes
5. WHEN an M-Pesa STK callback is received (unauthenticated), THE Tenant_Resolver SHALL determine the target tenant from the callback payload's account reference or short code mapping
