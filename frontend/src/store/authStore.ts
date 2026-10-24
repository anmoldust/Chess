import { create } from 'zustand';
import type { UserDto } from '../types';

type AuthPhase = 'loading' | 'ready';

interface AuthState {
  user: UserDto | null;
  phase: AuthPhase;
  setUser: (user: UserDto | null) => void;
  setPhase: (phase: AuthPhase) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  phase: 'loading',
  setUser: (user) => set({ user }),
  setPhase: (phase) => set({ phase }),
}));
