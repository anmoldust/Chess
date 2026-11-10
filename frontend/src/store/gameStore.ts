import { create } from 'zustand';
import type { GameDto } from '../types';

interface GameState {
  game: GameDto | null;
  connected: boolean;
  error: string | null;
  setGame: (game: GameDto | null) => void;
  setConnected: (connected: boolean) => void;
  setError: (error: string | null) => void;
}

export const useGameStore = create<GameState>((set) => ({
  game: null,
  connected: false,
  error: null,
  setGame: (game) => set({ game }),
  setConnected: (connected) => set({ connected }),
  setError: (error) => set({ error }),
}));
