import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

const { getMock, toastError } = vi.hoisted(() => ({ getMock: vi.fn(), toastError: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  BASE_URL: 'http://localhost:8080/api',
}));
vi.mock('@/app/context/AuthContext', () => ({ useAuth: () => ({ user: { username: 'u', roles: [] } }) }));
vi.mock('@/app/components/AuditStamp', () => ({ AuditStamp: () => null }));
vi.mock('@/app/components/PixQrCode', () => ({ PixQrCode: () => null }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: toastError } }));

import FinancePage from '@/app/finance/page';

const INVOICE = {
  id: 42, dueDate: '2026-12-01', amount: 199.9, totalAmount: 199.9, status: 'PENDING', description: 'Mensalidade Dezembro',
  student: { fullName: 'Maria Teste' },
};

describe('Financeiro: falha na carga das listas é sinalizada', () => {
  beforeEach(() => vi.clearAllMocks());

  test('403 na rota de faturas avisa o usuário (toast) em vez de mostrar tela vazia em silêncio', async () => {
    getMock.mockImplementation((url: string) =>
      url.startsWith('/finance/invoices/') ? Promise.reject({ response: { status: 403 } }) : Promise.resolve({ data: [] }));
    render(<FinancePage />);

    await waitFor(() => expect(toastError).toHaveBeenCalled());
  });

  test('falha só no histórico avisa, mas as faturas continuam carregadas', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.startsWith('/finance/history/')) return Promise.reject({ response: { status: 500 } });
      return Promise.resolve({ data: url.startsWith('/finance/invoices/GRADUATION') ? [INVOICE] : [] });
    });
    render(<FinancePage />);

    expect(await screen.findByText('Mensalidade Dezembro')).toBeInTheDocument();
    expect(toastError).toHaveBeenCalled();
  });

  test('carga bem-sucedida não dispara aviso de erro', async () => {
    getMock.mockImplementation((url: string) =>
      Promise.resolve({ data: url.startsWith('/finance/invoices/GRADUATION') ? [INVOICE] : [] }));
    render(<FinancePage />);

    expect(await screen.findByText('Mensalidade Dezembro')).toBeInTheDocument();
    expect(toastError).not.toHaveBeenCalled();
  });
});
