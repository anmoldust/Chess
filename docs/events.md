# Event catalogue

Everything travellers on the `chess.events` topic exchange. The routing key **is** the event
type. Payloads are the records in `platform/common-events/src/main/java/.../events/payload/` —
that module is the contract, and `EventEnvelope` wraps every one of them.

Envelope fields: `eventId` (unique; consumers deduplicate on it), `type`, `schemaVersion`,
`occurredAt`, `producer`, `correlationId`, `causationId`, `payload`.

## Published events

| Event | Producer | Consumers | Payload |
| --- | --- | --- | --- |
| `user.registered` | auth-service | notification-service | `userId, email, displayName, registeredAt` |
| `game.created` | game-service | — | `gameId, mode, whiteUserId, blackUserId, aiColor, createdAt` |
| `game.move.played` | game-service | presence-service | `gameId, ply, uci, fen, status, byUserId` |
| `game.ended` | game-service | notification-service, analysis-service | `gameId, status, result, plyCount, whiteUserId, blackUserId` |
| `engine.move.requested` | game-service | engine-service | `gameId, fen, difficulty, correlationId` |
| `engine.move.computed` | engine-service | game-service | `correlationId, gameId, uci, ponder, failed, failureReason` |
| `player.queued` | matchmaking-service | — | `userId, displayName, timeControl, queuedAt` |
| `matchmaking.match.found` | matchmaking-service | game-service, notification-service | `matchId, gameId, whiteUserId, blackUserId, whiteDisplayName, blackDisplayName, timeControl, matchedAt` |
| `presence.updated` | presence-service | — | `userId, online, gameId, at` |
| `notification.sent` | notification-service | — | `notificationId, userId, kind, message, at` |
| `analysis.completed` | analysis-service | — | `gameId, accuracyWhite, accuracyBlack, blundersWhite, blundersBlack, completedAt` |

`analysis.requested` exists in the contract but nothing publishes it yet — on-demand re-analysis
is not wired.

## Request/reply

AI moves are the only request/reply pair:

```
game-service  ──engine.move.requested{correlationId = gameId:ply}──▶  engine-service
game-service  ◀────────engine.move.computed{correlationId}─────────  engine-service
```

The correlation id encodes the **ply**, not just the game. A late or duplicated answer is
therefore recognisable on arrival and ignored rather than applied to the wrong position. This is
why `applyEngineMove` can be correct without any pending-request bookkeeping.

A failed computation still replies (`failed=true`), so a broken engine produces one clear failure
rather than an endless retry. Only a *malformed* message is rejected into the dead-letter queue.

## Guarantees

- **At-least-once delivery.** Every consumer is idempotent: `IdempotentEventProcessor` deduplicates
  on `eventId` inside the same transaction as the handler's effects.
- **Transactional outbox.** Every producer except engine-service and presence-service writes the
  event to an `outbox` row in the same transaction as the state change. The relay publishes it
  afterwards, so state and announcement cannot diverge.
  - engine-service has no durable state to commit, so it publishes directly and relies on
    "publish before ack" for retry.
  - presence-service is ephemeral and self-healing on the next heartbeat, so it publishes
    best-effort and never fails a request because the broker hiccuped.
- **Per-service dead-letter queues.** Each queue sets its own dead-letter routing key; a poison
  message lands only in its owner's DLQ, not in every service's.
- **A move is never made over the bus.** Only the *request* to the engine travels; the move itself
  is validated and applied synchronously in game-service. Chess cannot be eventually consistent.
