import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import { ChessBoard } from '../components/ChessBoard';
import { MoveList } from '../components/MoveList';
import { chooseAiMove, type Difficulty } from '../engine';
import { errorMessage } from '../lib/errors';
import { useAuthStore } from '../store/authStore';
import { useGameStore } from '../store/gameStore';
import { connectGameSocket, type GameSocket } from '../ws/socket';
import type { AnalysisReport, MoveRequest, PlayerColor } from '../types';

const STATUS_LABEL: Record<string, string> = {
  WAITING_FOR_OPPONENT: 'Waiting for an opponent',
  IN_PROGRESS: 'In progress',
  WHITE_WON: 'White won',
  BLACK_WON: 'Black won',
  DRAW: 'Draw',
};

const END_REASON_LABEL: Record<string, string> = {
  CHECKMATE: 'by checkmate',
  RESIGNATION: 'by resignation',
  TIMEOUT: 'on time',
  STALEMATE: 'by stalemate',
  INSUFFICIENT_MATERIAL: 'insufficient material',
  FIFTY_MOVE_RULE: 'fifty-move rule',
  THREEFOLD_REPETITION: 'threefold repetition',
};

function formatClock(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

export function GamePage({ gameId }: { gameId: string }) {
  const user = useAuthStore((s) => s.user);
  const game = useGameStore((s) => s.game);
  const setGame = useGameStore((s) => s.setGame);
  const connected = useGameStore((s) => s.connected);
  const setConnected = useGameStore((s) => s.setConnected);
  const aiThinking = useGameStore((s) => s.aiThinking);
  const setAiThinking = useGameStore((s) => s.setAiThinking);
  const error = useGameStore((s) => s.error);
  const setError = useGameStore((s) => s.setError);

  const socketRef = useRef<GameSocket | null>(null);
  const aiFenRef = useRef<string | null>(null);
  const [difficulty, setDifficulty] = useState<Difficulty>('medium');
  const [copied, setCopied] = useState(false);
  const [analysis, setAnalysis] = useState<AnalysisReport | null>(null);

  // Clocks: the server sends remaining milliseconds at snapshot time. We tick locally for the side
  // to move and resync on every broadcast, rather than trusting a client-side countdown.
  const [clockNow, setClockNow] = useState(() => Date.now());
  const receivedAtRef = useRef(Date.now());
  const clockVersionRef = useRef(-1);

  if (game && game.version !== clockVersionRef.current) {
    clockVersionRef.current = game.version;
    receivedAtRef.current = Date.now();
  }

  const clockRunning = Boolean(game) && game?.status === 'IN_PROGRESS';

  useEffect(() => {
    if (!clockRunning) {
      return;
    }
    const timer = window.setInterval(() => setClockNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [clockRunning]);

  useEffect(() => {
    let active = true;
    api
      .getGame(gameId)
      .then((g) => {
        if (active) setGame(g);
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
      setGame(null);
    };
  }, [gameId, setGame, setError]);

  useEffect(() => {
    const socket = connectGameSocket(gameId, {
      onGame: (g) => setGame(g),
      onError: (e) => setError(e.detail),
      onConnected: setConnected,
    });
    socketRef.current = socket;
    return () => {
      socket.disconnect();
      socketRef.current = null;
      setConnected(false);
    };
  }, [gameId, setGame, setError, setConnected]);

  // Which color — if any — this account is seated on. Null means spectator.
  const myColor: PlayerColor | null = useMemo(() => {
    if (!game || !user) return null;
    if (game.whiteUserId === user.id) return 'WHITE';
    if (game.blackUserId === user.id) return 'BLACK';
    return null;
  }, [game, user]);

  const isAi = game?.mode === 'HUMAN_VS_AI';
  const started = game?.status === 'IN_PROGRESS';
  const canMove =
    Boolean(game) && started && myColor !== null && (!isAi || game?.turn === myColor) && !aiThinking;

  const submitMove = useCallback(
    (move: MoveRequest) => {
      setError(null);
      if (connected && socketRef.current) {
        socketRef.current.sendMove(move);
      } else {
        api
          .move(gameId, move)
          .then(setGame)
          .catch((e) => setError(errorMessage(e)));
      }
    },
    [connected, gameId, setGame, setError],
  );

  const handleMove = useCallback(
    (from: string, to: string, promotion?: string) => {
      submitMove({ from, to, promotion: promotion ?? null });
    },
    [submitMove],
  );

  // Drive the engine when it is the computer's turn.
  useEffect(() => {
    if (!game || !isAi || game.status !== 'IN_PROGRESS' || myColor === null || game.turn === myColor) {
      return;
    }
    if (aiFenRef.current === game.fen) {
      return;
    }
    aiFenRef.current = game.fen;

    let cancelled = false;
    setAiThinking(true);
    chooseAiMove(game.fen, difficulty)
      .then((uci) => {
        if (cancelled || !uci) return;
        submitMove({
          from: uci.slice(0, 2),
          to: uci.slice(2, 4),
          promotion: uci.length > 4 ? uci[4] : null,
        });
      })
      .catch((e) => {
        if (!cancelled) setError(errorMessage(e));
      })
      .finally(() => {
        if (!cancelled) setAiThinking(false);
      });

    return () => {
      cancelled = true;
    };
  }, [game, isAi, myColor, difficulty, submitMove, setAiThinking, setError]);

  const analysisGameId = game?.id;
  const gameOver = game
    ? game.status === 'WHITE_WON' || game.status === 'BLACK_WON' || game.status === 'DRAW'
    : false;

  // Analysis is produced asynchronously once the game ends, so poll briefly for it rather than
  // showing "no analysis" the instant the result lands.
  useEffect(() => {
    if (!gameOver || !analysisGameId) {
      setAnalysis(null);
      return;
    }
    let active = true;
    let attempts = 0;

    const load = async () => {
      try {
        const report = await api.analysis.forGame(analysisGameId);
        if (active) setAnalysis(report);
      } catch {
        attempts += 1;
        if (active && attempts < 10) {
          window.setTimeout(() => void load(), 2000);
        }
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [gameOver, analysisGameId]);

  const shareUrl = useMemo(
    () => `${window.location.origin}${window.location.pathname}#/game/${gameId}`,
    [gameId],
  );

  if (!game) {
    return <p className="muted">{error ?? 'Loading game…'}</p>;
  }

  const orientation = myColor === 'BLACK' ? 'black' : 'white';

  const clockElapsed = game.status === 'IN_PROGRESS' ? Math.max(0, clockNow - receivedAtRef.current) : 0;
  const whiteRemaining =
    game.whiteTimeMs === null ? null : Math.max(0, game.whiteTimeMs - (game.turn === 'WHITE' ? clockElapsed : 0));
  const blackRemaining =
    game.blackTimeMs === null ? null : Math.max(0, game.blackTimeMs - (game.turn === 'BLACK' ? clockElapsed : 0));
  const canJoin = game.status === 'WAITING_FOR_OPPONENT' && myColor === null && !isAi;

  const join = () => {
    if (connected && socketRef.current) {
      socketRef.current.join();
    } else {
      api
        .joinGame(gameId)
        .then(setGame)
        .catch((e) => setError(errorMessage(e)));
    }
  };

  const resign = () => {
    if (connected && socketRef.current) {
      socketRef.current.resign();
    } else {
      api
        .resign(gameId)
        .then(setGame)
        .catch((e) => setError(errorMessage(e)));
    }
  };

  // Draws go over REST: unlike moves there is no live-update requirement that justifies a
  // socket message, and the server broadcast brings both clients up to date either way.
  const offerOrAcceptDraw = () => {
    setError(null);
    api
      .offerDraw(gameId)
      .then(setGame)
      .catch((e) => setError(errorMessage(e)));
  };

  const declineDraw = () => {
    setError(null);
    api
      .declineDraw(gameId)
      .then(setGame)
      .catch((e) => setError(errorMessage(e)));
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError('Could not copy the link');
    }
  };

  return (
    <div className="game-layout">
      <section className="game-board">
        {blackRemaining !== null && whiteRemaining !== null && (
          <div className="clocks">
            <span className={game.turn === 'BLACK' ? 'clock active' : 'clock'}>
              {game.blackPlayer ?? 'Black'}
              <b className={blackRemaining < 20_000 ? 'low' : ''}>{formatClock(blackRemaining)}</b>
            </span>
            <span className={game.turn === 'WHITE' ? 'clock active' : 'clock'}>
              {game.whitePlayer ?? 'White'}
              <b className={whiteRemaining < 20_000 ? 'low' : ''}>{formatClock(whiteRemaining)}</b>
            </span>
          </div>
        )}
        <ChessBoard fen={game.fen} orientation={orientation} canMove={canMove} onMove={handleMove} />
      </section>

      <aside className="game-side">
        <div className="panel">
          <div className="row">
            <h2>{STATUS_LABEL[game.status] ?? game.status}</h2>
            <span className={connected ? 'badge badge-on' : 'badge badge-off'}>
              {connected ? 'live' : 'offline'}
            </span>
          </div>
          {game.result && <p className="result">{game.result}</p>}
          {game.endReason && <p className="muted small">{END_REASON_LABEL[game.endReason] ?? game.endReason}</p>}
          <ul className="players">
            <li className={game.turn === 'WHITE' ? 'active' : ''}>
              <span className="dot dot-white" /> {game.whitePlayer ?? '—'}
              {myColor === 'WHITE' && <span className="muted small"> (you)</span>}
            </li>
            <li className={game.turn === 'BLACK' ? 'active' : ''}>
              <span className="dot dot-black" /> {game.blackPlayer ?? '—'}
              {myColor === 'BLACK' && <span className="muted small"> (you)</span>}
            </li>
          </ul>
          {myColor === null && !canJoin && <p className="muted small">You are watching this game.</p>}
          {aiThinking && <p className="muted">Stockfish is thinking…</p>}
          {error && <p className="error">{error}</p>}
          <div className="actions">
            {canJoin && <button onClick={join}>Join this game</button>}
            {started && myColor !== null && game.drawOfferBy === null && (
              <button className="secondary" onClick={offerOrAcceptDraw}>
                Offer draw
              </button>
            )}
            {started && myColor !== null && game.drawOfferBy !== null && game.drawOfferBy !== myColor && (
              <>
                <button onClick={offerOrAcceptDraw}>Accept draw</button>
                <button className="secondary" onClick={declineDraw}>
                  Decline
                </button>
              </>
            )}
            {started && myColor !== null && game.drawOfferBy === myColor && (
              <span className="muted small">Draw offered — waiting for your opponent…</span>
            )}
            {started && myColor !== null && (
              <button className="secondary" onClick={resign}>
                Resign
              </button>
            )}
            <button className="secondary" onClick={() => void copyLink()}>
              {copied ? 'Copied!' : 'Copy link'}
            </button>
          </div>
          {isAi && myColor !== null && (
            <label className="field">
              Difficulty
              <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
                <option value="easy">Easy</option>
                <option value="medium">Medium</option>
                <option value="hard">Hard</option>
              </select>
            </label>
          )}
        </div>

        <div className="panel">
          <h3>Moves</h3>
          <MoveList moves={game.moves} />
        </div>

        {gameOver && (
          <div className="panel">
            <h3>Analysis</h3>
            {analysis ? (
              <>
                <div className="analysis-grid">
                  <span className="muted">White</span>
                  <span>{analysis.accuracyWhite.toFixed(1)}%</span>
                  <span className="muted">Black</span>
                  <span>{analysis.accuracyBlack.toFixed(1)}%</span>
                  <span className="muted">Blunders</span>
                  <span>
                    {analysis.blundersWhite} / {analysis.blundersBlack}
                  </span>
                  <span className="muted">Moves</span>
                  <span>{analysis.movesAnalysed}</span>
                </div>
                {!analysis.engineBacked && (
                  <p className="muted small">
                    Material heuristic, not an engine evaluation — it catches hung pieces and
                    misses positional errors.
                  </p>
                )}
              </>
            ) : (
              <p className="muted">Analysis is being generated…</p>
            )}
          </div>
        )}

        <p className="muted small">Game id: {game.id}</p>
      </aside>
    </div>
  );
}
