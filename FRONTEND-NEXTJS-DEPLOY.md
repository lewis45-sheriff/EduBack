# Deploying the Next.js Frontend to the Same EC2 Server

This guide deploys a **Next.js** frontend onto the same EC2 box that runs the EduPoa backend,
behind the same Nginx reverse proxy, so the app and API share one origin (no CORS).

- **Server:** `44.195.19.44` (t3.medium). SSH: `ssh -i "Eduapp.pem" ubuntu@44.195.19.44`.
- **Stack dir:** `/opt/edupoa` (`docker-compose.prod.yml`, `nginx/nginx.conf`, `.env`, `frontend/`).
- **Existing containers:** `edupoa-nginx` (public :80), `edupoa-backend` (internal :8085,
  service name `app`), `edupoa-db` (MariaDB). Shared docker network: `edupoa_default`.
- **API base for the frontend:** relative `/api/v1` (same origin). WebSocket: `/ws`.

## Choose your deployment mode

Next.js can be deployed two ways. Pick based on your app:

| Use... | If your app... |
|--------|----------------|
| **A. Standalone Node container** (recommended default) | uses SSR, App Router server components, API routes, middleware, `next/image` optimization, server actions, or auth on the server |
| **B. Static export** | is fully client-rendered with no server features (`output: 'export'` works without errors) |

If unsure, use **Approach A** — it always works. Approach B is lighter but only valid for
fully-static apps.

---

## Approach A — Standalone Next.js container (recommended)

The frontend becomes its own container. Nginx proxies `/` to it, and keeps routing
`/api`, `/swagger-ui`, `/uploads`, `/ws` to the backend.

### 1. Configure Next.js for standalone output

In `next.config.js` (or `.mjs`):

```js
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  // If you call the API from the browser, use the relative path so it flows through Nginx.
  // e.g. fetch('/api/v1/...'). No NEXT_PUBLIC_API_URL with an absolute host is needed.
};
module.exports = nextConfig;
```

Calling the API:
- **From the browser (client components):** call the **relative** path `/api/v1/...`. Same origin
  through Nginx, no CORS.
- **From the server (server components / route handlers) inside Docker:** the browser-relative
  path won't resolve server-side. Use an env var pointing at the backend on the docker network:
  `API_INTERNAL_URL=http://app:8085/api/v1`. Read it only in server code.

### 2. Add a `Dockerfile` to the frontend repo root

```dockerfile
# ---- deps ----
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---- runtime (standalone) ----
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
# Standalone output bundles a minimal server + only needed node_modules.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
```

Add a `.dockerignore`:

```
node_modules
.next
.git
npm-debug.log
Dockerfile
.dockerignore
```

### 3. Add the frontend service to the server compose file

Edit `/opt/edupoa/docker-compose.prod.yml` and add a `frontend` service (image pulled from GHCR,
same as the backend). Add under `services:`:

```yaml
  frontend:
    image: ${FRONTEND_IMAGE:-ghcr.io/lewis45-sheriff/edupoa-frontend:latest}
    container_name: edupoa-frontend
    expose:
      - "3000"
    environment:
      - NODE_ENV=production
      - API_INTERNAL_URL=http://app:8085/api/v1
    depends_on:
      - app
    restart: unless-stopped
```

### 4. Point Nginx `/` at the frontend container

Edit `/opt/edupoa/nginx/nginx.conf`. Add an upstream and replace the static `location /` block
so it proxies to the Next.js container instead of serving files:

```nginx
    upstream frontend {
        server frontend:3000;
    }
```

```nginx
        # Next.js app (SSR/standalone)
        location / {
            proxy_pass         http://frontend;
            proxy_http_version 1.1;
            proxy_set_header   Host              $host;
            proxy_set_header   X-Real-IP         $remote_addr;
            proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
            proxy_set_header   X-Forwarded-Proto $scheme;
            proxy_set_header   Upgrade           $http_upgrade;
            proxy_set_header   Connection        "upgrade";
        }
```

Keep the existing `/api/`, `/swagger-ui/`, `/v3/api-docs`, `/uploads/`, `/ws` blocks unchanged —
they must be declared **before** the `location /` block so they take precedence.

### 5. CI/CD: build in GitHub Actions, push to GHCR, deploy on the server

Add `.github/workflows/deploy-frontend.yml` to the **frontend** repo:

```yaml
name: Build and Deploy Frontend

on:
  push:
    branches: [ main ]
  workflow_dispatch:

env:
  IMAGE_NAME: ghcr.io/${{ github.repository_owner }}/edupoa-frontend

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    outputs:
      image: ${{ steps.meta.outputs.image }}
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - id: meta
        run: echo "image=$(echo "${{ env.IMAGE_NAME }}" | tr '[:upper:]' '[:lower:]')" >> "$GITHUB_OUTPUT"
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ steps.meta.outputs.image }}:latest
            ${{ steps.meta.outputs.image }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy over SSH
        uses: appleboy/ssh-action@v1.2.0
        env:
          FRONTEND_IMAGE: ${{ needs.build-and-push.outputs.image }}:latest
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_KEY }}
          port: ${{ secrets.VPS_PORT }}
          command_timeout: 15m
          envs: FRONTEND_IMAGE
          script: |
            set -euo pipefail
            cd /opt/edupoa
            echo "${{ secrets.GHCR_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
            export FRONTEND_IMAGE="$FRONTEND_IMAGE"
            docker compose -f docker-compose.prod.yml pull frontend
            docker compose -f docker-compose.prod.yml up -d
            docker exec edupoa-nginx nginx -s reload || docker compose -f docker-compose.prod.yml restart nginx
            docker image prune -f
            docker compose -f docker-compose.prod.yml ps
```

**Required GitHub secrets** (frontend repo → Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|-------|
| `VPS_HOST` | `44.195.19.44` |
| `VPS_USER` | `ubuntu` |
| `VPS_PORT` | `22` |
| `VPS_KEY` | full contents of `Eduapp.pem` |
| `GHCR_TOKEN` | GitHub PAT with `read:packages` (server pulls the image) |

### 6. First deploy

After adding the `frontend` service (step 3) and updating nginx (step 4) on the server, either
push to `main` to let the pipeline run, or do the first one manually on the server once the image
exists in GHCR:

```bash
ssh -i "Eduapp.pem" ubuntu@44.195.19.44
cd /opt/edupoa
docker compose -f docker-compose.prod.yml up -d
docker exec edupoa-nginx nginx -s reload
```

Then open `http://44.195.19.44/`.

---

## Approach B — Static export (only for fully client-side apps)

If your app has no server features:

1. `next.config.js`:
   ```js
   module.exports = { output: 'export', images: { unoptimized: true } };
   ```
2. Build and export:
   ```bash
   npm ci
   npm run build          # with output:'export', this produces ./out
   ```
3. Ship `out/` to the server — the existing Nginx already serves `/opt/edupoa/frontend` at `/`
   with SPA fallback, so no compose or nginx change is needed:
   ```bash
   ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44 "rm -rf /opt/edupoa/frontend/*"
   scp -i "C:\Users\USER\Downloads\Eduapp.pem" -r out/* ubuntu@44.195.19.44:/opt/edupoa/frontend/
   ```
4. Open `http://44.195.19.44/`. Call the API at the relative `/api/v1` path.

> Note: static export disables SSR, API routes, middleware, ISR, and server image optimization.
> If `npm run build` errors about unsupported features under `output: 'export'`, use Approach A.

---

## Verify after deploying

- `http://44.195.19.44/` loads the Next.js app.
- Network tab shows API calls hitting `/api/v1/...` on the same origin (no CORS errors).
- Backend still reachable: `http://44.195.19.44/swagger-ui/index.html`.

## Notes

- **Same origin = no CORS.** Keep browser API calls on the relative `/api/v1` path.
- **Order matters in nginx:** specific locations (`/api/`, `/ws`, etc.) must come before
  `location /` so the frontend catch-all doesn't swallow API routes.
- **HTTPS later:** this is plain HTTP. Once you have a domain + TLS on Nginx, Next.js works the
  same; just make sure `X-Forwarded-Proto` is passed (already in the config above) so Next.js
  builds correct absolute URLs.
- **Elastic IP:** `44.195.19.44` may change if the instance is stopped/started. Allocate an
  Elastic IP (or a domain) so the frontend/back-end URL stays stable.
