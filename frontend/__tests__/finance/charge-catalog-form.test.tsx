import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
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
vi.mock('sonner', () => ({ toast: { success: toastSuccess, error: toastError } }));

import FinancePage from '@/app/finance/page';

const CATALOG = [
  {
    priceId: 7, code: 'NADA_CONSTA', name: 'Certidão de nada consta', group: 'Conclusão e Expedição de Documentos',
    paymentCategory: 'INTERNAL_PROCESS', processType: 'CERTIFICATE', value: 15.5, valueSource: 'GLOBAL',
    valueDefined: true, defaultDescription: 'Certidão de nada consta',
  },
  {
    priceId: 8, code: 'TUITION', name: 'Mensalidade', group: 'Cobranças recorrentes',
    paymentCategory: 'TUITION', processType: null, value: null, valueSource: 'GLOBAL',
    valueDefined: false, defaultDescription: 'Mensalidade',
  },
];

function mockApi(catalog: () => Promise<{ data: unknown }>) {
  getMock.mockImplementation((url: string) => {
    if (url === '/finance/charge-catalog') return catalog();
    if (url.startsWith('v1/grad-students/')) return Promise.resolve({ data: { fullName: 'Maria Teste', cpf: '39012874600' } });
    return Promise.resolve({ data: [] });
  });
}

async function openFormWithStudent() {
  render(<FinancePage />);
  await userEvent.click(await screen.findByText(/Gerar Nova Cobrança/));
  await userEvent.type(await screen.findByPlaceholderText('ID do Aluno no Sistema'), '1');
  await screen.findByText('Maria Teste', {}, { timeout: 4000 });
}

describe('FinancePage: procedimento preenche a cobrança', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    postMock.mockResolvedValue({ data: { id: 99 } });
  });

  test('escolher o procedimento preenche valor (somente leitura) e descrição, e o envio leva só o chargePriceId', async () => {
    mockApi(() => Promise.resolve({ data: CATALOG }));
    await openFormWithStudent();
    await waitFor(() => expect(getMock).toHaveBeenCalledWith('/finance/charge-catalog', { params: { studentId: '1', level: 'GRADUATION' } }));

    await userEvent.selectOptions(await screen.findByLabelText('Procedimento cobrado'), '7');

    const amount = screen.getByLabelText('Valor (R$)') as HTMLInputElement;
    expect(amount.value).toBe('15.50');
    expect(amount.readOnly).toBe(true);
    expect(screen.getByText('Valor da tabela (todos os cursos)')).toBeInTheDocument();
    expect(screen.queryByText('Categoria da Cobrança')).not.toBeInTheDocument();
    expect((screen.getByPlaceholderText(/Descreva o motivo/) as HTMLTextAreaElement).value).toBe('Certidão de nada consta');

    const due = document.querySelector('input[type="date"]') as HTMLInputElement;
    await userEvent.type(due, '2026-12-01');
    await userEvent.click(screen.getByText('Confirmar e Lançar'));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/charge', {
      studentId: 1, dueDate: '2026-12-01', description: 'Certidão de nada consta', academicLevel: 'GRADUATION', chargePriceId: 7,
    }));
  });

  test('procedimento sem valor definido aparece desabilitado', async () => {
    mockApi(() => Promise.resolve({ data: CATALOG }));
    await openFormWithStudent();

    const option = (await screen.findByRole('option', { name: /Mensalidade \(valor não definido\)/ })) as HTMLOptionElement;
    expect(option.disabled).toBe(true);
  });

  test('voltar para "sem procedimento" libera o valor manual e limpa o preenchimento', async () => {
    mockApi(() => Promise.resolve({ data: CATALOG }));
    await openFormWithStudent();
    const select = await screen.findByLabelText('Procedimento cobrado');
    await userEvent.selectOptions(select, '7');

    await userEvent.selectOptions(select, '');

    const amount = screen.getByLabelText('Valor (R$)') as HTMLInputElement;
    expect(amount.readOnly).toBe(false);
    expect(amount.value).toBe('');
    expect(screen.getByText('Categoria da Cobrança')).toBeInTheDocument();
  });

  test('falha ao carregar a lista mostra aviso e bloqueia o lançamento manual', async () => {
    mockApi(() => Promise.reject({ response: { status: 500 } }));
    await openFormWithStudent();

    expect(await screen.findByRole('alert')).toHaveTextContent(/Não foi possível carregar os procedimentos/);

    await userEvent.type(screen.getByLabelText('Valor (R$)'), '10');
    await userEvent.type(document.querySelector('input[type="date"]') as HTMLInputElement, '2026-12-01');
    await userEvent.click(screen.getByText('Confirmar e Lançar'));

    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/não carregou/));
    expect(postMock).not.toHaveBeenCalledWith('/finance/charge', expect.anything());
  });
});
