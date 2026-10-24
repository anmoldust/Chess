import { Client, type IMessage } from '@stomp/stompjs';
import type { ErrorDto, GameDto, MoveRequest } from '../types';

export interface GameSocketHandlers {
  onGame: (game: GameDto) => void;
  onError?: (error: ErrorDto) => void;
  onConnected?: (connected: boolean) => void;
}

export interface GameSocket {
  sendMove: (move: MoveRequest) => void;
  join: () => void;
  resign: () => void;
  disconnect: () => void;
}

/**
 * Connect to the per-game STOMP topics. The session cookie rides along on the WebSocket
 * handshake, so the server knows who we are; components never touch WebSocket directly.
 */
export function connectGameSocket(gameId: string, handlers: GameSocketHandlers): GameSocket {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';

  const client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 3000,
    onConnect: () => {
      handlers.onConnected?.(true);
      client.subscribe(`/topic/game/${gameId}`, (message: IMessage) =>
        handlers.onGame(JSON.parse(message.body) as GameDto),
      );
      client.subscribe(`/topic/game/${gameId}/errors`, (message: IMessage) =>
        handlers.onError?.(JSON.parse(message.body) as ErrorDto),
      );
    },
    onWebSocketClose: () => handlers.onConnected?.(false),
    onStompError: (frame) =>
      handlers.onError?.({ code: 'STOMP_ERROR', detail: frame.headers['message'] ?? 'Broker error' }),
  });

  client.activate();

  const publish = (action: string, body?: unknown) => {
    if (!client.connected) {
      return;
    }
    client.publish({
      destination: `/app/game/${gameId}/${action}`,
      body: body === undefined ? '{}' : JSON.stringify(body),
    });
  };

  return {
    sendMove: (move) => publish('move', move),
    join: () => publish('join'),
    resign: () => publish('resign'),
    disconnect: () => {
      void client.deactivate();
    },
  };
}
