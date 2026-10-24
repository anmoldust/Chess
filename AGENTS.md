# AGENTS.md — CCMMD chess platform

Production-grade chess platform: **React + TypeScript** frontend, **Java Spring Boot** backend,
online multiplayer over WebSocket, and **Stockfish (WASM)** for the local AI opponent.

## Repository layout

```
backend/    Spring Boot 3 (Java 21, Maven) — authoritative game server
frontend/   Vite + React + TypeScript client
docs/       API + WebSocket contract
.commandcode/  skills and sub-agents for this repo
```

## Commands

Backend (run from `backend/`):
- `mvn test` — unit + rules + perft tests (must pass before any commit)
- `mvn spring-boot:run` — start the API on :8080
- `mvn -q -DskipTests package` — build the jar

Frontend (run from `frontend/`):
- `npm install` — install deps
- `npm run dev` — dev server on :5173 (proxies `/api` and `/ws` to :8080)
- `npm run build` — type-check + production build
- `npm run test` — unit tests (Vitest)

## Architecture rules (do not violate)

1. **The server is authoritative.** The client may render optimistically, but every move is
   validated and applied server-side with chesslib. Never trust a client-supplied board state;
   the client only sends `{from, to, promotion?}`.
2. **FEN is the canonical board representation.** Persist FEN, not a serialized board array.
3. **chesslib is the only source of truth for legal moves / check / mate / draw.** Do not
   hand-roll move legality. It is resolved from JitPack (see `backend/pom.xml`).
4. **Moves are stored in UCI (`e2e4`, `e7e8q`).** SAN, if needed, is derived for display.
5. **One WebSocket topic per game** (`/topic/game/{id}`); clients publish moves to
   `/app/game/{id}/move`. Messages are small JSON payloads — see `docs/api-contract.md`.
6. **Concurrency:** `Game` uses JPA optimistic locking (`@Version`). A rejected move due to a
   concurrent write must surface as HTTP 409 / a WS error, never a silent overwrite.
7. **Authorization is decided server-side from data.** Never trust a client-claimed identity,
   color, or seat. The seat owners are `whiteUserId` / `blackUserId` on `Game`; the caller's
   identity comes from the authenticated session. Only the side to move may move (the owner
   may drive both seats in a `HUMAN_VS_AI` game).
8. **Session-cookie auth, no tokens in JS.** `/api/auth/**` issues a session; mutations
   require the `X-XSRF-TOKEN` CSRF header. `GET /api/games*` is public so games are
   watchable; everything else requires an account.

## Java conventions

- Java 21. Constructor injection only (no field `@Autowired`). No Lombok — write explicit
  code or use `record` for DTOs.
- DTOs are `record`s. Entities are plain classes with a no-arg constructor and accessors.
- Package root: `com.ccmmd.chess`. Layers: `web` → `service` → `repository` → `domain`.
- All controller/service errors flow through `GlobalExceptionHandler` (RFC 7807 problem detail).

## TypeScript / React conventions

- Strict mode on; **no `any`** (use `unknown` + narrowing).
- Function components + hooks only. Shared state in a single Zustand store.
- API access goes through `src/api/client.ts`; WebSocket access through `src/ws/socket.ts`.
  Components never call `fetch`/`WebSocket` directly.
- Engine access goes through `src/engine/` only.

## Domain invariants (see the `chess-domain` skill)

- A move that leaves the mover's own king in check is never legal.
- FEN round-trips must be lossless.
- Perft from the start position must match the published node counts.
