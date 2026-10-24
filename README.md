# CCMMD — Chess Platform

A production-shaped chess platform.

- **Frontend:** React 18 + TypeScript (Vite), `react-chessboard`, `chess.js`
- **Backend:** Java 21 + Spring Boot 3, server-authoritative rules via
  [chesslib](https://github.com/bhlangonijr/chesslib), REST + STOMP WebSocket
- **Multiplayer:** real-time games over WebSocket; server validates every move
- **AI:** Stockfish (WASM) runs client-side for local play and analysis

## Quick start

Two terminals.

**1. Backend** (http://localhost:8080)

```bash
cd backend
mvn spring-boot:run
```

**2. Frontend** (http://localhost:5173)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173, create a game, and share the game id with a friend for
online play — or play Stockfish locally.

## How it fits together

```
Browser (React)                    Spring Boot
  ├─ REST  /api/games/**    ──────▶  GameController ─┐
  ├─ WS    /ws (STOMP)      ──────▶  GameSocket      ├─▶ GameService ─▶ chesslib (rules)
  └─ Stockfish WASM (local AI)                       └─▶ GameRepository ─▶ H2 / Postgres
```

The **server owns the truth**: it re-derives legal moves from FEN on every request, so a
buggy or malicious client cannot make an illegal move.

See `docs/api-contract.md` for the exact REST and WebSocket payloads, and `AGENTS.md` for
the conventions this repo follows.

## Built with Command Code

This repo follows the workflow in `AGENTS.md`; the `.commandcode/` directory holds the
`chess-domain` skill and the `test-writer` / `reviewer` sub-agents used while developing it.
