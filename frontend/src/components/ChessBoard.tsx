import { useState } from 'react';
import { Chessboard } from 'react-chessboard';
import type { PromotionPieceOption, Square } from 'react-chessboard/dist/chessboard/types';
import { isPromotionMove } from '../lib/chessUtils';

interface ChessBoardProps {
  fen: string;
  orientation: 'white' | 'black';
  canMove: boolean;
  onMove: (from: string, to: string, promotion?: string) => void;
}

export function ChessBoard({ fen, orientation, canMove, onMove }: ChessBoardProps) {
  const [promotion, setPromotion] = useState<{ from: Square; to: Square } | null>(null);

  const handleDrop = (source: Square, target: Square): boolean => {
    if (!canMove) {
      return false;
    }
    if (isPromotionMove(source, target)) {
      setPromotion({ from: source, to: target });
      return true;
    }
    onMove(source, target);
    return true;
  };

  const handlePromotionSelect = (piece?: PromotionPieceOption, from?: Square, to?: Square): boolean => {
    if (piece && from && to) {
      onMove(from, to, piece[1].toLowerCase());
    }
    setPromotion(null);
    return true;
  };

  return (
    <div className="board-wrapper">
      <Chessboard
        position={fen}
        boardOrientation={orientation}
        arePiecesDraggable={canMove}
        onPieceDrop={handleDrop}
        onPromotionPieceSelect={handlePromotionSelect}
        showPromotionDialog={promotion !== null}
        promotionToSquare={promotion?.to ?? null}
        animationDuration={200}
        customBoardStyle={{ borderRadius: '10px', boxShadow: '0 12px 30px rgba(0, 0, 0, 0.45)' }}
        customDarkSquareStyle={{ backgroundColor: '#6f8faf' }}
        customLightSquareStyle={{ backgroundColor: '#e8edf2' }}
      />
    </div>
  );
}
