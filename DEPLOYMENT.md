# Deployment Guide — EduPoa Backend

The backend runs on an AWS EC2 instance inside Docker. Images are built and pushed to
GitHub Container Registry (GHCR) by GitHub Actions, then the server pulls and runs them.
The build happens in Actions (not on the server) because the instance is small
(≈900 MB RAM, 8 GB disk).

## Architecture

```
push to main/Lewis
      │
      ▼
GitHub Actions ── build image ──► GHCR (ghcr.io/lewis45-sheriff/eduback:latest)
      │
      └── SSH to EC2 ──► docker compose pull + up
                              │
     Internet ──► :80 ──► [ edupoa-nginx ] ──► [ edupoa-backend :8085 ] ──► [ edupoa-db ]
                              │                    (internal only)
                              └── serves frontend SPA at /
```

Nginx is the single public entry point (port 80). It reverse-proxies API traffic to the
backend and serves the frontend static build, so frontend and API share one origin
(no CORS issues). The backend port 8085 is **not** exposed to the host.

- Nginx container: `edupoa-nginx` (port 80, config `/opt/edupoa/nginx/nginx.conf`)
- App container: `edupoa-backend` (Spring Boot, internal 8085, profile `docker`)
- DB container: `edupoa-db` (MariaDB 11, volume `mariadb_data`)
- Uploads volume: `app_uploads` → `/app/uploads`
- Server working dir: `/opt/edupoa`
  - `docker-compose.prod.yml` — the running stack definition
  - `.env` — secrets/config (NOT in git)
  - `nginx/nginx.conf` — reverse proxy + SPA config
  - `frontend/` — frontend production build served at `/`

### Nginx routes

| Path              | Goes to                                 |
|-------------------|-----------------------------------------|
| `/api/...`        | backend (Spring Boot)                   |
| `/swagger-ui/...` | backend (Swagger UI)                    |
| `/v3/api-docs`    | backend (OpenAPI JSON)                  |
| `/uploads/...`    | backend (uploaded files)                |
| `/ws`             | backend (WebSocket / SockJS)            |
| `/` (everything else) | frontend SPA (`try_files ... /index.html`) |

## Swagger UI

Once port 80 is open in the security group:

- Swagger UI: http://35.175.109.77/swagger-ui/index.html
- OpenAPI JSON: http://35.175.109.77/v3/api-docs
- API base: http://35.175.109.77/api/v1/

## Required: open port 80 in the AWS Security Group

The stack is confirmed running, but AWS blocks inbound 80 by default. Open it once:

1. AWS Console → EC2 → Instances → select the instance (public IP `35.175.109.77`).
2. Security tab → click the attached Security Group.
3. Inbound rules → Edit inbound rules → Add rule:
   - Type: HTTP, Port range: `80`, Source: `0.0.0.0/0`.
   - Keep the existing SSH (22) rule.
   - (Later, when you add TLS: also add HTTPS port `443`.)
4. Save. External access works immediately.

You no longer need port 8085 open — Nginx handles all public traffic on 80.

## Deploying the frontend

Build your frontend for production, then copy the build output into `/opt/edupoa/frontend`
on the server. Nginx serves it at `/` with SPA fallback.

```bash
# From your frontend project (example for Vite/React; adjust to your tooling)
npm run build            # produces dist/ (or build/ for CRA)

# Copy the build to the server
scp -i "Eduapp.pem" -r dist/* ubuntu@35.175.109.77:/opt/edupoa/frontend/

# No restart needed - nginx serves the new files immediately.
```

Point your frontend's API base URL at the same origin, e.g. `/api/v1` (relative), so it
works through Nginx without CORS. WebSocket/SockJS endpoint is `/ws`.

## Required GitHub secrets

Set these in the repo: Settings → Secrets and variables → Actions → New repository secret.

| Secret       | Value                                                        |
|--------------|-------------------------------------------------------------|
| `VPS_HOST`   | `35.175.109.77`                                             |
| `VPS_USER`   | `ubuntu`                                                    |
| `VPS_PORT`   | `22`                                                        |
| `VPS_KEY`    | Full contents of the `Eduapp.pem` private key file          |
| `GHCR_TOKEN` | A GitHub Personal Access Token with `read:packages` scope   |

Notes:
- `VPS_KEY` must be the entire PEM including the `-----BEGIN ...` and `-----END ...` lines.
- `GHCR_TOKEN`: create at GitHub → Settings → Developer settings → Personal access tokens
  (classic) with `read:packages`. The server uses it to pull the image.
- The image build/push uses the built-in `GITHUB_TOKEN`; no extra secret needed for that.
- If the GHCR package is **private**, `read:packages` is required. You can also make the
  package public (GHCR package settings) and skip `GHCR_TOKEN` on the server.

## First automated deploy

The very first image was built directly on the server. After you add the secrets above and
push to `main` or `Lewis`, the pipeline takes over: it builds in Actions, pushes to GHCR, and
the server pulls the new image automatically.

## Manual operations on the server

```bash
ssh -i "Eduapp.pem" ubuntu@35.175.109.77

cd /opt/edupoa
docker compose -f docker-compose.prod.yml ps        # status
docker compose -f docker-compose.prod.yml logs -f app   # app logs
docker compose -f docker-compose.prod.yml restart app   # restart app
docker compose -f docker-compose.prod.yml down          # stop stack
```

## Secrets currently in the server `.env`

The server `.env` holds DB password, JWT secret, mail credentials, and M-Pesa sandbox keys.
These were seeded from `application-prod.properties`. Rotate the JWT secret and mail app
password for real production use, and update `/opt/edupoa/.env` then
`docker compose -f docker-compose.prod.yml up -d`.
