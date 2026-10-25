# SMS Delivery Report (DLR) Callback — Africa's Talking

This adds asynchronous delivery tracking: when Africa's Talking (AT) finishes delivering (or
failing) an SMS, it POSTs a delivery report to our server, and we update the recipient's status.

## How it works

1. When we send an SMS (single or bulk), AT returns a per-recipient `messageId`. We now persist it
   on `message_recipients.provider_message_id`, and set the initial status to **`PENDING`**
   (accepted by AT, awaiting the delivery report). Rejected numbers are stored as **`FAILED`**.
2. AT later calls our callback with the final outcome. We look up the recipient by
   `provider_message_id` and update `delivery_status` + `provider_status` + `delivered_at`.

Status mapping (AT `status` → our `DeliveryStatus`):

| AT status | DeliveryStatus |
|-----------|----------------|
| `Success`, `Delivered` | `DELIVERED` (sets `deliveredAt`) |
| `Sent`, `Submitted`, `Buffered` | `PENDING` (still in flight) |
| `Failed`, `Rejected`, `Undeliverable` | `FAILED` |

## The endpoint

```
POST /api/v1/communication/sms/delivery-report
Content-Type: application/x-www-form-urlencoded
```

Fields AT sends (form-encoded): `id` (the messageId), `status`, `phoneNumber`, `networkCode`,
`failureReason`, `retryCount`. The endpoint always returns HTTP 200 `OK` so AT does not retry.

It is **public** (no JWT) — whitelisted in `SecurityConfig` — because AT cannot present our auth.

## Register the callback URL in Africa's Talking

1. Log in at https://account.africastalking.com and open your app (sandbox or live).
2. Go to **SMS → Settings → Delivery Reports** (a.k.a. "SMS Callbacks" / "Delivery Report URL").
3. Set the **Delivery Report URL** to:
   ```
   http://44.195.19.44/api/v1/communication/sms/delivery-report
   ```
   (Use your domain + HTTPS once TLS is set up — AT prefers HTTPS in production.)
4. Save. AT will POST delivery reports there as messages are processed.

> Sandbox note: in the AT simulator you can manually trigger delivery statuses to test the flow.

## Data change

`MessageRecipient` gained two columns (Hibernate `ddl-auto=update` will add them automatically on
next startup — no manual migration needed):

- `provider_message_id` — AT messageId, used to match reports.
- `provider_status` — last raw AT status string.

## Testing

1. Send an SMS (single or bulk). The recipient row is saved as `PENDING` with a
   `provider_message_id`.
2. Simulate a report (replace the id with a real one from your send):
   ```bash
   curl -X POST http://44.195.19.44/api/v1/communication/sms/delivery-report \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "id=ATXid_abc123&status=Success&phoneNumber=+254712345678"
   ```
3. Confirm the recipient's `delivery_status` flips to `DELIVERED`. Watch logs:
   ```bash
   docker logs -f edupoa-backend | grep "\[SMS-DLR\]"
   ```

## Deploy

New code — rebuild the image and redeploy (env change alone is not enough):

```bash
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44
cd /opt/edupoa && rm -rf src && git clone --depth 1 -b Lewis https://github.com/lewis45-sheriff/EduBack.git src \
  && cd src/EP && docker build -t ghcr.io/lewis45-sheriff/eduback:latest . \
  && cd /opt/edupoa && docker compose -f docker-compose.prod.yml up -d && docker image prune -f
```

Or push to `Lewis`/`main` to let the GitHub Actions pipeline build and deploy.
