import type { Engine, EngineRequest, EngineResult } from './engine';

const DEFAULT_URL = '/stockfish/stockfish-nnue-16-single.js';
const READY_TIMEOUT_MS = 10_000;
const MOVE_TIMEOUT_MS = 20_000;

type LineListener = (line: string) => void;

/**
 * Drives a Stockfish WASM worker over the UCI protocol. The worker is started lazily and
 * shared for the lifetime of the page. If the worker cannot be created, {@link bestMove}
 * rejects and the caller falls back to a weaker built-in engine.
 */
export class StockfishEngine implements Engine {
  readonly name = 'stockfish';

  private readonly url: string;
  private worker: Worker | null = null;
  private ready: Promise<void> | null = null;
  private listeners = new Set<LineListener>();

  constructor(url: string = DEFAULT_URL) {
    this.url = url;
  }

  private ensureReady(): Promise<void> {
    if (this.ready) {
      return this.ready;
    }

    this.ready = new Promise<void>((resolve, reject) => {
      let settled = false;
      const settleReject = (error: Error) => {
        if (!settled) {
          settled = true;
          reject(error);
        }
      };
      const timer = setTimeout(() => settleReject(new Error('Stockfish did not become ready')), READY_TIMEOUT_MS);

      try {
        const worker = new Worker(this.url);
        worker.onmessage = (event: MessageEvent) => {
          const line = typeof event.data === 'string' ? event.data : String(event.data);
          if (line.trim() === 'uciok' && !settled) {
            settled = true;
            clearTimeout(timer);
            resolve();
          }
          for (const listener of this.listeners) {
            listener(line);
          }
        };
        worker.onerror = (event: ErrorEvent) =>
          settleReject(new Error(`Stockfish worker failed to load: ${event.message}`));
        this.worker = worker;
        worker.postMessage('uci');
      } catch (error) {
        clearTimeout(timer);
        settleReject(error instanceof Error ? error : new Error(String(error)));
      }
    });

    return this.ready;
  }

  async bestMove(request: EngineRequest): Promise<EngineResult> {
    await this.ensureReady();
    const worker = this.worker;
    if (!worker) {
      throw new Error('Stockfish worker is not available');
    }

    const skill = clamp(request.skill ?? 20, 0, 20);
    const depth = request.depth ?? 12;

    return new Promise<EngineResult>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.listeners.delete(listener);
        reject(new Error('Stockfish timed out'));
      }, request.movetimeMs ?? MOVE_TIMEOUT_MS);

      const listener: LineListener = (line) => {
        const trimmed = line.trim();
        if (!trimmed.startsWith('bestmove')) {
          return;
        }
        clearTimeout(timer);
        this.listeners.delete(listener);
        const parts = trimmed.split(/\s+/);
        const uci = parts[1];
        if (!uci || uci === '(none)') {
          reject(new Error('No legal move available'));
          return;
        }
        resolve({ uci, ponder: parts[3] ?? null });
      };

      this.listeners.add(listener);
      worker.postMessage(`setoption name Skill Level value ${skill}`);
      worker.postMessage(`position fen ${request.fen}`);
      worker.postMessage(`go depth ${depth}`);
    });
  }

  dispose(): void {
    this.worker?.terminate();
    this.worker = null;
    this.ready = null;
    this.listeners.clear();
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
