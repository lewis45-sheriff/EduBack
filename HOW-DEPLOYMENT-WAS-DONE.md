# How the EduPoa Backend Deployment Was Achieved

A record of how the Spring Boot backend was deployed to AWS EC2 with Docker, an Nginx reverse
proxy, and a GitHub Actions CI/CD pipeline — including the real-world constraints hit along
the way and how they were solved.

## 1. Target environment

- **Server:** AWS EC2, public IP `35.175.109.77`, Ubuntu 26.04.
- **Access:** SSH key `Eduapp.pem`, user `ubuntu`.
- **Repo:** `git@github.com:lewis45-sheriff/EduBack.git`. The Spring project lives in the
  `EP/` subfolder (important: the Dockerfile, compose files, and `pom.xml` are all under `EP/`,
  while the git repo root is one level up).

## 2. Constraints discovered up front

Inspecting the server revealed the key limits that shaped every later decision:

- **RAM:** only ~908 MB, with **no swap**.
- **Disk:** only 8 GB total (~6.9 GB usable), ~4.6 GB free.
- **Docker:** not installed. Git was present.

Implication: building the app image on the server (Maven + JDK build image + JRE runtime image
+ layers) does not fit in RAM or disk. So the design became **build in GitHub Actions, push to
a registry, pull on the server** — the server only ever runs a prebuilt image.

## 3. Server provisioning

1. **Added 2 GB swap** so the JVM + MariaDB don't get OOM-killed on the 908 MB box:
   `fallocate -l 2G /swapfile`, `mkswap`, `swapon`, and an `/etc/fstab` entry for persistence.
2. **Installed Docker** via the official convenience script (`get.docker.com`), which brought
   Docker Engine 29.x and the Compose v2 plugin. Enabled the service and added `ubuntu` to the
   `docker` group.

## 4. Application configuration

- The app already had a `docker` Spring profile (`application-docker.properties`) that reads all
  sensitive values from **environment variables** — this is the profile used in production
  (`SPRING_PROFILES_ACTIVE=docker`). The `prod` profile was avoided because it hardcodes
  `localhost` DB URLs and secrets.
- Swagger (springdoc-openapi 2.5.0) was already enabled, exposing Swagger UI at
  `/swagger-ui/index.html` and the OpenAPI JSON at `/v3/api-docs`.
- The `Dockerfile` (multi-stage: Maven build → `eclipse-temurin:21-jre` runtime) was updated to
  honor a `JAVA_OPTS` env var and default to the `docker` profile:
  ```
  ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=30"
  ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}"]
  ```
  Capping the heap by RAM percentage keeps the JVM from exhausting the small instance.

## 5. Compose stack

A production compose file (`EP/docker-compose.prod.yml`) defines the stack. It pulls a prebuilt
image (`APP_IMAGE`, default `ghcr.io/lewis45-sheriff/eduback:latest`) rather than building:

- `app` (`edupoa-backend`) — the Spring Boot image, profile `docker`, env-var driven config,
  uploads on a named volume (`app_uploads`).
- `db` (`edupoa-db`) — MariaDB 11 with a named volume (`mariadb_data`) and a healthcheck; the
  app waits for the DB to be healthy before starting.

Secrets/config live in a server-side `.env` file (git-ignored) next to the compose file,
seeded from the app's existing properties (DB password, JWT secret, mail, M-Pesa sandbox keys).

## 6. First deploy — and the disk problem

The first attempt to `docker build` on the server **failed at the image-export step with
"no space left on device"**, even though the Maven build itself succeeded. The 8 GB disk
couldn't hold the Maven build image + runtime image + build cache at once.

**Solution that worked within the disk limit:**

1. Build the JAR alone inside a throwaway Maven container (jar written to a mounted volume).
2. Remove the large Maven image immediately to reclaim space.
3. Build a **runtime-only image** (`EP/Dockerfile.runtime`) from just the JRE base + the
   prebuilt `app.jar` + JasperReports.
4. Prune build cache and the leftover jar.

This produced `ghcr.io/lewis45-sheriff/eduback:latest` locally on the server, and
`docker compose -f docker-compose.prod.yml up -d` started the stack. The app logged
"Started EpApplication", seeded the default tenant/admin, and Swagger returned HTTP 200
from `localhost` on the server.

> This runtime-only path was the bootstrap for the very first deploy. Ongoing deploys use the
> GitHub Actions pipeline (section 8), which builds the full image in the cloud where disk is
> not a constraint.

## 7. Nginx reverse proxy (single public entry)

To serve the frontend and API on one origin (avoiding CORS) and to avoid exposing the raw app
port, an Nginx service was added to the compose stack:

- `nginx` (`edupoa-nginx`) — `nginx:1.27-alpine`, the only container with a host port (`80:80`).
- The backend was changed from a published port to `expose: 8085` — **internal only**.
- Config (`EP/nginx/nginx.conf`) routes:

  | Path                  | Upstream            |
  |-----------------------|---------------------|
  | `/api/...`            | backend (`app:8085`)|
  | `/swagger-ui/...`     | backend             |
  | `/v3/api-docs`        | backend             |
  | `/uploads/...`        | backend             |
  | `/ws`                 | backend (WebSocket/SockJS with upgrade headers) |
  | everything else `/`   | static frontend from `/usr/share/nginx/html` with `try_files ... /index.html` (SPA fallback) |

- `client_max_body_size 120m` matches the app's multipart limits (bulk uploads, logos).
- The frontend build directory is mounted from `/opt/edupoa/frontend`. A placeholder page is
  served until the real frontend build is dropped in.

Verified through Nginx on port 80: `/` = 200, `/swagger-ui/index.html` = 200, `/v3/api-docs`
= 200, `/api/v1/auth/login` reaches Spring Security. Confirmed publicly reachable once the
security group allowed inbound port 80.

## 8. CI/CD pipeline (GitHub Actions)

`.github/workflows/deploy.yml` (at the **repo root** — GitHub only runs workflows from the
root `.github/workflows`, which mattered here because the app is nested in `EP/`). On push to
`main` or `Lewis`, or manual dispatch:

1. **build-and-push job:** checkout → set up Buildx → log in to GHCR with the built-in
   `GITHUB_TOKEN` → build the image with context `./EP` and push
   `ghcr.io/<owner>/eduback:latest` and `:<sha>` (with GitHub Actions layer caching).
2. **deploy job:** SSH to the server (`appleboy/ssh-action`), authenticate to GHCR with a PAT,
   prune to protect the small disk, `docker compose pull` the new image, `up -d`, then prune
   the old image.

**Required GitHub secrets:** `VPS_HOST`, `VPS_USER`, `VPS_PORT`, `VPS_KEY` (full PEM contents),
and `GHCR_TOKEN` (a PAT with `read:packages`, used by the server to pull).

## 9. What must be done outside this automation

- **AWS security group:** inbound **port 80** must be open (done). Port 8085 is no longer needed
  publicly since Nginx fronts everything. Add port 443 later for HTTPS.
- **HTTPS:** currently plain HTTP. With a domain pointed at the server, add Let's Encrypt/TLS to
  the Nginx service. Needed before serving a production frontend over HTTPS (mixed-content).
- **Secret rotation:** the seeded `.env` contains real mail and sandbox M-Pesa credentials;
  rotate the JWT secret and mail app password for real production use.

## 10. Public endpoints

- Swagger UI: `http://35.175.109.77/swagger-ui/index.html`
- OpenAPI JSON: `http://35.175.109.77/v3/api-docs`
- API base: `http://35.175.109.77/api/v1/`
- WebSocket: `http://35.175.109.77/ws`

## 11. File map (in the repo)

| File | Purpose |
|------|---------|
| `EP/Dockerfile` | Multi-stage build image (used by CI) |
| `EP/Dockerfile.runtime` | Runtime-only image for the low-disk first deploy |
| `EP/docker-compose.prod.yml` | The running stack: nginx + backend + db |
| `EP/nginx/nginx.conf` | Reverse-proxy + SPA routing |
| `.github/workflows/deploy.yml` | Build → GHCR → deploy pipeline |
| `DEPLOYMENT.md` | Operator guide (secrets, security group, frontend, ops commands) |
| `FRONTEND-DEPLOY-PROMPT.md` | Prompt to deploy the frontend with the same architecture |

## 12. Common server operations

```bash
ssh -i "Eduapp.pem" ubuntu@35.175.109.77
cd /opt/edupoa
docker compose -f docker-compose.prod.yml ps          # status
docker compose -f docker-compose.prod.yml logs -f app # app logs
docker compose -f docker-compose.prod.yml restart nginx
docker compose -f docker-compose.prod.yml up -d        # apply changes
```
