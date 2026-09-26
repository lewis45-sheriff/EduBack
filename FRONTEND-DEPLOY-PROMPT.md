# Prompt: Deploy the EduPoa frontend to the same EC2 server (same architecture as the backend)

Copy everything in the block below and give it to an AI coding agent (or follow it yourself)
while working inside your **frontend** repository.

---

You are deploying a frontend web app to an existing AWS EC2 server that already hosts a
Spring Boot backend behind Nginx. Reuse the same architecture: Docker image built in GitHub
Actions, pushed to GitHub Container Registry (GHCR), pulled and run on the server. The server
already runs an Nginx reverse proxy that terminates all public traffic on port 80.

## Existing environment (do not break it)

- Server (EC2, Ubuntu, small: ~900 MB RAM, 8 GB disk): public IP `35.175.109.77`.
- SSH: `ssh -i "Eduapp.pem" ubuntu@35.175.109.77` (key lives at `C:\Users\USER\Downloads\Eduapp.pem`).
- Docker + docker compose are installed. A 2 GB swapfile is active. Disk is tight — do NOT
  build Docker images on the server; build in GitHub Actions and pull.
- The backend stack lives at `/opt/edupoa` with `docker-compose.prod.yml` running three
  containers on a shared docker network (`edupoa_default`):
  - `edupoa-nginx`  — public entry on port 80, config at `/opt/edupoa/nginx/nginx.conf`
  - `edupoa-backend`— Spring Boot, internal only on 8085 (service name `app`)
  - `edupoa-db`     — MariaDB 11
- Nginx currently routes: `/api/`, `/swagger-ui/`, `/v3/api-docs`, `/uploads/`, `/ws` → backend;
  everything else → static frontend served from `/opt/edupoa/frontend`.
- API base URL for the frontend is the **relative** path `/api/v1` (same origin, no CORS needed).
  WebSocket/SockJS endpoint is `/ws`.

## Goal

Serve the frontend on the same domain/origin as the API so there are no CORS issues. Two
supported approaches — pick ONE and implement it end to end:

### Approach A (recommended, lowest disk cost): static build served by the existing Nginx

The existing `edupoa-nginx` already serves `/opt/edupoa/frontend` at `/`. Just ship the build there.

1. Ensure the app uses a **relative API base** `/api/v1` (e.g. `VITE_API_BASE_URL=/api/v1` or
   axios `baseURL: '/api/v1'`). No absolute `http://35.175.109.77` URLs — that breaks HTTPS later.
2. Configure the router for history-mode/SPA (the server does `try_files $uri /index.html`).
3. Add a GitHub Actions workflow `.github/workflows/deploy-frontend.yml` that, on push to the
   main branch:
   - Installs deps and builds the production bundle (`npm ci && npm run build`).
   - Copies the build output (`dist/` or `build/`) to the server via SSH/SCP into
     `/opt/edupoa/frontend/` (clear the folder first, then copy). Use the `appleboy/scp-action`
     and `appleboy/ssh-action` GitHub Actions.
   - Required GitHub secrets: `VPS_HOST=35.175.109.77`, `VPS_USER=ubuntu`, `VPS_PORT=22`,
     `VPS_KEY` = full contents of the `Eduapp.pem` private key.
4. No server restart is needed — Nginx serves the new files immediately.

### Approach B: containerized frontend (own Nginx) added to the compose stack

Only if you want the frontend as its own image. Build a small `nginx:alpine` image that
copies the static build in, push it to GHCR (`ghcr.io/lewis45-sheriff/edupoa-frontend`),
add a `frontend` service to `/opt/edupoa/docker-compose.prod.yml`, and make the existing
`edupoa-nginx` proxy `/` to that container instead of serving files directly. This uses more
disk (extra image) on an already-tight box, so prefer Approach A unless there's a reason.

## Constraints & conventions to follow

- Match the backend's deploy model: build in Actions, never on the server (disk is limited).
- Keep the frontend on the **same origin** as the API (`/api/v1`) to avoid CORS.
- Pin dependency versions; use `npm ci` for reproducible builds.
- Do not commit secrets. The PEM key goes only into the `VPS_KEY` GitHub secret.
- After deploying, verify from the internet:
  - `http://35.175.109.77/` loads the app.
  - The app can call `http://35.175.109.77/api/v1/...` successfully.
- Update the security group only if needed — port 80 is already open; no new port required for
  Approach A.

## Deliverables

1. Frontend configured to call the relative `/api/v1` base and to work as an SPA under Nginx.
2. A working GitHub Actions workflow that builds and deploys on push to the main branch.
3. A short `FRONTEND-DEPLOYMENT.md` in the frontend repo documenting the setup and the
   required GitHub secrets.

---

## Quick manual deploy (no pipeline, do it right now)

If you just want it live immediately without wiring CI:

```bash
# In your frontend project
npm ci
npm run build        # produces dist/ (Vite) or build/ (CRA)

# Ship it to the server (adjust dist -> build if needed)
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@35.175.109.77 "rm -rf /opt/edupoa/frontend/* "
scp -i "C:\Users\USER\Downloads\Eduapp.pem" -r dist/* ubuntu@35.175.109.77:/opt/edupoa/frontend/
```

Then open `http://35.175.109.77/` in a browser. Nginx serves the new files with no restart.
