# API & WebSocket contract

All REST paths are under `/api`. Errors use RFC 7807 problem detail
(`application/problem+json`) with `type`, `title`, `status`, `detail`.

Board state is always exchanged as **FEN**. Moves are **UCI** (`e2e4`, `e7e8q`).

## Authentication

Session-cookie auth (Spring Security). The browser sends the `JSESSIONID` cookie
automatically; there is no bearer token.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/register` | `{email, password, displayName}` → `201` `UserDto`, starts a session |
| `POST /api/auth/login` | `{email, password}` → `200` `UserDto`, starts a session |
| `POST /api/auth/logout` | `204`, invalidates the session |
| `GET /api/auth/me` | `{authenticated, user}` — never 401, so the SPA can bootstrap |
| `POST /api/auth/password/forgot` | `{email}` → `202` `{token}`; always `202` for a known or unknown address |
| `POST /api/auth/password/reset` | `{token, password}` → `204`, burns the token |

Password reset: `forgot` issues a single-use, 30-minute token. It answers identically whether or
not the address is registered, so it is not an account-enumeration oracle. `reset` claims it and
sets the new password. `token` in the `forgot` response is null in every real deployment — a mail
provider (or a log line) carries the link; it is populated only when
`ccmmd.auth.reset.expose-token=true`, a development switch. Existing gateway sessions are not
force-invalidated on reset; they lapse with the session timeout.

CSRF: Spring Security writes a readable `XSRF-TOKEN` cookie on the first request. Every
**mutating** request must echo it back in the `X-XSRF-TOKEN` header, or it gets `403`.
(The WebSocket handshake is exempt — CSRF only applies to HTTP.)

`401` when unauthenticated, `403` when authenticated but not allowed.

## Ownership rules

- A game belongs to the accounts seated on it. Spectators may **read** any game (all
  `GET /api/games*` endpoints are public) but cannot move.
- Only the account seated on the side to move may move. In a `HUMAN_VS_AI` game the human
  owns one seat and the computer the other (its user id is `null`); the human's client
  drives the engine, so the owner may submit moves for **both** seats.
- Only a seated player may resign, and only its own seat.
- Only signed-in users may create or join.

## REST

### Create a game

`POST /api/games`

```json
{ "mode": "HUMAN_VS_HUMAN", "preferredColor": "WHITE", "timeControl": "BLITZ" }
```

`mode` ∈ `HUMAN_VS_HUMAN` | `HUMAN_VS_AI`. `preferredColor` ∈ `WHITE` | `BLACK` | `RANDOM`.
`timeControl` ∈ `BULLET` (1+0) | `BLITZ` (5+2) | `RAPID` (15+10) | `CLASSICAL` (30+30); absent or
unknown falls back to `BLITZ`. The creator is the signed-in account; the display name comes from
the account, not the body.

`201 Created`:

```json
{
  "id": "5f1c9d1e-...",
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "status": "WAITING_FOR_OPPONENT",
  "mode": "HUMAN_VS_HUMAN",
  "timeControl": "BLITZ",
  "whitePlayer": "rajat",
  "blackPlayer": null,
  "whiteUserId": "9c1f...",
  "blackUserId": null,
  "aiColor": null,
  "turn": "WHITE",
  "moves": [],
  "version": 0
}
```

`whiteUserId` / `blackUserId` are how a client decides which color it is (`null` = computer
seat or empty). The server never sends a per-viewer field, so one payload can be broadcast.

### List open games

`GET /api/games?status=WAITING_FOR_OPPONENT` → `200` array of game summaries.

### Get a game

`GET /api/games/{id}` → `200` game, or `404`.

### Player game history

`GET /api/games/history?userId={id}&limit={n}` → `200` array of **finished** games, newest first,
as compact summaries (no move list). Public, like every game read — a watchable game is a
watchable game. `limit` defaults to 20 and is capped at 100. Used by the profile page.

### Join a game

`POST /api/games/{id}/join` (no body — you join as your account) → `200` game.
`409` if the game is full, not open, already yours, or is against the computer.

### Make a move

`POST /api/games/{id}/move`

```json
{ "from": "e2", "to": "e4", "promotion": null }
```

`promotion` ∈ `Q`|`R`|`B`|`N` (only for promotion moves). `200` returns the updated game.
`422` if the move is illegal; `403` if it is not your move to make; `409` if the game is not
awaiting that side / concurrent write.

`MoveRecord` shape inside `moves`:

```json
{ "ply": 1, "from": "e2", "to": "e4", "promotion": null, "uci": "e2e4", "fenAfter": "..." }
```

## Ending and repeating a game

| Endpoint | Purpose |
| --- | --- |
| `POST /api/games/{id}/resign` | The seated player resigns; the opponent wins |
| `POST /api/games/{id}/draw` | Offers a draw, or accepts the opponent's open offer |
| `POST /api/games/{id}/draw/decline` | Declines the open offer; play continues |
| `POST /api/games/{id}/rematch` | Offers a rematch, or accepts the opponent's open offer |

Draw and rematch offers both travel in the game DTO (`drawOfferBy`, `rematchOfferBy`), so both
clients see them on the next broadcast without a separate channel. Offering back is the acceptance
— one verb, one button, no way to half-accept twice. When a rematch is agreed, the finished game's
DTO carries `rematchGameId`; both clients open that game, which starts with the colours swapped and
the same clock. Rematch is offered between two humans only.

## WebSocket (STOMP over SockJS)

- Handshake endpoint: `/ws`
- Application prefix: `/app`
- Broker prefix: `/topic`

### Subscribe

Client subscribes to `/topic/game/{id}` and receives the **full game object** (same shape as
the REST response) on every change — `CREATE`, `JOIN`, `MOVE`, `RESIGN`, `END`.

### Send a move

Client publishes to `/app/game/{id}/move`:

```json
{ "from": "e2", "to": "e4", "promotion": null }
```

The session cookie authenticates the handshake, so the server knows which account is
sending; an unauthenticated or unseated sender is rejected. The server validates, persists,
and broadcasts the updated game. On a rejected action it broadcasts to
`/topic/game/{id}/errors`:

```json
{ "code": "ILLEGAL_MOVE", "detail": "e2e5 is not a legal move" }
```

`code` ∈ `ILLEGAL_MOVE` | `FORBIDDEN` | `CONFLICT` | `GAME_NOT_FOUND` | `BAD_REQUEST`.

### Join / resign

`/app/game/{id}/join` and `/app/game/{id}/resign` take an empty body (`{}`); the seat and
color are derived from the authenticated account.

## Ratings

Owned by matchmaking-service, because a rating exists to decide who plays whom. It is updated
from the `game.ended` event, so game-service still owns the result. Only human-vs-human games are
rated; a computer seat has no account and so no rating. A new account starts at **1200**.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/ratings/me` | Your own rating and record |
| `GET /api/ratings/{userId}` | Any player's rating — the game page shows both seats |
| `GET /api/ratings/{userId}/history?limit=20` | That player's rating movements, newest first |
| `GET /api/ratings/leaderboard?limit=20` | Top ranked players (only accounts with a finished game) |

`RatingDto`:

```json
{
  "userId": "9c1f...",
  "displayName": "rajat",
  "rating": 1216,
  "gamesPlayed": 1,
  "wins": 1,
  "losses": 0,
  "draws": 0
}
```

A player with no rating row yet reads as `1200` with an empty record, without a write. Pairing
prefers the closest rating within a time control; the window widens the longer a player waits, so
a small pool still pairs rather than stranding anyone.

Each finished game also records one `RatingChangeDto` per player — `{gameId, oldRating, newRating,
delta, playedAt}` — in the same transaction as the rating update, so a rating and its history
cannot disagree. The profile page joins those to the game history to show the delta next to each
result.

