import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Vitest sem "globals: true" nao registra o afterEach global que o
// @testing-library/react usa para limpeza automatica entre testes.
afterEach(() => {
  cleanup();
});
