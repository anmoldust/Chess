# CCMMD microservice build — progress and handover

Written while you were asleep. Everything below was verified by running it, not by reading it.
**Status: all six stages complete. The full stack runs under `docker compose up` and the
end-to-end acceptance script passes every check.** Section 3 records a false alarm worth reading —
it changed how verification is done in this repo.

> **AI opponent:** engine-service bakes in **Stockfish 19** (see `infra/engine.Dockerfile`) and
> drives it over UCI. Verified: `e2e4` → `e7e5` at depth 10, and the service logs
> `engine started: Stockfish 19`. Outside Docker, point `CCMMD_ENGINE_BINARY` at any UCI engine;
> with it unset the service falls back to a random legal move and says so in its name.
>
> **Analysis is engine-backed.** `analysis-service` drives Stockfish over UCI (the same binary the
> engine image bakes in) and reports centipawn-loss per move, so `engineBacked=true` and quiet
> positional mistakes count. With no engine configured it falls back to the earlier material
> heuristic and says so (`engineBacked=false`). See `docs/api-contract.md`.

Plan of record: `~/.commandcode/plans/microservices-split.md`

---

## 1. Where things stand

| Stage | State | Evidence |
| --- | --- | --- |
| 0 — platform modules + infra | **Done, verified** | 13 tests green; `docker compose up` → postgres/rabbitmq/redis all healthy; 5 databases + 5 logins created |
| 1a — auth-service | **Done, verified against real Postgres + RabbitMQ** | 21 tests green; live register/login/refresh/JWKS; `user.registered` published to the broker |
| 1b — api-gateway | **Done, verified** | Session, CSRF enforcement, routing and the session→JWT exchange all proven end-to-end |
| 2 — game-service | **Done, verified against real Postgres + RabbitMQ** | 52 tests green, the perft + rules suites moved across intact; cross-service flow works through the gateway; per-service outbox isolation confirmed |
| 3 — engine-service | **Done, verified over the bus** | 12 tests green including a real child-process UCI test; the AI's reply arrives asynchronously and is applied by game-service; poison message lands only in the owning DLQ |
| 4 — matchmaking + presence | **Done, verified end to end** | 16 tests green; two queued players are paired and game-service creates the game with both seated; presence is Redis-backed with heartbeat TTL |
| 5 — notification + analysis | **Done, verified** | 12 tests green; a finished game produced a GAME_ENDED notification for both players and an analysis report (`moves=4`, `engineBacked=false`) |
| 6 — full compose + E2E | **Done, verified** | Nine images build from one Dockerfile; all twelve containers healthy; `scripts/e2e.ps1` passes 10/10 through the gateway |

The pre-split monolith in `backend/` is untouched and still works; it is retired at stage 2.

---

## 2. Run and verify what exists

Infrastructure is **already running** (postgres, rabbitmq, redis containers).

```bash
# one-time: put the platform jars in the local repo so a single service can run standalone
mvn -B install -DskipTests

# auth-service
mvn -B spring-boot:run -pl services/auth-service          # :8081

# game-service  (needs postgres + rabbitmq)
mvn -B spring-boot:run -pl services/game-service          # :8082

# engine-service  (needs rabbitmq)
mvn -B spring-boot:run -pl services/engine-service        # :8083

# matchmaking-service  (needs postgres + rabbitmq)
mvn -B spring-boot:run -pl services/matchmaking-service   # :8084

# presence-service  (needs redis + rabbitmq)
mvn -B spring-boot:run -pl services/presence-service      # :8085

# notification-service  (needs postgres + rabbitmq)
mvn -B spring-boot:run -pl services/notification-service  # :8086

# analysis-service  (needs postgres + rabbitmq + game-service)
mvn -B spring-boot:run -pl services/analysis-service      # :8087

# api-gateway  (needs redis)
mvn -B spring-boot:run -pl services/api-gateway           # :8080

# a stub downstream, to observe the token the gateway attaches
powershell -File infra/tools/stub-downstream.ps1 -Port 8082

# end-to-end gateway check (session, CSRF, token exchange)
powershell -File infra/tools/verify-gateway.ps1

# cross-service game flow (gateway -> auth + game), with an authorization probe
powershell -File infra/tools/verify-game-flow.ps1

# AI move computed over the bus (gateway -> game -> engine -> game)
powershell -File infra/tools/verify-engine-flow.ps1

# two players queue, get paired, and a real game appears
powershell -File infra/tools/verify-matchmaking.ps1

# a finished game fans out to notification + analysis
powershell -File infra/tools/verify-analysis-flow.ps1
```

Whole tree: `mvn -B test` at the root (134 tests: 13 platform, 21 auth-service, 6 gateway,
54 game-service, 12 engine-service, 10 matchmaking-service, 6 presence-service,
3 notification-service, 9 analysis-service).

---

## 3. RESOLVED — the "CSRF bypass" was my verification script, not the gateway

An earlier version of `infra/tools/verify-gateway.ps1` reported that CSRF could be bypassed when
a session existed. **That finding was wrong. There is no bypass.** `SecurityConfig` is correct
and CSRF is enforced.

Root cause: PowerShell's `WebRequestSession` **persists `-Headers` across calls**. The script
passed the CSRF header via `-Headers` on the register call, and from then on *every* call in
that session silently carried it — including the "no CSRF" logout. The header was present the
whole time; the test was lying about what it sent.

Decisive evidence (`infra/tools/probe-csrf.ps1`), with CSRF state set explicitly on
`$session.Headers` rather than via `-Headers`:

```
session.Headers initially        = []
1 no header                      = 403     <- enforced
2 with header                    = 2xx
  session.Headers now            = [X-XSRF-TOKEN]   <- PowerShell stored it
3 "no header" again              = 2xx     <- still sending the stored header
4 header explicitly cleared      = 403     <- CORRECT: this is a real cross-site request
5 logout, header cleared         = 403     <- CORRECT
```

What was true all along: a *missing* CSRF token is rejected, and a *wrong* one is rejected.

**Lesson:** manual probing through a stateful HTTP client is not evidence. The script now sets
CSRF state explicitly before every mutating call, and the automated tests are the real
regression net. Future "findings" from a hand-rolled script deserve the same scepticism.

---

## 4. Bugs found and fixed (all with tests)

1. **Optimistic-lock version never advanced** in the monolith — `version` only increments at
   flush, so responses carried the stale value. Now flushes before mapping.
2. **JWT `exp` is whole seconds** (NumericDate) while our `Instant` has nanoseconds; the
   comparison failed. Fixed by comparing at the precision a token can represent.
3. **`@Scheduled(fixedDelay=…)` fires once immediately at startup**, then waits. The outbox
   relay therefore ran mid-test and dialled the real broker. Added an
   `initialDelayString` knob — also a real production concern with N replicas.
4. **`RSAPrivateKey` has no `getPublicExponent()`** — deriving the public half needs the CRT
   form (`RSAPrivateCrtKey`). Now fails loudly instead of publishing an unusable JWKS.
5. **`SessionUser` was not `Serializable`.** Spring Session serialises to Redis, so the first
   request that saved a session 500'd. In-memory sessions would never have caught this — it was
   only found by running against real Redis.

---

6. **`engine-service` did not scan the platform package**, so the Rabbit topology beans were
   never registered and the context failed at startup. Found because the service simply never
   came up; the platform README documents this wiring and I had skipped it for this service.
7. **Every service's DLQ was bound to `chess.dlx` with `#`**, so a poison message fanned out to
   every service's dead-letter queue — turning a precise alert into noise. Now each queue sets
   an explicit dead-letter routing key and only its owner's DLQ binds it. Found by the DLQ
   drill, which is exactly why the drill exists.
8. **I edited an already-applied Flyway migration.** Adding a column to `V2__games.sql` would
   have failed with a checksum mismatch on the real `game_db`. Reverted V2 and put the change
   in `V3__game_difficulty.sql`, which is what migrations are for.
9. **`UciProtocol.parseBestMove` rejected lines with leading whitespace** — a real engine that
   pads its output would have had a valid move silently dropped.
10. **A test asserted on the outbox globally**, so it passed or failed depending on what other
    tests had left behind. Assertions are now scoped to the game under test. Same class of
    mistake as the CSRF harness: a check that does not control its inputs is not evidence.

11. **The gateway's downstream-auth filter used a hardcoded prefix allow-list** (`/api/games`,
    `/ws`). Adding the matchmaking and presence routes meant those requests were proxied with no
    Bearer token and every call returned 401. Inverted the test — everything the gateway does not
    serve itself is proxied — so a new route inherits identity automatically.
12. **The matchmaking tests needed `@Transactional`.** Without it, a waiting player left behind by
    one test was paired by the next test's matcher call, making count-based assertions
    meaningless; the annotation also supplies the transaction its row locks require.
13. **Two of my verification scripts raced asynchronous events** — they asked for the game
    immediately after the match was announced, before game-service had consumed the event. Both
    now poll. A green check that only passes on a fast machine is not a check.

14. **`AnalysisCalculator` attributed a material loss to the side that played the capture**, not
    the side that lost the material — so hanging a queen was never counted against the player who
    hung it. Caught by the unit test that pins the queen-blunder case.
15. **RESOLVED — `verify-analysis-flow.ps1`'s "analysis missing" was not a fan-out failure.** The
    reports were always being written. Two separate causes: my scripts fetched analysis
    **anonymously**, and neither layer allowed it — gateway and analysis-service both required a
    token for a report about a publicly watchable game. Both now permit `GET /api/analysis/**`,
    which is the consistent rule (you can watch the game, so you can read its analysis). Worth
    noting how this was found: `docker exec ... wget` straight at analysis-service returned `200`
    while the gateway returned `401`, which localised the rejection to the edge in one step.
16. **My health probe used a host port that compose does not publish.** Services are deliberately
    internal-only, so `localhost:8087/actuator/health` refused while the container was healthy —
    which made a perfectly healthy service look down and sent me chasing a phantom.

## 5. Decisions I made on your behalf

1. **Postgres is on host port 5433.** Port 5432 is held by a **native `postgres` process** on
   your machine. I did not touch it. Containers use `postgres:5432` internally.
2. **RabbitMQ uses `rabbitmq:3.13-management`, not `-alpine`** — the alpine image dies on
   Docker Desktop/Windows with `Error when reading /var/lib/rabbitmq/.erlang.cookie: eacces`.
3. **The `oidc-*` stack is stopped** (6 containers) to free 8080/5173. They are **not** a
   compose project (all compose labels are empty), so bring them back with:
   `docker start oidc-backend oidc-frontend oidc-keycloak oidc-grafana oidc-postgres oidc-prometheus`
   Tell me when and I'll do it — or when the gateway stops needing 8080.
4. **`chess-platform-*` left completely alone**, as you asked.
5. **auth-service is the only component that mints tokens** (the refinement you approved). The
   gateway caches the token in the session and refreshes via `POST /api/auth/refresh`
   authorised by a shared internal token — so the signing key lives in exactly one place.
6. **The gateway owns the browser session and CSRF**, and turns a session into a short-lived
   JWT for downstream services. Services stay stateless and validate JWTs only.
7. **Platform JPA entities are per-service, not shared tables.** Each service's Flyway
   migrations create `outbox` and `processed_event` in its *own* database.
8. **Password reset delivers by email when a mail server is configured.** Tokens are real
   (hashed, single-use, expiring). Delivery goes through `ResetNotifier`: with `spring.mail.host`
   set (Mailpit in `docker compose`, or any SMTP relay in production) `SmtpResetNotifier` emails
   the link; with it unset `LoggingResetNotifier` logs the link so the flow still works locally. A
   mail outage is logged rather than surfaced, so the endpoint keeps answering identically for
   known and unknown addresses. `ccmmd.auth.reset.expose-token` can echo the token in the response
   for local use.

---

## 6. What is proven working

**The session→JWT exchange** — the point of the whole gateway design. With the stub downstream
on 8082 recording headers:

```
GET /api/games | Authorization=<none>          <- anonymous, correctly unauthenticated
GET /api/games | Authorization=<none>
GET /api/games | Authorization=Bearer <JWT>    <- after login; browser never saw this token
```

Also: registering through the gateway creates the account in real Postgres, writes the outbox
row in the same transaction, and the relay publishes it to `chess.events` on RabbitMQ
(`published=true | attempts=1 | error=none`). The JWKS exposes `kty=RSA` with no private
exponent. Logout invalidates the session (`/api/auth/me` then reports `authenticated=false`).

---

### Getting real engine strength

`engine-service` speaks UCI and will drive any engine. There is no Stockfish binary on this
machine, so it currently falls back to a random legal move (legal, but very weak). To use
Stockfish, install it and point the service at the executable:

```bash
# then, in the environment of engine-service (or docker-compose):
CCMMD_ENGINE_BINARY=C:\tools\stockfish\stockfish.exe
```

The adapter is tested against a real child process (`UciEngineTest` spawns a stub engine), so
the protocol layer is exercised even without Stockfish present. Difficulty maps to depth and
UCI skill level together: easy 4/3, medium 10/12, hard 15/20.

---

## 7. Next steps

The stack is **currently running** (twelve containers). Stop it with
`docker compose -f infra/docker-compose.yml down`; the SPA is on http://localhost:3000 and the
gateway on http://localhost:8080.

1. **Supply a UCI engine** (section 6) so AI games are actually strong.
2. **Wire the frontend to the four new services** — matchmaking, presence, notifications and
   analysis are API-complete and reachable through the gateway, but no UI calls them yet. This is
   the largest remaining gap between "nine working services" and "an app someone would use".
3. Add real engine-backed analysis. **Done:** `analysis-service` evaluates each move with Stockfish
   over UCI and reports centipawn loss; a depth knob (`ccmmd.engine.depth`) trades sharpness for
   latency, and the material heuristic remains only as the no-engine fallback.
4. **Put this repo under git.** Still the cheapest way to lose all of it.

`infra/tools/` holds five dev aids: `stub-downstream.ps1` (records inbound headers),
`verify-gateway.ps1` (session/CSRF/token-exchange check), `probe-csrf.ps1` (isolates whether a
CSRF finding is real), `verify-game-flow.ps1` (cross-service game flow) and
`verify-engine-flow.ps1` (AI move over the bus). Delete any you'd rather not keep.
