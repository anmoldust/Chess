import { copyFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const sourceDir = join(root, 'node_modules', 'stockfish', 'src');
const targetDir = join(root, 'public', 'stockfish');
const artifacts = ['stockfish-nnue-16-single.js', 'stockfish-nnue-16-single.wasm'];

await mkdir(targetDir, { recursive: true });

let copied = 0;
for (const artifact of artifacts) {
  const from = join(sourceDir, artifact);
  if (!existsSync(from)) {
    console.warn(`[copy-engine] skipping missing ${artifact}`);
    continue;
  }
  await copyFile(from, join(targetDir, artifact));
  copied += 1;
}

console.log(`[copy-engine] staged ${copied}/${artifacts.length} Stockfish files into public/stockfish`);
