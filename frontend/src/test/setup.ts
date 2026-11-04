import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Unmount everything between tests so a component's effects never leak into the next one.
afterEach(() => {
  cleanup();
});
