import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { errorMessage } from '../lib/errors';
import { navigate } from '../lib/useHashRoute';
import { useAuthStore } from '../store/authStore';
import type { GameSummaryDto, PlayerColor, RatingChangeDto, RatingDto } from '../types';

const END_REASON_LABEL: Record<string, string> = {
  CHECKMATE: 'checkmate',
  RESIGNATION: 'resignation',
  TIMEOUT: 'time',
  STALEMATE: 'stalemate',
  INSUFFICIENT_MATERIAL: 'insufficient material',
  FIFTY_MOVE_RULE: 'fifty-move rule',
  THREEFOLD_REPETITION: 'repetition',
  DRAW_AGREED: 'agreement',
};

export function ProfilePage() {
  const user = useAuthStore((s) => s.user);

  const [rating, setRating] = useState<RatingDto | null>(null);
  const [games, setGames] = useState<GameSummaryDto[]>([]);
  const [changes, setChanges] = useState<Record<string, RatingChangeDto>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      return;
    }
    let active = true;
    Promise.all([
      api.ratings.me(),
      api.listGameHistory(user.id, 20),
      api.ratings.history(user.id, 20),
    ])
      .then(([mine, history, movements]) => {
        if (!active) return;
        setRating(mine);
        setGames(history);
        setChanges(Object.fromEntries(movements.map((movement) => [movement.gameId, movement])));
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
    };
  }, [user]);

  if (!user) {
    return null;
  }

  return (
    <div className="lobby">
      <div className="panel">
        <h2>{user.displayName}</h2>
        {rating ? (
          <p className="muted">
            Rating <strong>{rating.rating}</strong> · {rating.wins}W {rating.losses}L {rating.draws}D
            {rating.gamesPlayed > 0 && <span className="small"> ({rating.gamesPlayed} games)</span>}
          </p>
        ) : (
          <p className="muted">Loading…</p>
        )}
        {error && <p className="error">{error}</p>}
      </div>

      <div className="panel">
        <h2>Recent games</h2>
        {games.length === 0 && <p className="muted">No finished games yet. Play one to start your record.</p>}
        <ul className="game-list">
          {games.map((game) => {
            const myColor: PlayerColor = game.whiteUserId === user.id ? 'WHITE' : 'BLACK';
            const opponent = myColor === 'WHITE' ? game.blackPlayer : game.whitePlayer;
            const won = game.status === 'WHITE_WON' ? myColor === 'WHITE' : myColor === 'BLACK';
            const outcome = game.status === 'DRAW' ? 'Draw' : won ? 'Win' : 'Loss';
            const movement = changes[game.id];

            return (
              <li key={game.id}>
                <span>
                  <strong>{opponent ?? 'Opponent'}</strong>{' '}
                  <span className={outcome === 'Win' ? 'pill pill-result' : 'pill'}>{outcome}</span>{' '}
                  <span className="muted small">
                    {game.result}
                    {game.endReason ? ` · ${END_REASON_LABEL[game.endReason] ?? game.endReason}` : ''}
                    {` · ${game.plyCount} plies`}
                  </span>
                </span>
                <span className="row">
                  {movement && (
                    <span className={movement.delta >= 0 ? 'pill pill-result' : 'pill'}>
                      {movement.delta >= 0 ? `+${movement.delta}` : movement.delta}
                    </span>
                  )}
                  <button className="secondary" onClick={() => navigate({ name: 'game', id: game.id })}>
                    View
                  </button>
                </span>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
