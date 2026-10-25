# Integrating Africa's Talking Bulk SMS

Step-by-step to add **bulk** SMS sending (one API call for many recipients) to the existing
Africa's Talking integration. Reference: https://developers.africastalking.com/docs/sms/sending/bulk

## Where things stand today

- `SmsGatewayService` exposes a single method: `sendSms(String phoneNumber, String message)`.
- `AfricasTalkingSmsService` implements it with OkHttp against `smsConfig.getSms().getUrl()`,
  sending `username`, `to`, `message`, optional `from`, and the `apiKey` header.
- `CommunicationServiceImpl.sendSmsToAllParents(...)` currently **loops** and calls `sendSms`
  once per parent — that's N API calls. Bulk turns this into one call with a comma-separated
  `to` list, which is faster and lighter.

The AT endpoint is the same one you already call; "bulk" just means multiple numbers in `to`,
plus the optional `bulkSMSMode=1` and `enqueue=1` flags for high volume. The JSON response
returns a `Recipients` array with one entry per number.

---

## Step 1 — Add a `sendBulkSms` method to the gateway interface

`Communications/SMS/SmsGatewayService.java`:

```java
public interface SmsGatewayService {
    SmsDispatchResult sendSms(String phoneNumber, String message);

    // NEW: one AT call for many recipients. Returns per-recipient results.
    BulkSmsDispatchResult sendBulkSms(java.util.List<String> phoneNumbers, String message);
}
```

## Step 2 — Add a result type for bulk

New file `Communications/SMS/BulkSmsDispatchResult.java`:

```java
package com.EduePoa.EP.Communications.SMS;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class BulkSmsDispatchResult {
    private final boolean success;          // true if the request was accepted by AT
    private final String errorMessage;      // set when the whole request failed
    private final List<Recipient> recipients; // per-number outcome

    @Getter
    @Builder
    public static class Recipient {
        private final String number;
        private final int statusCode;   // 101/102 = accepted
        private final String status;
        private final String messageId;
        private final String cost;
        private final boolean success;
    }

    public static BulkSmsDispatchResult failed(String errorMessage) {
        return BulkSmsDispatchResult.builder().success(false).errorMessage(errorMessage).build();
    }
}
```

## Step 3 — Implement `sendBulkSms` in `AfricasTalkingSmsService`

Reuse the existing helpers (`normalisePhone`, `resolveSenderId`, `resolveApiKey`, the shared
`httpClient`). Add:

```java
@Override
public BulkSmsDispatchResult sendBulkSms(java.util.List<String> phoneNumbers, String message) {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) {
        return BulkSmsDispatchResult.failed("No recipients — bulk SMS not sent");
    }
    if (message == null || message.isBlank()) {
        return BulkSmsDispatchResult.failed("Message content is blank — bulk SMS not sent");
    }

    // Normalise + dedupe + drop blanks, then join with commas (AT bulk format).
    String to = phoneNumbers.stream()
            .filter(p -> p != null && !p.isBlank())
            .map(this::normalisePhone)
            .distinct()
            .collect(java.util.stream.Collectors.joining(","));

    if (to.isBlank()) {
        return BulkSmsDispatchResult.failed("No valid recipients after normalisation");
    }

    String senderId = resolveSenderId();
    String apiKey = resolveApiKey();
    log.info("[SMS] Bulk dispatch to {} recipients via Africa's Talking", phoneNumbers.size());

    try {
        FormBody.Builder form = new FormBody.Builder()
                .add("username", smsConfig.getUsername())
                .add("to", to)
                .add("message", message)
                .add("bulkSMSMode", "1")   // send as bulk
                .add("enqueue", "1");      // recommended for large batches

        if (senderId != null && !senderId.isBlank()) {
            form.add("from", senderId);
        }

        Request request = new Request.Builder()
                .url(smsConfig.getSms().getUrl())
                .addHeader("Accept", "application/json")
                .addHeader("apiKey", apiKey)
                .post(form.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[SMS] AT bulk HTTP {}: {}", response.code(), body);
                return BulkSmsDispatchResult.failed("HTTP " + response.code() + ": " + body);
            }
            return parseBulkResponse(body);
        }
    } catch (java.io.IOException e) {
        log.error("[SMS] Network error (bulk): {}", e.getMessage(), e);
        return BulkSmsDispatchResult.failed("Network error: " + e.getMessage());
    } catch (Exception e) {
        log.error("[SMS] Unexpected error (bulk): {}", e.getMessage(), e);
        return BulkSmsDispatchResult.failed("Unexpected error: " + e.getMessage());
    }
}

private BulkSmsDispatchResult parseBulkResponse(String json) {
    try {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject smsData = root.getAsJsonObject("SMSMessageData");
        JsonArray recipients = smsData.getAsJsonArray("Recipients");

        java.util.List<BulkSmsDispatchResult.Recipient> out = new java.util.ArrayList<>();
        if (recipients != null) {
            for (var el : recipients) {
                JsonObject r = el.getAsJsonObject();
                int code = r.get("statusCode").getAsInt();
                out.add(BulkSmsDispatchResult.Recipient.builder()
                        .number(r.has("number") ? r.get("number").getAsString() : null)
                        .statusCode(code)
                        .status(r.has("status") ? r.get("status").getAsString() : null)
                        .messageId(r.has("messageId") ? r.get("messageId").getAsString() : null)
                        .cost(r.has("cost") ? r.get("cost").getAsString() : null)
                        .success(code == 101 || code == 102)
                        .build());
            }
        }
        boolean anyAccepted = out.stream().anyMatch(BulkSmsDispatchResult.Recipient::isSuccess);
        return BulkSmsDispatchResult.builder()
                .success(anyAccepted)
                .recipients(out)
                .errorMessage(anyAccepted ? null : "No recipients accepted")
                .build();
    } catch (Exception e) {
        log.error("[SMS] Failed to parse AT bulk response: {}", e.getMessage());
        return BulkSmsDispatchResult.failed("Response parse error: " + e.getMessage());
    }
}
```

> AT status codes: `101` = success (queued/sent), `102` = sent (no delivery report yet). Anything
> else is a per-number failure (e.g. `403` insufficient balance, `406` invalid number).

## Step 4 — Use bulk in `sendSmsToAllParents`

In `CommunicationServiceImpl.sendSmsToAllParents(...)`, replace the per-parent loop that calls
`sendSms` with a single bulk call:

1. Collect all valid parent phone numbers into a `List<String>` (keep a map of number → parent
   so you can record per-recipient results).
2. Call `smsGatewayService.sendBulkSms(numbers, content)` **once**.
3. Iterate `result.getRecipients()` and build your `MessageRecipient` rows from each entry's
   `success`/`status`/`messageId`.

If AT limits the number of recipients per call (typical guidance is to chunk large lists), split
into batches (e.g. 100–500 numbers) and call `sendBulkSms` per batch:

```java
final int BATCH = 300;
for (int i = 0; i < numbers.size(); i += BATCH) {
    var slice = numbers.subList(i, Math.min(i + BATCH, numbers.size()));
    var res = smsGatewayService.sendBulkSms(slice, content);
    // record res.getRecipients() ...
}
```

Keep the existing single-recipient `sendSmsToParentOfStudent(...)` using `sendSms` as-is.

## Step 5 — Configuration (per tenant + defaults)

The service already resolves the API key and sender ID from **tenant configuration** first
(`sms.api_key`, `sms.sender_id`) and falls back to application-level `SmsConfig`. So:

- **Application defaults** in `application.properties` / `application-docker.properties`:
  ```properties
  africastalking.username=sandbox
  africastalking.api-key=<your AT api key>
  africastalking.sms.url=https://api.sandbox.africastalking.com/version1/messaging
  africastalking.sms.sender-id=
  ```
- **Production** (live account): set `africastalking.username=<your AT username>`, the live API
  key, and switch the URL to `https://api.africastalking.com/version1/messaging`.
- **Per tenant** (multi-tenant): store `sms.api_key`, `sms.sender_id` in tenant configuration so
  each school uses its own AT account/sender — no code change needed.

> Note: the current `application.properties` has the AT block but **no `africastalking.api-key`
> and no `africastalking.sms.url`** set. Add both, or the request will fail auth / hit a null URL.

Set these on the server without rebuilding by adding env vars to `/opt/edupoa/.env` and mapping
them in `docker-compose.prod.yml`, e.g.:

```properties
# .env
AT_USERNAME=sandbox
AT_API_KEY=atsk_xxx
AT_SMS_URL=https://api.sandbox.africastalking.com/version1/messaging
```
and in the compose `app` environment: `- africastalking.username=${AT_USERNAME}` etc. (Spring maps
relaxed env names, but explicit mapping is clearest.)

## Step 6 — (Optional) expose a bulk endpoint

If you want an API to send an arbitrary bulk blast, add to `CommunicationController`:

```java
@PostMapping("sms/send-bulk")
@PreAuthorize("hasPermission(null, 'message:send')")
public ResponseEntity<?> sendBulk(@Valid @RequestBody BulkSmsSendRequest request,
                                  Authentication authentication) {
    String username = authentication != null ? authentication.getName() : "system";
    var response = communicationService.sendBulkSms(request.getNumbers(), request.getContent(), username);
    return ResponseEntity.status(response.getStatusCode()).body(response);
}
```

with a `BulkSmsSendRequest { List<String> numbers; String content; }` DTO and a matching
service method that calls `smsGatewayService.sendBulkSms(...)`.

## Step 7 — Test

1. **Sandbox first.** Keep `username=sandbox` and the sandbox URL. Use the AT
   [simulator](https://developers.africastalking.com/simulator) to receive test messages.
2. Unit-test `parseBulkResponse` with a sample AT JSON body (mock the HTTP call).
3. Integration test: hit `sms/send-bulk` (or trigger `sendSmsToAllParents`) with 2–3 sandbox
   numbers and confirm the `Recipients` array maps to your `MessageRecipient` rows.
4. Check `statusCode` handling: 101/102 = accepted; verify a bad number surfaces as failed.

## Step 8 — Go live

- Switch `africastalking.username` to your live username, set the live API key, and set
  `africastalking.sms.url=https://api.africastalking.com/version1/messaging`.
- Register and use an approved **sender ID / short code** (`from`) for production deliverability.
- Ensure the AT account has SMS credit. `403` responses usually mean insufficient balance.
- Monitor logs (`[SMS] ...`) and, if you need delivery confirmation, configure an AT **delivery
  report callback** URL and add a handler (separate from send).

## Build & deploy

```bash
# locally
cd EP && ./mvnw -q clean package -DskipTests

# then let the GitHub Actions pipeline rebuild+deploy on push,
# or rebuild the image on the server:
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44
cd /opt/edupoa && rm -rf src && git clone --depth 1 -b Lewis https://github.com/lewis45-sheriff/EduBack.git src \
  && cd src/EP && docker build -t ghcr.io/lewis45-sheriff/eduback:latest . \
  && cd /opt/edupoa && docker compose -f docker-compose.prod.yml up -d && docker image prune -f
```
