import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { errorMessage } from '../lib/errors';
import { navigate } from '../lib/useHashRoute';
import { useAuthStore } from '../store/authStore';
import { useGameStore } from '../store/gameStore';
import type { GameDto, GameMode, NotificationDto, PlayerColor, RatingDto } from '../types';

const TIME_CONTROLS = [
  { value: 'BULLET', label: 'Bullet 1+0' },
  { value: 'BLITZ', label: 'Blitz 5+2' },
  { value: 'RAPID', label: 'Rapid 15+10' },
  { value: 'CLASSICAL', label: 'Classical 30+30' },
];

const DEFAULT_TIME_CONTROL = 'BLITZ';

function timeControlLabel(value: string | null): string {
  if (!value) return '';
  return TIME_CONTROLS.find((control) => control.value === value)?.label ?? value;
}

export function LobbyPage() {
  const user = useAuthStore((s) => s.user);
  const setGame = useGameStore((s) => s.setGame);

  const [mode, setMode] = useState<GameMode>('HUMAN_VS_HUMAN');
  const [color, setColor] = useState<PlayerColor>('WHITE');
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [openGames, setOpenGames] = useState<GameDto[]>([]);
  const [joinId, setJoinId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [timeControl, setTimeControl] = useState(DEFAULT_TIME_CONTROL);
  const [createTimeControl, setCreateTimeControl] = useState(DEFAULT_TIME_CONTROL);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [myRating, setMyRating] = useState<RatingDto | null>(null);
  const [leaderboard, setLeaderboard] = useState<RatingDto[]>([]);

  const refreshGames = useCallback(async () => {
    try {
      setOpenGames(await api.listOpenGames());
    } catch {
      setOpenGames([]);
    }
  }, []);

  const refreshNotifications = useCallback(async () => {
    try {
      setNotifications(await api.notifications.list());
    } catch {
      // Notifications are a side panel; a failure here must not break the lobby.
    }
  }, []);

  const refreshRatings = useCallback(async () => {
    try {
      const [mine, board] = await Promise.all([api.ratings.me(), api.ratings.leaderboard(10)]);
      setMyRating(mine);
      setLeaderboard(board);
    } catch {
      // Ratings are an enhancement just like notifications; never break the lobby over them.
    }
  }, []);

  useEffect(() => {
    void refreshGames();
    void refreshNotifications();
    void refreshRatings();
  }, [refreshGames, refreshNotifications, refreshRatings]);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const game = await api.createGame({
        mode,
        preferredColor: color,
        timeControl: createTimeControl,
        difficulty,
      });
      setGame(game);
      navigate({ name: 'game', id: game.id });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const join = async (id: string) => {
    setBusy(true);
    setError(null);
    try {
      const game = await api.joinGame(id);
      setGame(game);
      navigate({ name: 'game', id: game.id });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const findMatch = async () => {
    setBusy(true);
    setError(null);
    try {
      const queued = await api.matchmaking.queue(timeControl);
      setQueuePosition(queued.position);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const cancelMatch = async () => {
    try {
      await api.matchmaking.leave();
    } catch {
      // Leaving is best-effort; the queue entry is cancelled server-side either way.
    }
    setQueuePosition(null);
  };

  // While queued, poll for a pairing. The match is announced by the server, so the client has
  // nothing to compute — it just waits to be told where to go.
  useEffect(() => {
    if (queuePosition === null) {
      return;
    }
    let active = true;

    const poll = async () => {
      try {
        const status = await api.matchmaking.status();
        if (!active) return;
        if (status.matched && status.gameId) {
          setQueuePosition(null);
          setGame(null);
          navigate({ name: 'game', id: status.gameId });
        } else if (!status.queued) {
          setQueuePosition(null);
        }
      } catch {
        // Keep polling; a transient error is not a reason to drop out of the queue.
      }
    };

    void poll();
    const timer = setInterval(() => void poll(), 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [queuePosition, setGame]);

  const joinable = openGames.filter(
    (game) => game.whiteUserId !== user?.id && game.blackUserId !== user?.id,
  );

  return (
    <div className="lobby">
      <div className="panel">
        <h2>Quick match</h2>
        <p className="muted">Get paired with another waiting player.</p>
        <label className="field">
          Time control
          <select
            value={timeControl}
            onChange={(e) => setTimeControl(e.target.value)}
            disabled={queuePosition !== null}
          >
            {TIME_CONTROLS.map((tc) => (
              <option key={tc.value} value={tc.value}>
                {tc.label}
              </option>
            ))}
          </select>
        </label>
        {queuePosition === null ? (
          <button disabled={busy} onClick={() => void findMatch()}>
            Find opponent
          </button>
        ) : (
          <div className="queue-state">
            <p className="muted">Waiting for an opponent…</p>
            <button className="secondary" onClick={() => void cancelMatch()}>
              Cancel
            </button>
          </div>
        )}
        {error && <p className="error">{error}</p>}
      </div>

      <div className="panel">
        <h2>New game</h2>
        <p className="muted">
          Playing as <strong>{user?.displayName}</strong>
        </p>
        {myRating && (
          <p className="muted">
            Rating <strong>{myRating.rating}</strong>
            {myRating.gamesPlayed > 0 && (
              <span>
                {' '}
                · {myRating.wins}W {myRating.losses}L {myRating.draws}D
              </span>
            )}
          </p>
        )}
        <label className="field">
          Mode
          <select value={mode} onChange={(e) => setMode(e.target.value as GameMode)}>
            <option value="HUMAN_VS_HUMAN">Two players (online)</option>
            <option value="HUMAN_VS_AI">Play Stockfish</option>
          </select>
        </label>
        <label className="field">
          Your color
          <select value={color} onChange={(e) => setColor(e.target.value as PlayerColor)}>
            <option value="WHITE">White</option>
            <option value="BLACK">Black</option>
            <option value="RANDOM">Random</option>
          </select>
        </label>
        {mode === 'HUMAN_VS_AI' && (
          <label className="field">
            Difficulty
            <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </select>
          </label>
        )}
        <label className="field">
          Time control
          <select value={createTimeControl} onChange={(e) => setCreateTimeControl(e.target.value)}>
            {TIME_CONTROLS.map((tc) => (
              <option key={tc.value} value={tc.value}>
                {tc.label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={busy} onClick={() => void create()}>
          Create game
        </button>
      </div>

      <div className="panel">
        <div className="row">
          <h2>Open games</h2>
          <button className="secondary" onClick={() => void refreshGames()}>
            Refresh
          </button>
        </div>
        {joinable.length === 0 && <p className="muted">No open games. Create one and share the link.</p>}
        <ul className="game-list">
          {joinable.map((game) => (
            <li key={game.id}>
              <span>
                {game.whitePlayer ?? 'Unknown'}{' '}
                <span className="muted">{game.mode === 'HUMAN_VS_AI' ? 'AI' : 'online'}</span>
                {game.timeControl && (
                  <span className="muted"> · {timeControlLabel(game.timeControl)}</span>
                )}
              </span>
              <button className="secondary" disabled={busy} onClick={() => void join(game.id)}>
                Join
              </button>
            </li>
          ))}
        </ul>
        <label className="field">
          Join by game id
          <div className="row">
            <input
              value={joinId}
              onChange={(e) => setJoinId(e.target.value)}
              placeholder="paste a game id"
            />
            <button disabled={busy || joinId.trim() === ''} onClick={() => void join(joinId.trim())}>
              Join
            </button>
          </div>
        </label>
      </div>

      <div className="panel">
        <div className="row">
          <h2>Notifications</h2>
          <button className="secondary" onClick={() => void refreshNotifications()}>
            Refresh
          </button>
        </div>
        {notifications.length === 0 && <p className="muted">Nothing yet.</p>}
        <ul className="notification-list">
          {notifications.map((n) => (
            <li key={n.id}>
              <span className={n.kind === 'GAME_ENDED' ? 'pill pill-result' : 'pill'}>{n.kind}</span>
              <span>{n.message}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="panel">
        <div className="row">
          <h2>Leaderboard</h2>
          <button className="secondary" onClick={() => void refreshRatings()}>
            Refresh
          </button>
        </div>
        {leaderboard.length === 0 && <p className="muted">No ranked games yet.</p>}
        <ul className="game-list">
          {leaderboard.map((entry, index) => (
            <li key={entry.userId}>
              <span>
                <span className="muted">{index + 1}. </span>
                {entry.displayName ?? 'Player'}
                {entry.userId === user?.id && <span className="muted small"> (you)</span>}
              </span>
              <span className="pill pill-result">{entry.rating}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
