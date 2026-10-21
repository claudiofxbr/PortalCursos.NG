import React from 'react';
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock, postMock, toastSuccess, toastError } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: postMock, put: vi.fn(), delete: vi.fn() },
  BASE_URL: 'http://localhost:8080/api',
}));

vi.mock('@/app/context/AuthContext', () => ({
  useAuth: () => ({ user: { username: 'financeiro' } }),
}));

vi.mock('@/app/components/AuditStamp', () => ({ AuditStamp: () => null }));
vi.mock('@/app/components/PixQrCode', () => ({ PixQrCode: () => null }));

vi.mock('sonner', () => ({
  toast: { success: toastSuccess, error: toastError },
}));

import FinancePage from '@/app/finance/page';
import { cardCheckoutErrorMessage, isSafePayUrl, paymentIdFromSearch } from '@/app/finance/cardCheckout';

const INVOICE = {
  id: 42,
  dueDate: '2026-12-01',
  amount: 199.9,
  totalAmount: 199.9,
  status: 'PENDING',
  description: 'Mensalidade Dezembro',
  studentName: 'Maria',
};

describe('helpers do pagamento por cartão', () => {
  test('isSafePayUrl aceita só https sem credenciais', () => {
    expect(isSafePayUrl('https://pagamento.pagbank.com.br/pagamento?code=x')).toBe(true);
    expect(isSafePayUrl('http://pay.example/p')).toBe(false);
    expect(isSafePayUrl('https://user:pass@pay.example/p')).toBe(false);
    expect(isSafePayUrl('javascript:alert(1)')).toBe(false);
    expect(isSafePayUrl('//pay.example')).toBe(false);
    expect(isSafePayUrl(undefined)).toBe(false);
    expect(isSafePayUrl(42)).toBe(false);
  });

  test('paymentIdFromSearch só aceita inteiro positivo', () => {
    expect(paymentIdFromSearch('?payment=42')).toBe(42);
    expect(paymentIdFromSearch('?payment=0')).toBeNull();
    expect(paymentIdFromSearch('?payment=-1')).toBeNull();
    expect(paymentIdFromSearch('?payment=4x')).toBeNull();
    expect(paymentIdFromSearch('?payment=<script>')).toBeNull();
    expect(paymentIdFromSearch('?payment=99999999999999999999')).toBeNull();
    expect(paymentIdFromSearch('')).toBeNull();
  });

  test('mensagens de erro por status', () => {
    expect(cardCheckoutErrorMessage(429)).toMatch(/Muitas tentativas/);
    expect(cardCheckoutErrorMessage(403, 'Pagamento por cartão em breve.')).toBe('Pagamento por cartão em breve.');
    expect(cardCheckoutErrorMessage(403)).toMatch(/Não foi possível/);
    expect(cardCheckoutErrorMessage(404)).toMatch(/não encontrada/);
    expect(cardCheckoutErrorMessage(502)).toMatch(/indisponível/);
    expect(cardCheckoutErrorMessage()).toMatch(/indisponível/);
  });
});

describe('FinancePage — pagamento por cartão', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMock.mockImplementation((url: string) =>
      Promise.resolve({ data: url.startsWith('/finance/invoices/GRADUATION') ? [INVOICE] : [] }),
    );
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.useRealTimers();
    window.history.replaceState(null, '', '/');
  });

  test('sem a variável de build o botão Cartão não aparece e Pix/Boleto continuam', async () => {
    render(<FinancePage />);

    expect(await screen.findByText('Pix')).toBeInTheDocument();
    expect(screen.getByText('Boleto')).toBeInTheDocument();
    expect(screen.queryByText('Cartão')).not.toBeInTheDocument();
  });

  test('com a variável ligada, o clique chama a API e redireciona para o link https', async () => {
    vi.stubEnv('NEXT_PUBLIC_CARD_PAYMENTS_ENABLED', 'true');
    postMock.mockResolvedValue({ data: { url: 'https://pagamento.pagbank.com.br/pagamento?code=x', paymentId: 42, reused: false } });
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign, search: '', pathname: '/' } });

    render(<FinancePage />);
    await userEvent.click(await screen.findByText('Cartão'));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://pagamento.pagbank.com.br/pagamento?code=x'));
    expect(postMock).toHaveBeenCalledWith('/finance/generate-card-checkout/42');

    Object.defineProperty(window, 'location', { configurable: true, value: original });
  });

  test('link inseguro devolvido pela API não redireciona', async () => {
    vi.stubEnv('NEXT_PUBLIC_CARD_PAYMENTS_ENABLED', 'true');
    postMock.mockResolvedValue({ data: { url: 'http://evil.example/p' } });
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign, search: '', pathname: '/' } });

    render(<FinancePage />);
    await userEvent.click(await screen.findByText('Cartão'));

    await waitFor(() => expect(toastError).toHaveBeenCalled());
    expect(assign).not.toHaveBeenCalled();

    Object.defineProperty(window, 'location', { configurable: true, value: original });
  });

  test('erro 429 mostra a mensagem de limite e não redireciona', async () => {
    vi.stubEnv('NEXT_PUBLIC_CARD_PAYMENTS_ENABLED', 'true');
    postMock.mockRejectedValue({ response: { status: 429, data: { message: 'x' } } });

    render(<FinancePage />);
    await userEvent.click(await screen.findByText('Cartão'));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Muitas tentativas de pagamento. Aguarde alguns minutos.'));
  });

  test('retorno do PagBank (?payment=42) mostra aguardando e nunca afirma pagamento sem confirmação', async () => {
    window.history.replaceState(null, '', '/finance?payment=42');

    render(<FinancePage />);

    expect(await screen.findByText(/Aguardando confirmação do pagamento da fatura #42/)).toBeInTheDocument();
    expect(screen.queryByText(/Pagamento confirmado/)).not.toBeInTheDocument();
    expect(window.location.search).toBe('');
  });

  test('quando a fatura aparece no histórico, mostra confirmado', async () => {
    window.history.replaceState(null, '', '/finance?payment=42');
    getMock.mockImplementation((url: string) =>
      Promise.resolve({ data: url.startsWith('/finance/history/GRADUATION') ? [{ ...INVOICE, status: 'PAID' }] : [] }),
    );

    render(<FinancePage />);

    expect(await screen.findByText(/Pagamento confirmado\./, {}, { timeout: 4000 })).toBeInTheDocument();
    expect(toastSuccess).toHaveBeenCalledWith('Pagamento confirmado!');
  });
});
