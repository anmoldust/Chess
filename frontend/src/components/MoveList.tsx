import type { MoveDto } from '../types';

export function MoveList({ moves }: { moves: MoveDto[] }) {
  if (moves.length === 0) {
    return <p className="muted">No moves yet.</p>;
  }

  const rows: Array<{ number: number; white?: MoveDto; black?: MoveDto }> = [];
  moves.forEach((move, index) => {
    const rowIndex = Math.floor(index / 2);
    if (!rows[rowIndex]) {
      rows[rowIndex] = { number: rowIndex + 1 };
    }
    if (index % 2 === 0) {
      rows[rowIndex].white = move;
    } else {
      rows[rowIndex].black = move;
    }
  });

  return (
    <ol className="move-list">
      {rows.map((row) => (
        <li key={row.number}>
          <span className="move-number">{row.number}.</span>
          <span className="move-uci">{row.white?.uci ?? ''}</span>
          <span className="move-uci">{row.black?.uci ?? ''}</span>
        </li>
      ))}
    </ol>
  );
}
