# CCMMD production runbook

Single VPS, Cloudflare edge, `docker compose`. This is the operator's manual: how to deploy,
roll back, restore, and rotate secrets.

- **Deploy artifacts**: `infra/docker-compose.prod.yml`, `deploy/`
- **Plan / rationale**: `~/.commandcode/plans/vps-production-deployment.md`
- **App conventions**: `AGENTS.md`

---

## 1. One-time setup

### 1.1 Server

```bash
# as root, on a fresh Ubuntu 24.04 box
adduser --disabled-password --gecos "" deploy
mkdir -p /home/deploy/.ssh && cp /root/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
usermod -aG sudo,docker deploy

# harden ssh (then: systemctl reload ssh)
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/;s/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config

# firewall: ssh + http/https only, and 80/443 only from Cloudflare
ufw allow 22/tcp
for ip in $(curl -s https://www.cloudflare.com/ips-v4; curl -s https://www.cloudflare.com/ips-v6); do
  ufw allow from "$ip" to any port 80,443 proto tcp
done
ufw enable

apt-get install -y fail2ban unattended-upgrades
```

Install Docker Engine + Compose v2 (Docker apt repo), then a swapfile (the JVM herd is memory-hungry):

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl -w vm.swappiness=10
```

### 1.2 Layout on the VPS

```bash
sudo -u deploy git clone <repo-url> /opt/ccmmd
install -m 600 deploy/.env.production.example /opt/ccmmd/deploy/.env.production   # then edit
mkdir -p /opt/ccmmd/deploy/secrets
```

Put the **Cloudflare Origin Certificate** in place (SSL/TLS → Origin Server → Create Certificate):

```bash
# /opt/ccmmd/deploy/secrets/origin.pem  and  origin.key, both chmod 600, owned by deploy
```

### 1.3 Cloudflare

- DNS: `A @ → <VPS IP>`, **proxied** (orange cloud). Optional `CNAME www → @`.
- SSL/TLS mode: **Full (strict)**. Enable **Always Use HTTPS** and **Brotli**.
- Cache rule: **bypass** cache for `/api/*`, `/ws*`, `/actuator/*`; cache `/assets/*` normally
  (the origin already sends `immutable`, so the default "cache static extensions" rule is enough).
- Optional WAF rate-limit rule on `/api/auth/login`.

### 1.4 Let the VPS pull images from GHCR

If the repo (and therefore the packages) is private, authenticate once on the VPS:

```bash
echo <PAT-with-read:packages> | docker login ghcr.io -u <github-user> --password-stdin
```

### 1.5 GitHub secrets for CD

Repo → Settings → Secrets and variables → Actions:

| secret | value |
| --- | --- |
| `VPS_HOST` | server IP / hostname |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | private key for a key that can SSH to the VPS **and** read the repo (`git pull`) |

The `deploy` job runs `git pull --ff-only && TAG=<sha> deploy/scripts/deploy.sh` on the VPS.

### 1.6 Nightly backups

```bash
sudo cp /opt/ccmmd/deploy/systemd/ccmmd-backup.* /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now ccmmd-backup.timer
```

For off-site copies, install `rclone` and configure a remote named `r2` (Cloudflare R2, S3 API),
then set `R2_BUCKET` in `.env.production`.

---

## 2. Everyday operations

```bash
cd /opt/ccmmd
C="docker compose --env-file deploy/.env.production -f infra/docker-compose.prod.yml"

$C ps                         # status + health
$C logs -f api-gateway        # follow one service
$C restart game-service       # restart one service
$C down                       # stop the whole stack (volumes are kept)
docker stats --no-stream      # memory headroom (must not be pegged at the limits)
```

### Deploy

Normally CI does this. To do it by hand:

```bash
TAG=<git-sha> bash deploy/scripts/deploy.sh
```

The script pulls, recreates, waits for auth/game/gateway to be `UP`, checks the public edge, and
**rolls back automatically** to `deploy/.last-good-tag` if the health gate fails.

### Rollback

```bash
TAG=$(cat deploy/.last-good-tag) docker compose --env-file deploy/.env.production -f infra/docker-compose.prod.yml up -d --remove-orphans
```

---

## 3. Restore a database

```bash
# pick a backup
ls -1dt deploy/backups/*/ | head

# restore one database (example: game_db)
gunzip -c deploy/backups/<stamp>/game_db.sql.gz | \
  docker compose --env-file deploy/.env.production -f infra/docker-compose.prod.yml exec -T postgres \
    psql -U postgres -d game_db
```

Restore into a scratch DB first (`createdb scratch && psql -d scratch`) if you only want to inspect.

---

## 4. Secret rotation

| What | How | Effect |
| --- | --- | --- |
| `CCMMD_INTERNAL_TOKEN` | new value in `.env.production` → `up -d auth-service api-gateway` | new gateway→auth minting; old in-flight tokens lapse quickly |
| DB password | `ALTER USER <svc> WITH PASSWORD '<new>';` in Postgres, then update `.env.production`, then recreate that service | brief reconnect blip |
| `REDIS_PASSWORD` | update `.env.production` → recreate `redis api-gateway presence-service` | sessions survive in the volume; gateway reconnects |
| JWT signing key | delete `jwtkey` volume content and restart auth-service (regenerates) | **all sessions invalidated** — everyone re-logs in |
| SESSION/CSRF cookies | nothing to rotate; they follow the session | — |

> The JWT key lives on the `jwtkey` volume. Back it up: losing it forces every user to sign in again.

---

## 5. Troubleshooting

- **Game page shows "offline"** — the WebSocket did not upgrade. Check `edge-nginx` has the
  `Upgrade`/`Connection "upgrade"` block (it does in `deploy/nginx/edge.conf`) and that Cloudflare
  is not rewriting the request. Cloudflare drops idle sockets at ~100 s, so STOMP heartbeats /
  the client's 3 s reconnect must be working.
- **`/api` returns 502** — `api-gateway` is unhealthy or nginx is pointing at a stale container IP.
  `$C restart edge-nginx` (nginx resolves upstream names at start).
- **Cookie not sent / logged out constantly** — confirm `CCMMD_COOKIE_SECURE=true` and that
  Cloudflare is in Full (strict); the gateway needs `X-Forwarded-Proto: https` (the edge sets it).
- **A data service won't start after adding `no-new-privileges`** — that option is applied only to
  the Java services and the edge for this reason; do not add it to `postgres`/`rabbitmq`.
- **Out of memory** — check `docker stats`; the per-service `mem_limit` values are the first knob.
  Swap is a safety net, not a fix.

---

## 6. What is intentionally not here

- **Horizontal scaling of `game-service`**: it holds an in-memory STOMP `SimpleBroker`, so a second
  replica would drop broadcasts. It needs a broker relay first. The gateway and the SPA can be
  scaled behind the `api_gateway` upstream today.
- **Metrics stack (Prometheus/Grafana)**: skipped to fit 8 GB; health checks + logs + an external
  uptime probe are the current observability.
- **Managed data services**: the plan chose self-hosted; moving Postgres to RDS later is a
  `SPRING_DATASOURCE_URL` change.
