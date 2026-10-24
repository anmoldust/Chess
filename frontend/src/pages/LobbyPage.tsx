import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { errorMessage } from '../lib/errors';
import { navigate } from '../lib/useHashRoute';
import { useAuthStore } from '../store/authStore';
import { useGameStore } from '../store/gameStore';
import type { GameDto, GameMode, PlayerColor } from '../types';

export function LobbyPage() {
  const user = useAuthStore((s) => s.user);
  const setGame = useGameStore((s) => s.setGame);

  const [mode, setMode] = useState<GameMode>('HUMAN_VS_HUMAN');
  const [color, setColor] = useState<PlayerColor>('WHITE');
  const [openGames, setOpenGames] = useState<GameDto[]>([]);
  const [joinId, setJoinId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setOpenGames(await api.listOpenGames());
    } catch {
      setOpenGames([]);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const game = await api.createGame({ mode, preferredColor: color });
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

  const joinable = openGames.filter((game) => game.whiteUserId !== user?.id && game.blackUserId !== user?.id);

  return (
    <div className="lobby">
      <div className="panel">
        <h2>New game</h2>
        <p className="muted">
          Playing as <strong>{user?.displayName}</strong>
        </p>
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
        <button disabled={busy} onClick={() => void create()}>
          Create game
        </button>
        {error && <p className="error">{error}</p>}
      </div>

      <div className="panel">
        <div className="row">
          <h2>Open games</h2>
          <button className="secondary" onClick={() => void refresh()}>
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
    </div>
  );
}
