import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import PriceTablePage from '@/app/finance/prices/page';

const TABLE = {
  role: 'ADMIN',
  rows: [{
    id: 1, code: 'REMATRICULA', category: 'Renovação e Ajustes Curriculares', name: 'Rematrícula periódica',
    academicScope: 'ALL', courseId: null, currentValue: null, version: 0, canEdit: true, canPropose: false, pending: null,
  }],
};

function serve(policy: unknown) {
  getMock.mockImplementation((url: string) => {
    if (url === '/finance/prices') return Promise.resolve({ data: TABLE });
    if (url === '/finance/charge-policy') return policy instanceof Error ? Promise.reject(policy) : Promise.resolve({ data: policy });
    return Promise.resolve({ data: [] });
  });
}

describe('Tela de valores: aviso da regra "valor sempre da tabela"', () => {
  beforeEach(() => vi.clearAllMocks());

  test('tabela incompleta mostra a regra em espera e quantos valores faltam', async () => {
    serve({ enforceTablePrice: false, canRequestException: true, canDecideException: true, tableReady: false, blankValues: 24 });
    render(<PriceTablePage />);

    const banner = await screen.findByRole('status', { name: 'Regra do valor da cobrança' });
    expect(banner).toHaveTextContent(/em espera/);
    expect(banner).toHaveTextContent(/Faltam 24 valores/);
  });

  test('tabela completa e regra valendo mostra "Regra ativa"', async () => {
    serve({ enforceTablePrice: true, canRequestException: true, canDecideException: true, tableReady: true, blankValues: 0 });
    render(<PriceTablePage />);

    expect(await screen.findByRole('status', { name: 'Regra do valor da cobrança' })).toHaveTextContent(/Regra ativa/);
  });

  test('se a política não carregar, a tabela aparece normalmente e sem o aviso', async () => {
    serve(new Error('falha'));
    render(<PriceTablePage />);

    expect(await screen.findByText('Rematrícula periódica')).toBeInTheDocument();
    expect(screen.queryByRole('status', { name: 'Regra do valor da cobrança' })).not.toBeInTheDocument();
  });
});
