# Tenant Onboarding — Frontend Integration Guide

## User Story

**As a Platform_Admin**, I want to onboard a new school through a guided multi-step flow, so that the school gets a fully configured workspace (tenant record, admin account, initial settings) and can start using the system immediately.

---

## Frontend Flow (Wizard Steps)

1. **School Info** — name, address, phone, email domain, logo upload → validates identifier availability in real-time
2. **Plan Selection** — choose subscription tier (BASIC / PREMIUM)
3. **Review & Submit** — shows summary, calls `POST /api/v1/tenants`
4. **Success** — shows admin credentials, links to configure services

---

## API Endpoints

All endpoints require `Authorization: Bearer <platform_admin_jwt>` unless noted otherwise.

---

### Step 1: Validate Tenant Identifier

Real-time check as the user types the school slug.

```
GET /api/v1/tenants/check-availability?identifier=nairobi-academy
```

**Response (200):**

```json
{
  "identifier": "nairobi-academy",
  "available": true
}
```

If taken:

```json
{
  "identifier": "bureti-high",
  "available": false,
  "suggestion": "bureti-high-2"
}
```

**Validation Rules:**
- 3–63 characters
- Lowercase alphanumeric + hyphens only
- Pattern: `^[a-z0-9]+(-[a-z0-9]+)*$`

---

### Step 2: Register the Tenant

Submits the onboarding form. This single call provisions the entire tenant workspace.

```
POST /api/v1/tenants
```

**Request Body:**

```json
{
  "tenantIdentifier": "nairobi-academy",
  "schoolName": "Nairobi Academy",
  "physicalAddress": "123 Kenyatta Avenue, Nairobi",
  "emailDomain": "nairobi-academy.ac.ke",
  "phoneNumber": "+254712345678",
  "logoUrl": "https://cdn.edupoa.com/logos/nairobi-academy.png",
  "subscriptionPlan": "PREMIUM"
}
```

**Required Fields:** `tenantIdentifier`, `schoolName`, `subscriptionPlan`

**Response (201 Created):**

```json
{
  "id": 2,
  "tenantIdentifier": "nairobi-academy",
  "schoolName": "Nairobi Academy",
  "physicalAddress": "123 Kenyatta Avenue, Nairobi",
  "emailDomain": "nairobi-academy.ac.ke",
  "phoneNumber": "+254712345678",
  "logoUrl": "https://cdn.edupoa.com/logos/nairobi-academy.png",
  "status": "ACTIVE",
  "subscriptionPlan": "PREMIUM",
  "createdOn": "2026-06-30T10:00:00Z",
  "updatedOn": "2026-06-30T10:00:00Z"
}
```

**What happens on the backend:**
- Creates the Tenant record (status: ACTIVE)
- Creates a default School_Admin user: `admin@nairobi-academy.edupoa.com` with temporary password `ChangeMe123!`
- Initializes placeholder configuration entries (M-Pesa, SMS, email, reports)
- Logs a `CREATED` audit entry

**Error Responses:**

| Status | Code | When |
|--------|------|------|
| 409 | `TENANT_CONFLICT` | Identifier already exists |
| 400 | Validation error | Missing/invalid fields |

---

### Step 3: Configure Tenant Services (optional, can be done post-onboarding)

Batch-update service configurations for the new tenant.

```
PUT /api/v1/tenants/{id}/configurations
X-Tenant-ID: nairobi-academy
```

**Request Body:**

```json
{
  "configurations": [
    { "configKey": "mpesa.shortcode", "configValue": "174379", "isSensitive": true },
    { "configKey": "mpesa.passkey", "configValue": "bfb279f9aa...", "isSensitive": true },
    { "configKey": "mpesa.callback_url", "configValue": "https://api.edupoa.com/api/v1/process-call-back", "isSensitive": false },
    { "configKey": "sms.sender_id", "configValue": "NAIROBI_AC", "isSensitive": false },
    { "configKey": "sms.api_key", "configValue": "at_api_key_here", "isSensitive": true },
    { "configKey": "email.sender_address", "configValue": "info@nairobi-academy.ac.ke", "isSensitive": false },
    { "configKey": "email.sender_name", "configValue": "Nairobi Academy", "isSensitive": false },
    { "configKey": "reports.school_name", "configValue": "Nairobi Academy", "isSensitive": false },
    { "configKey": "reports.logo_url", "configValue": "https://cdn.edupoa.com/logos/nairobi-academy.png", "isSensitive": false },
    { "configKey": "reports.address", "configValue": "123 Kenyatta Avenue, Nairobi", "isSensitive": false }
  ]
}
```

**Response (200):**

```json
{
  "tenantIdentifier": "nairobi-academy",
  "configurationsUpdated": 10
}
```

---

### Step 4: Get Onboarding Summary

Retrieve post-onboarding details to display on the success page.

```
GET /api/v1/tenants/{id}/onboarding-summary
```

**Response (200):**

```json
{
  "tenant": {
    "id": 2,
    "tenantIdentifier": "nairobi-academy",
    "schoolName": "Nairobi Academy",
    "status": "ACTIVE",
    "subscriptionPlan": "PREMIUM"
  },
  "adminCredentials": {
    "email": "admin@nairobi-academy.edupoa.com",
    "temporaryPassword": "ChangeMe123!",
    "forcePasswordReset": true
  },
  "configuredServices": {
    "mpesa": true,
    "sms": false,
    "email": false,
    "reports": true
  },
  "nextSteps": [
    "Share admin credentials with the school administrator",
    "School admin logs in and changes the temporary password",
    "Configure SMS and email settings for notifications",
    "Upload school logo and branding for reports"
  ]
}
```

---

## Supporting Endpoints

### Tenant Management (Dashboard)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/v1/tenants` | List all tenants |
| `GET` | `/api/v1/tenants/{id}` | Get tenant details |
| `PUT` | `/api/v1/tenants/{id}` | Update tenant metadata |
| `POST` | `/api/v1/tenants/{id}/suspend` | Suspend a tenant |
| `POST` | `/api/v1/tenants/{id}/reactivate` | Reactivate a tenant |
| `POST` | `/api/v1/tenants/{id}/decommission` | Decommission a tenant |

### Tenant Configuration

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/v1/tenants/{id}/configurations` | Get all configs |
| `PUT` | `/api/v1/tenants/{id}/configurations` | Batch update configs |

### Tenant Audit

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/v1/tenants/{id}/audit-logs` | View lifecycle history |

---

## Lifecycle Actions

### Suspend Tenant

```
POST /api/v1/tenants/{id}/suspend
```

```json
{
  "reason": "Non-payment of subscription fees"
}
```

### Reactivate Tenant

```
POST /api/v1/tenants/{id}/reactivate
```

```json
{
  "reason": "Payment received and verified"
}
```

### Decommission Tenant

```
POST /api/v1/tenants/{id}/decommission
```

```json
{
  "reason": "School permanently closed"
}
```

---

## Error Response Format

All errors follow this structure:

```json
{
  "timestamp": "2026-06-30T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "TENANT_CONFLICT",
  "message": "Tenant identifier already exists: nairobi-academy",
  "tenantId": "nairobi-academy",
  "path": "/api/v1/tenants"
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `TENANT_CONFLICT` | 409 | Duplicate tenant identifier |
| `TENANT_NOT_FOUND` | 401 | Tenant identifier not recognized |
| `TENANT_INACTIVE` | 401 | Tenant is suspended or decommissioned |
| `TENANT_MISMATCH` | 403 | Operation violates tenant isolation |
| `TENANT_OVERRIDE_DENIED` | 403 | Non-admin attempted X-Tenant-ID override |
| `TENANT_CONFIG_MISSING` | 422 | Required configuration not found |
| `CROSS_TENANT_ACCESS` | 403 | Accessing another tenant's data |
| `TENANT_CONTEXT_MISSING` | 500 | Server error — no tenant context |

---

## Authentication Notes

- All tenant management endpoints require the `Platform_Admin` role
- The Platform_Admin uses the `X-Tenant-ID` header to scope operations to a specific tenant when needed
- The frontend should store the JWT token and include it in the `Authorization: Bearer <token>` header
- After onboarding, the new School_Admin logs in with their own credentials scoped to their tenant

---

## Configuration Keys Reference

| Key | Description | Sensitive |
|-----|-------------|-----------|
| `mpesa.shortcode` | M-Pesa business shortcode | Yes |
| `mpesa.passkey` | M-Pesa API passkey | Yes |
| `mpesa.callback_url` | STK push callback URL | No |
| `sms.sender_id` | Africa's Talking sender ID | No |
| `sms.api_key` | Africa's Talking API key | Yes |
| `email.sender_address` | Email from address | No |
| `email.sender_name` | Email from display name | No |
| `reports.school_name` | School name on reports | No |
| `reports.logo_url` | Logo URL for report headers | No |
| `reports.address` | School address on reports | No |

---

## New Endpoints to Implement

These three endpoints need to be added to the backend `TenantController`:

1. **`GET /api/v1/tenants/check-availability?identifier=...`** — availability check
2. **`PUT /api/v1/tenants/{id}/configurations`** — batch config update
3. **`GET /api/v1/tenants/{id}/onboarding-summary`** — post-registration summary

The core registration (`POST /api/v1/tenants`) and all lifecycle endpoints are already implemented.
