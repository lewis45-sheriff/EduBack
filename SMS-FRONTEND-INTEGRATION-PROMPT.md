# Prompt: Integrate SMS (single + bulk) into the existing frontend

Copy the block below into an AI agent working in your **frontend** repo (or follow it yourself).
It describes the real backend API so the frontend can send a single SMS and bulk SMS.

---

You are adding SMS-sending features to an existing frontend that talks to the EduPoa backend.
The backend already exposes SMS endpoints. Integrate them into the existing communications/
messaging UI, matching the app's current patterns (state management, API client, auth, toasts).

## API base & auth

- Same-origin base path: `/api/v1` (through Nginx). API root for these ca1`
lls:
  `/api/v1/communication/`.
- All endpoints require the caller to be authenticated and to hold the `message:send`
  permission. Send the app's usual auth (JWT — the app uses an `accessToken` cookie; include
  credentials / Authorization header exactly as other authenticated calls in this codebase do).
- Content-Type: `application/json`.

## Standard response envelope

Every endpoint returns this shape (HTTP status mirrors `statusCode`):

```ts
interface CustomResponse<T> {
  message: string;      // human-readable result, safe to toast
  statusCode: number;   // 201 created/sent, 206 partial (dispatch issue), 400 bad input, 500 error
  entity: T | null;
}
```

For SMS sends, `entity` is a `MessageResponse`:

```ts
interface MessageResponse {
  id: number;
  subject: string;
  content: string;
  messageType: string;      // "SMS"
  status: string;
  scheduledAt: string | null;
  sentAt: string | null;
  createdBy: string;
  createdAt: string | null;
  recipients: RecipientResponse[];   // one per number, with per-recipient delivery status
  deliveryStats: DeliveryStatsResponse | null;
}
```

Treat `statusCode`:
- `201` → sent/created (success).
- `206` → accepted but some/all recipients failed at the gateway — show a warning with `message`.
- `400` → validation error (e.g. no numbers / blank content) — show `message`, do not retry blindly.
- `500` → server error — show `message`, allow retry.

## Endpoints to integrate

### 1. Single SMS to a student's parent

```
POST /api/v1/communication/sms/send-to-parent/{studentId}
Body: { "content": string }   // max 1600 chars
```
Sends to the primary guardian of the given student. Use where a user is viewing a student and
wants to message that student's parent.

### 2. Bulk SMS to all parents

```
POST /api/v1/communication/sms/send-to-all-parents
Body: { "content": string }   // max 1600 chars
```
Broadcasts to every student's primary guardian in one action. This can be a large send — show a
confirmation dialog with an estimate ("This will SMS all parents") before calling, and a loading
state while it runs.

### 3. Bulk SMS to an explicit list of numbers

```
POST /api/v1/communication/sms/send-bulk
Body: { "numbers": string[], "content": string }   // numbers non-empty; content max 1600 chars
```
For sending to a chosen set of recipients (e.g. a selected class, a manually entered list, a CSV
paste). Accepts local (`07…`, `01…`) or international (`+254…`, `254…`) formats — the backend
normalises them.

## UI to build (fit into the existing communications module)

1. **Single send**: on a student view, a "Send SMS" action opening a small form with a message
   textarea (show remaining chars toward the 1600 limit and the SMS-segment count: 160 chars per
   segment for GSM-7, 70 for unicode). On submit → endpoint (1).
2. **Bulk to all parents**: a button in the messaging screen → confirmation modal → endpoint (2).
3. **Bulk to selected numbers**: a form where the user can (a) pick recipients from existing
   lists (parents/classes if available) and/or (b) paste/enter numbers, plus the message textarea
   → endpoint (3). Validate at least one number and non-empty content client-side.

After each call:
- On `201`, toast the `message` and, if the current UI shows message history, refresh it or
  optimistically insert the returned `MessageResponse`.
- On `206`, toast a warning using `message` (partial delivery). If `entity.recipients` is present,
  optionally surface how many delivered vs failed.
- On `400`/`500`, toast the error `message`.

## Implementation constraints

- Reuse the project's existing API client / fetch wrapper, auth handling, and toast/notification
  system — do not introduce a new HTTP library.
- Add typed request/response models (`CustomResponse<MessageResponse>`, `MessageResponse`) in the
  app's shared types location.
- Enforce the 1600-char limit and a non-empty message client-side (mirrors backend validation).
- Guard the UI so only users with the `message:send` permission see the actions, consistent with
  how the app gates other permissioned features.
- Keep calls on the relative `/api/v1` origin (no absolute host) so it works through Nginx and
  later under HTTPS.

## Deliverables

1. Typed API functions: `sendSmsToParent(studentId, content)`, `sendSmsToAllParents(content)`,
   `sendBulkSms(numbers, content)`.
2. UI wired into the existing communications/messaging screens for all three flows, with
   confirmation on the "all parents" broadcast, loading and error states, and toasts.
3. Client-side validation (char limit, non-empty, at least one number for the explicit-list case).

---

## Quick reference — example requests

```bash
# Single (to parent of student 1)
curl -X POST /api/v1/communication/sms/send-to-parent/1 \
  -H "Content-Type: application/json" \
  -d '{"content":"Reminder: fees due Friday."}'

# All parents
curl -X POST /api/v1/communication/sms/send-to-all-parents \
  -H "Content-Type: application/json" \
  -d '{"content":"School closes early tomorrow."}'

# Explicit list
curl -X POST /api/v1/communication/sms/send-bulk \
  -H "Content-Type: application/json" \
  -d '{"numbers":["0712345678","+254733111222"],"content":"Meeting at 10am."}'
```
