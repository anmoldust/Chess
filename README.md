# CCMMD — Chess Platform

A production-shaped, event-driven chess platform: a **React + TypeScript** SPA in front of
**eight Spring Boot services**, communicating over **RabbitMQ**, with **one Postgres database per
service**, **Redis**, and **Stockfish** for the computer opponent and post-game analysis.

| Piece | What it is |
| --- | --- |
| `frontend/` | Vite + React SPA — board, lobby, matchmaking, ratings, notifications, analysis |
| `services/api-gateway` | The only browser-facing edge: session (Redis), CSRF, rate limiting, routing; mints a short-lived internal JWT |
| `services/auth-service` | Accounts, credentials, password reset; the only component that mints tokens |
| `services/game-service` | Authoritative rules, moves, clocks, draw/rematch; its own Postgres |
| `services/engine-service` | Drives Stockfish over UCI to play the computer's moves |
| `services/matchmaking-service` | Pairing and Elo ratings |
| `services/presence-service` | Online / in-game presence (Redis TTL) |
| `services/notification-service` | Per-account notifications, fanned out from events |
| `services/analysis-service` | Post-game analysis — centipawn loss per move, via Stockfish |
| `platform/` | Shared libraries: events, AMQP, security (JWT), messaging (outbox + idempotency) |
| `infra/` | Docker Compose, the service/engine/SPA Dockerfiles, nginx, Postgres bootstrap |

## Run it

One command brings up the whole stack — Postgres, RabbitMQ, Redis, Mailpit, the eight services and
the SPA:

```bash
docker compose -f infra/docker-compose.yml up -d --build
```

Then open:

- **SPA** — http://localhost:3000
- **gateway** — http://localhost:8080
- **Mailpit** (catches password-reset emails) — http://localhost:8025

End-to-end acceptance check (register → matchmake → play to mate → notifications + analysis):

```bash
powershell -File scripts/e2e.ps1
```

## Develop

**Backend** — Java 21, Maven, one reactor from the repository root:

```bash
mvn test                                     # every module
mvn -B spring-boot:run -pl services/game-service
```

The `api-gateway` and `presence-service` suites exercise a real Redis, so `mvn test` is only fully
green with Redis running (`docker compose -f infra/docker-compose.yml up -d redis`, or the whole
stack). Every other suite uses H2 and needs nothing.

**Frontend** — from `frontend/`:

```bash
npm install
npm run dev        # :5173, proxies /api and /ws to the gateway on :8080
npm run build      # tsc --noEmit && vite build
npm test           # vitest
```

## How it fits together

```
browser ──▶ api-gateway ──▶ game-service ──▶ Postgres (rules, moves, clocks)
 (SPA :3000)   │  session (Redis)        ▲
               │  mints internal JWT     │  chess.events (RabbitMQ topic exchange)
               ▼                         │
     auth · matchmaking · presence · engine · notification · analysis
```

The gateway owns the browser session and exchanges it for a short-lived JWT that downstream
services validate — so no token is ever exposed to JavaScript and services stay stateless. A move
is a strongly consistent, single-writer operation in `game-service`; matchmaking, presence,
notifications and analysis are eventually consistent over the event bus. The full picture is in
`docs/events.md` and `docs/api-contract.md`.

## Built with Command Code

`.commandcode/` holds the skills and sub-agents used while building this; `AGENTS.md` is the
conventions every contributor — human or agent — follows. `docs/PROGRESS.md` records where the
build stands.
