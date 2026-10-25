# Africa's Talking SMS — Getting API Keys & Configuring

What was implemented, how to get your Africa's Talking (AT) credentials, and how to plug them in
via environment variables (kept out of source).

## What's implemented

- **Single SMS** — `POST /api/v1/communication/sms/send-to-parent/{studentId}` (existing) and the
  gateway method `sendSms(phone, message)`.
- **Bulk SMS** — two paths:
  - `POST /api/v1/communication/sms/send-to-all-parents` — now sends via **one batched AT bulk
    call** instead of a per-parent loop.
  - `POST /api/v1/communication/sms/send-bulk` — send to an explicit list of numbers:
    ```json
    { "numbers": ["0712345678", "+254733111222"], "content": "Hello from EduPoa" }
    ```
- The gateway `sendBulkSms(numbers, message)` joins recipients into AT's comma-separated `to`
  field with `bulkSMSMode=1` and `enqueue=1`, and returns a per-recipient result.
- All AT config is now read from **environment variables** (see below).

## Configuration (environment variables)

| Variable | Meaning | Sandbox value | Production value |
|----------|---------|---------------|------------------|
| `AT_USERNAME` | AT app username | `sandbox` | your registered AT username |
| `AT_API_KEY` | AT API key | sandbox key | live API key |
| `AT_SMS_URL` | messaging endpoint | `https://api.sandbox.africastalking.com/version1/messaging` | `https://api.africastalking.com/version1/messaging` |
| `AT_SENDER_ID` | sender ID / short code (optional) | blank | your approved sender ID |

These map to Spring properties `africastalking.username`, `africastalking.api-key`,
`africastalking.sms.url`, `africastalking.sms.sender-id` (defaults baked into
`application.properties` and `application-docker.properties`). Per-tenant overrides
(`sms.api_key`, `sms.sender_id`) still take precedence when set in tenant configuration.

## How to get your API keys

### A. Sandbox (for testing — free, no real messages)

1. Create an account at https://account.africastalking.com and log in.
2. Switch to the **Sandbox** app (top of the dashboard). Its username is always `sandbox`.
3. Go to **Settings → API Key**, generate a key, and copy it.
4. Test delivery with the AT **Simulator** (https://simulator.africastalking.com) — enter a
   phone number there to receive sandbox messages.
5. Set:
   - `AT_USERNAME=sandbox`
   - `AT_API_KEY=<sandbox key>`
   - `AT_SMS_URL=https://api.sandbox.africastalking.com/version1/messaging`

### B. Production (live messages)

1. In the AT dashboard, **Create a live app** (or use your default live app). Its name is your
   **username**.
2. Go to **Settings → API Key** for that app, generate the live key, copy it.
3. **Top up SMS credit** (Billing → add funds). Without credit, sends fail with statusCode `403`
   (insufficient balance).
4. (Recommended) Request a **Sender ID** or **short code** under **SMS → Sender IDs**. Approval
   can take a few days. Until approved, leave `AT_SENDER_ID` blank to use AT's shared shortcode.
5. Set:
   - `AT_USERNAME=<your live app username>`
   - `AT_API_KEY=<live key>`
   - `AT_SMS_URL=https://api.africastalking.com/version1/messaging`
   - `AT_SENDER_ID=<approved sender id>` (optional)

## Where to put the values

### Local development

Set them as environment variables, or temporarily in `application.properties`. Do **not** commit
real keys.

### On the server (Docker)

Edit `/opt/edupoa/.env` and set the `AT_*` values, then restart the app:

```bash
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44
cd /opt/edupoa
nano .env          # set AT_USERNAME, AT_API_KEY, AT_SMS_URL, AT_SENDER_ID
docker compose -f docker-compose.prod.yml up -d   # picks up new env
```

The compose `app` service already forwards `AT_USERNAME`, `AT_API_KEY`, `AT_SMS_URL`,
`AT_SENDER_ID` into the container.

### Per-tenant (multi-tenant)

To give a specific school its own AT account, set tenant configuration keys `sms.api_key` and
`sms.sender_id` for that tenant. The gateway uses them ahead of the global env values.

## Testing

1. **Sandbox smoke test** (single):
   ```bash
   curl -X POST http://44.195.19.44/api/v1/communication/sms/send-to-parent/1 \
     -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
     -d '{"content":"Test single SMS"}'
   ```
2. **Bulk test**:
   ```bash
   curl -X POST http://44.195.19.44/api/v1/communication/sms/send-bulk \
     -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
     -d '{"numbers":["0712345678","0733111222"],"content":"Test bulk SMS"}'
   ```
3. Watch container logs for `[SMS]` lines:
   ```bash
   docker logs -f edupoa-backend | grep "\[SMS\]"
   ```
4. AT response `statusCode`: `101` success, `102` sent (no delivery report yet), other = failure
   (`403` = no balance, `406` = invalid number, `401`/`403` = bad API key/username).

## Security notes

- Never commit `AT_API_KEY`. It lives only in `/opt/edupoa/.env` (git-ignored) and GitHub secrets
  if you wire it into CI.
- Rotate the key from the AT dashboard if it's ever exposed.
- Phone numbers are normalised to E.164 (`+254...`) before sending; store parent numbers as
  `07...`, `01...`, `254...`, or `+254...` and the gateway handles the rest.

## Deploy the code change

The bulk feature is new code, so the image must be rebuilt (not just an env change):

```bash
# via the GitHub Actions pipeline: commit + push to trigger build->GHCR->deploy
# or rebuild on the server:
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44
cd /opt/edupoa && rm -rf src && git clone --depth 1 -b Lewis https://github.com/lewis45-sheriff/EduBack.git src \
  && cd src/EP && docker build -t ghcr.io/lewis45-sheriff/eduback:latest . \
  && cd /opt/edupoa && docker compose -f docker-compose.prod.yml up -d && docker image prune -f
```
