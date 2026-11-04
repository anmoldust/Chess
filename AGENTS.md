# AGENTS.md — CCMMD chess platform

An event-driven chess platform: a **React + TypeScript** SPA, **Java 21 / Spring Boot** services
behind one gateway, **RabbitMQ** for events, **one Postgres database per service**, and
**Stockfish** (driven over UCI) for the computer opponent and post-game analysis.

## Repository layout

```
platform/   shared libraries — common-events, common-amqp, common-security, common-messaging
services/   api-gateway, auth-service, game-service, engine-service, matchmaking-service,
            presence-service, notification-service, analysis-service — one Maven module each
frontend/   Vite + React + TypeScript SPA
infra/      docker-compose.yml, service.Dockerfile, engine.Dockerfile, nginx.conf, postgres/init.sql
docs/       api-contract.md, events.md, PROGRESS.md
scripts/    e2e.ps1 — the end-to-end acceptance run
```

There is no single backend module. The pre-split monolith is gone; anything named `backend/` is a
stale build directory, not source.

## Commands

Backend — Java 21, Maven, one reactor, run from the repository root:

- `mvn test` — every module's tests; they must pass before any commit. The **api-gateway** and
  **presence-service** suites exercise a real Redis, so those two need one running; every other
  suite uses H2 and needs nothing.
- `mvn -B spring-boot:run -pl services/<name>` — run one service (it needs its own infra: Postgres,
  RabbitMQ, Redis as applicable).
- `docker compose -f infra/docker-compose.yml up -d --build` — the whole stack. SPA :3000,
  gateway :8080, Mailpit (reset emails) :8025.
- `docker compose -f infra/docker-compose.yml build <service>` — rebuild one image after changing it.
- `powershell -File scripts/e2e.ps1` — register → matchmake → play to mate → notifications + analysis.

Frontend — from `frontend/`:

- `npm install` — a `NODE_ENV=production` in the shell makes npm silently skip devDependencies; use
  `npm install --include=dev` if that bites.
- `npm run dev` — dev server on :5173, proxying `/api` and `/ws` to the gateway on :8080.
- `npm run build` — `tsc --noEmit` + production build.
- `npm test` — vitest: unit and rendered-component tests.

## Architecture rules (do not violate)

1. **The server is authoritative.** The client may render optimistically, but every move is
   validated and applied server-side with chesslib, in `game-service`. Never trust a
   client-supplied board state; the client only sends `{from, to, promotion?}`.
2. **A move is strongly consistent and single-writer.** It is applied in `game-service` and only
   *then* announced on the bus (transactional outbox); it is never mediated by the broker.
   Matchmaking, presence, notifications and analysis are eventually consistent.
3. **FEN is the canonical board representation.** Persist FEN, not a serialized board array.
4. **chesslib is the only source of truth for legal moves / check / mate.** Do not hand-roll move
   legality. (Repetition is the one exception: the service counts it from the move history it owns.)
5. **Moves are stored in UCI (`e2e4`, `e7e8q`).** SAN, if needed, is derived for display.
6. **Concurrency:** `Game` uses JPA optimistic locking (`@Version`). A rejected move must surface as
   HTTP 409 / a WS error, never a silent overwrite.
7. **Authorization is decided server-side from data.** Never trust a client-claimed identity,
   colour, or seat. Seats are `whiteUserId` / `blackUserId` on `Game`; the caller's identity comes
   from the verified JWT, not from a header. Only the side to move may move.
8. **The gateway owns the browser session; services trust only JWTs.** The browser keeps an httpOnly
   session cookie plus the `X-XSRF-TOKEN` CSRF header on mutations — no token is ever exposed to
   JavaScript. The gateway exchanges the session for a short-lived internal JWT; downstream services
   validate that JWT and nothing else, and the gateway strips any inbound `X-User-*`.
9. **One database per service, Flyway migrations, no cross-service joins.** Never edit an
   already-applied migration — add a new one.
10. **Events are at-least-once.** Producers write an outbox row inside the business transaction;
    consumers are idempotent and drop duplicates by `eventId`.

## Java conventions

- Java 21. Constructor injection only (no field `@Autowired`). No Lombok — write explicit code or
  use `record` for DTOs.
- DTOs are `record`s. Entities are plain classes with a no-arg constructor and accessors.
- Package root: `com.ccmmd.chess`. Layers: `web` → `service` → `repository` → `domain`.
- All controller/service errors flow through `GlobalExceptionHandler` (RFC 7807 problem detail).

## TypeScript / React conventions

- Strict mode on; **no `any`** (use `unknown` + narrowing).
- Function components + hooks only. Shared state lives in the Zustand stores under `src/store/`.
- API access goes through `src/api/client.ts`; WebSocket access through `src/ws/socket.ts`.
  Components never call `fetch`/`WebSocket` directly.
- Engine access goes through `src/engine/` only. Tests sit beside the code (`*.test.ts` / `*.test.tsx`).

## Domain invariants (see the `chess-domain` skill)

- A move that leaves the mover's own king in check is never legal.
- FEN round-trips must be lossless.
- Perft from the start position must match the published node counts.
