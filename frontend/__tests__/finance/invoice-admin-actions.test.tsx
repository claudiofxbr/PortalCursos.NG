import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock, postMock } = vi.hoisted(() => ({ getMock: vi.fn(), postMock: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: postMock, put: vi.fn(), delete: vi.fn() },
  BASE_URL: 'http://localhost:8080/api',
}));
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => ({ user: { username: 'u', roles: [] } }) }));
vi.mock('@/app/components/AuditStamp', () => ({ AuditStamp: () => null }));
vi.mock('@/app/components/PixQrCode', () => ({ PixQrCode: () => null }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import FinancePage from '@/app/finance/page';

const INVOICE = {
  id: 42, dueDate: '2026-12-01', amount: 199.9, totalAmount: 199.9, status: 'PENDING', description: 'Mensalidade Dezembro',
  student: { fullName: 'Maria Teste' },
};

function serve(policy: unknown) {
  getMock.mockImplementation((url: string) => {
    if (url === '/finance/charge-policy') return Promise.resolve({ data: policy });
    return Promise.resolve({ data: url.startsWith('/finance/invoices/GRADUATION') ? [INVOICE] : [] });
  });
}

const POLICY = (over: Record<string, unknown>) => ({
  enforceTablePrice: false, canRequestException: true, canDecideException: false, tableReady: false, blankValues: 3, ...over,
});

describe('Cobranças: exclusão e alteração de valor só para administrador', () => {
  beforeEach(() => vi.clearAllMocks());

  test('quem não é administrador não vê o botão Excluir', async () => {
    serve(POLICY({ canDecideException: false }));
    render(<FinancePage />);

    expect(await screen.findByText('Mensalidade Dezembro')).toBeInTheDocument();
    expect(screen.getByText('Editar')).toBeInTheDocument();
    expect(screen.queryByText('Excluir')).not.toBeInTheDocument();
  });

  test('o administrador vê o botão Excluir', async () => {
    serve(POLICY({ canDecideException: true }));
    render(<FinancePage />);

    expect(await screen.findByText('Excluir')).toBeInTheDocument();
  });

  test('sem a política carregada (falha) o Excluir fica escondido por segurança', async () => {
    getMock.mockImplementation((url: string) =>
      url === '/finance/charge-policy' ? Promise.reject(new Error('falha'))
        : Promise.resolve({ data: url.startsWith('/finance/invoices/GRADUATION') ? [INVOICE] : [] }));
    render(<FinancePage />);

    expect(await screen.findByText('Mensalidade Dezembro')).toBeInTheDocument();
    expect(screen.queryByText('Excluir')).not.toBeInTheDocument();
  });

  test('na edição, quem não é administrador não altera o valor; o administrador altera (com a regra em espera)', async () => {
    serve(POLICY({ canDecideException: false }));
    const { unmount } = render(<FinancePage />);
    await userEvent.click(await screen.findByText('Editar'));
    const readonlyAmount = document.querySelector('input[type="number"][step="0.01"]') as HTMLInputElement;
    expect(readonlyAmount.readOnly).toBe(true);
    unmount();

    serve(POLICY({ canDecideException: true }));
    render(<FinancePage />);
    await userEvent.click(await screen.findByText('Editar'));
    expect((document.querySelector('input[type="number"][step="0.01"]') as HTMLInputElement).readOnly).toBe(false);
  });

  test('salvar a edição envia o valor no campo `amount` (principal), nunca o totalAmount com juros', async () => {
    serve(POLICY({ canDecideException: true }));
    const { default: api } = await import('@/app/services/api');
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} });
    render(<FinancePage />);
    await userEvent.click(await screen.findByText('Editar'));
    const amount = document.querySelector('input[type="number"][step="0.01"]') as HTMLInputElement;
    await userEvent.clear(amount);
    await userEvent.type(amount, '150.5');
    await userEvent.click(screen.getByRole('button', { name: /salvar/i }));

    const [url, body] = (api.put as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/finance/invoices/42');
    expect(body.amount).toBe(150.5);
    expect(body).not.toHaveProperty('totalAmount');
    expect(body.dueDate).toBe('2026-12-01');
  });
});
