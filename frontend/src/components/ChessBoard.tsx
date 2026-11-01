import { useState } from 'react';
import { Chessboard } from 'react-chessboard';
import type { PromotionPieceOption, Square } from 'react-chessboard/dist/chessboard/types';
import { isPromotionMove } from '../lib/chessUtils';

interface ChessBoardProps {
  fen: string;
  orientation: 'white' | 'black';
  /** True when it is the viewer's turn and they may move now. */
  canMove: boolean;
  /** True when the viewer may queue a premove because it is the opponent's turn. */
  canPremove: boolean;
  /** A queued premove, drawn as a highlight on its two squares. */
  premove: { from: string; to: string } | null;
  onMove: (from: string, to: string, promotion?: string) => void;
  onPremove: (from: string, to: string) => void;
}

const PREMOVE_STYLE = { background: 'rgba(91, 157, 217, 0.45)' };

export function ChessBoard({
  fen,
  orientation,
  canMove,
  canPremove,
  premove,
  onMove,
  onPremove,
}: ChessBoardProps) {
  const [promotion, setPromotion] = useState<{ from: Square; to: Square } | null>(null);

  const handleDrop = (source: Square, target: Square): boolean => {
    if (canMove) {
      if (isPromotionMove(source, target)) {
        setPromotion({ from: source, to: target });
        return true;
      }
      onMove(source, target);
      return true;
    }
    if (canPremove) {
      // A premove is not played now: the piece snaps back and the wish is shown as a highlight,
      // to be attempted when it becomes the mover's turn.
      onPremove(source, target);
      return false;
    }
    return false;
  };

  const handlePromotionSelect = (piece?: PromotionPieceOption, from?: Square, to?: Square): boolean => {
    if (piece && from && to) {
      onMove(from, to, piece[1].toLowerCase());
    }
    setPromotion(null);
    return true;
  };

  const squareStyles = premove
    ? { [premove.from]: PREMOVE_STYLE, [premove.to]: PREMOVE_STYLE }
    : {};

  return (
    <div className="board-wrapper">
      <Chessboard
        position={fen}
        boardOrientation={orientation}
        arePiecesDraggable={canMove || canPremove}
        onPieceDrop={handleDrop}
        onPromotionPieceSelect={handlePromotionSelect}
        showPromotionDialog={promotion !== null}
        promotionToSquare={promotion?.to ?? null}
        animationDuration={200}
        customSquareStyles={squareStyles}
        customBoardStyle={{ borderRadius: '10px', boxShadow: '0 12px 30px rgba(0, 0, 0, 0.45)' }}
        customDarkSquareStyle={{ backgroundColor: '#6f8faf' }}
        customLightSquareStyle={{ backgroundColor: '#e8edf2' }}
      />
    </div>
  );
}
