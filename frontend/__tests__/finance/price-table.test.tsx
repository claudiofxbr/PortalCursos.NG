import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock, postMock, putMock, toastSuccess, toastError } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: postMock, put: putMock, delete: vi.fn() },
}));

vi.mock('sonner', () => ({
  toast: { success: toastSuccess, error: toastError },
}));

import PriceTablePage from '@/app/finance/prices/page';
import {
  formatMoney,
  groupByCategory,
  mayOpenPriceTable,
  parseMoney,
  priceErrorMessage,
  type PriceRow,
  type PriceTable,
} from '@/app/finance/prices/priceTable';

const row = (over: Partial<PriceRow>): PriceRow => ({
  id: 1,
  code: 'REMATRICULA',
  category: 'Renovação e Ajustes Curriculares',
  name: 'Rematrícula periódica',
  academicScope: 'ALL',
  courseId: null,
  currentValue: 100,
  version: 3,
  canEdit: false,
  canPropose: false,
  pending: null,
  ...over,
});

describe('helpers da tabela de valores', () => {
  test('parseMoney aceita vírgula, ponto e milhar; recusa o resto', () => {
    expect(parseMoney('150')).toBe(150);
    expect(parseMoney('150,5')).toBe(150.5);
    expect(parseMoney('1.234,50')).toBe(1234.5);
    expect(parseMoney('1234.50')).toBe(1234.5);
    expect(parseMoney('R$ 99,99')).toBe(99.99);
    expect(parseMoney('0')).toBe(0);
    expect(parseMoney('')).toBeNull();
    expect(parseMoney('-1')).toBeNull();
    expect(parseMoney('1,234')).toBeNull();
    expect(parseMoney('abc')).toBeNull();
    expect(parseMoney('10000000')).toBeNull();
  });

  test('formatMoney mostra "a preencher" quando não há valor', () => {
    expect(formatMoney(null)).toMatch(/a preencher/);
    expect(formatMoney(150)).toMatch(/150,00/);
  });

  test('mayOpenPriceTable aceita admin, root e secretaria (texto ou objeto) e recusa o resto', () => {
    expect(mayOpenPriceTable(['ROLE_ADMIN'])).toBe(true);
    expect(mayOpenPriceTable([{ name: 'ROLE_SECRETARIA' }])).toBe(true);
    expect(mayOpenPriceTable(['ROLE_ROOT_MASTER'])).toBe(true);
    expect(mayOpenPriceTable(['ROLE_FINANCEIRO'])).toBe(false);
    expect(mayOpenPriceTable(['ROLE_ALUNO'])).toBe(false);
    expect(mayOpenPriceTable(undefined)).toBe(false);
  });

  test('groupByCategory e mensagens de erro', () => {
    const groups = groupByCategory([row({ id: 1 }), row({ id: 2, category: 'Outra' }), row({ id: 3 })]);
    expect(groups.map((g) => [g.category, g.rows.length])).toEqual([
      ['Renovação e Ajustes Curriculares', 2],
      ['Outra', 1],
    ]);
    expect(priceErrorMessage(403)).toMatch(/permissão/);
    expect(priceErrorMessage(404)).toMatch(/não encontrada/);
    expect(priceErrorMessage(400, 'Valor acima do limite permitido.')).toBe('Valor acima do limite permitido.');
  });
});

describe('PriceTablePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const serve = (table: PriceTable) => getMock.mockResolvedValue({ data: table });

  test('secretaria só lê: sem campos nem botões de edição', async () => {
    serve({ role: 'SECRETARIA', rows: [row({})] });
    render(<PriceTablePage />);

    expect(await screen.findByText('Rematrícula periódica')).toBeInTheDocument();
    expect(screen.getByText(/Secretaria \(somente leitura\)/)).toBeInTheDocument();
    expect(screen.getByText('somente leitura')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  test('chefe propõe (não salva direto) e o texto avisa que depende de confirmação', async () => {
    serve({ role: 'HEAD', rows: [row({ id: 7, courseId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', canPropose: true })] });
    postMock.mockResolvedValue({ data: {} });
    render(<PriceTablePage />);

    await userEvent.type(await screen.findByLabelText('Novo valor de Rematrícula periódica'), '150,50');
    expect(screen.queryByText('Salvar')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('Propor'));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/prices/7/proposals', { value: 150.5, reason: '' }));
    expect(putMock).not.toHaveBeenCalled();
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringMatching(/Aguarda a confirmação/));
  });

  test('administrador salva com a versão da linha', async () => {
    serve({ role: 'ADMIN', rows: [row({ id: 5, canEdit: true, version: 3 })] });
    putMock.mockResolvedValue({ data: {} });
    render(<PriceTablePage />);

    await userEvent.type(await screen.findByLabelText('Novo valor de Rematrícula periódica'), '200');
    await userEvent.click(screen.getByText('Salvar'));

    await waitFor(() => expect(putMock).toHaveBeenCalledWith('/finance/prices/5', { value: 200, version: 3 }));
  });

  test('administrador vê Confirmar e Rejeitar numa proposta pendente; chefe não vê', async () => {
    const pending = { id: 9, value: 150, proposedBy: 'chefe', proposedAt: '2026-10-20T10:00:00', reason: 'reajuste' };
    serve({ role: 'ADMIN', rows: [row({ id: 5, canEdit: true, pending })] });
    postMock.mockResolvedValue({ data: {} });
    const { unmount } = render(<PriceTablePage />);

    await userEvent.click(await screen.findByText('Confirmar'));
    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/prices/proposals/9/approve'));
    expect(screen.getByText('Rejeitar')).toBeInTheDocument();
    unmount();

    serve({ role: 'HEAD', rows: [row({ id: 5, canPropose: true, pending })] });
    render(<PriceTablePage />);
    expect(await screen.findByText(/proposto por chefe/)).toBeInTheDocument();
    expect(screen.queryByText('Confirmar')).not.toBeInTheDocument();
    expect(screen.queryByText('Rejeitar')).not.toBeInTheDocument();
  });

  test('valor inválido não chama a API', async () => {
    serve({ role: 'ADMIN', rows: [row({ id: 5, canEdit: true })] });
    render(<PriceTablePage />);

    await userEvent.type(await screen.findByLabelText('Novo valor de Rematrícula periódica'), 'abc');
    await userEvent.click(screen.getByText('Salvar'));

    expect(putMock).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/valor válido/));
  });

  test('403 mostra mensagem de acesso negado e nenhuma linha', async () => {
    getMock.mockRejectedValue({ response: { status: 403 } });
    render(<PriceTablePage />);

    expect(await screen.findByText(/Você não tem acesso a esta tabela/)).toBeInTheDocument();
    const panel = screen.getByText(/restrita a administradores/).closest('div') as HTMLElement;
    expect(within(panel).queryByRole('table')).not.toBeInTheDocument();
  });
});
