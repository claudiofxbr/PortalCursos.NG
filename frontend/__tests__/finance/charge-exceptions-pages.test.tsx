import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
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
  useAuth: () => ({ user: { username: 'ana', roles: ['ROLE_SECRETARIA'] } }),
}));

vi.mock('@/app/components/AuditStamp', () => ({ AuditStamp: () => null }));
vi.mock('@/app/components/PixQrCode', () => ({ PixQrCode: () => null }));
vi.mock('sonner', () => ({ toast: { success: toastSuccess, error: toastError } }));

import FinancePage from '@/app/finance/page';
import ChargeExceptionsPage from '@/app/finance/exceptions/page';

const CATALOG = [
  {
    priceId: 7, code: 'NADA_CONSTA', name: 'Certidão de nada consta', group: 'Conclusão e Expedição de Documentos',
    paymentCategory: 'INTERNAL_PROCESS', processType: 'CERTIFICATE', value: 100, valueSource: 'GLOBAL',
    valueDefined: true, defaultDescription: 'Certidão de nada consta',
  },
];

const POLICY_SECRETARIA = { enforceTablePrice: true, canRequestException: true, canDecideException: false };
const POLICY_ADMIN = { enforceTablePrice: true, canRequestException: true, canDecideException: true };

const PENDING = {
  id: 9, studentId: 1, studentName: 'Maria Teste', academicLevel: 'GRADUATION', chargePriceId: 7,
  procedureName: 'Certidão de nada consta', tableAmount: 100, requestedAmount: 60, dueDate: '2026-12-01',
  description: null, reason: 'Bolsa parcial concedida pela direção', status: 'PENDING', requestedBy: 'ana',
  requestedAt: '2026-10-21T10:00:00', decidedBy: null, decidedAt: null, decisionReason: null, paymentId: null,
};

function mockFinance(policy: unknown) {
  getMock.mockImplementation((url: string) => {
    if (url === '/finance/charge-policy') return Promise.resolve({ data: policy });
    if (url === '/finance/charge-catalog') return Promise.resolve({ data: CATALOG });
    if (url.startsWith('v1/grad-students/')) return Promise.resolve({ data: { fullName: 'Maria Teste', cpf: '39012874600' } });
    return Promise.resolve({ data: [] });
  });
}

async function openFormAndPickProcedure() {
  render(<FinancePage />);
  await userEvent.click(await screen.findByText(/Gerar Nova Cobrança/));
  await userEvent.type(await screen.findByPlaceholderText('ID do Aluno no Sistema'), '1');
  await screen.findByText('Maria Teste', {}, { timeout: 4000 });
  await userEvent.selectOptions(await screen.findByLabelText('Procedimento cobrado'), '7');
}

describe('FinancePage com a regra "valor sempre da tabela" ligada', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    postMock.mockResolvedValue({ data: {} });
  });

  test('some a opção de valor manual e o campo de valor fica travado', async () => {
    mockFinance(POLICY_SECRETARIA);
    render(<FinancePage />);
    await userEvent.click(await screen.findByText(/Gerar Nova Cobrança/));

    expect(await screen.findByRole('option', { name: 'Escolha o procedimento…' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /informar o valor manualmente/ })).not.toBeInTheDocument();
    expect((screen.getByLabelText('Valor (R$)') as HTMLInputElement).readOnly).toBe(true);
  });

  test('sem procedimento escolhido o envio é recusado e nada é enviado', async () => {
    mockFinance(POLICY_SECRETARIA);
    render(<FinancePage />);
    await userEvent.click(await screen.findByText(/Gerar Nova Cobrança/));
    await userEvent.type(await screen.findByPlaceholderText('ID do Aluno no Sistema'), '1');
    await screen.findByText('Maria Teste', {}, { timeout: 4000 });
    await userEvent.type(document.querySelector('input[type="date"]') as HTMLInputElement, '2026-12-01');

    await userEvent.click(screen.getByText('Confirmar e Lançar'));

    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/Escolha o procedimento/));
    expect(postMock).not.toHaveBeenCalledWith('/finance/charge', expect.anything());
  });

  test('a secretária vê o botão de exceção; o pedido leva o procedimento e a justificativa', async () => {
    mockFinance(POLICY_SECRETARIA);
    await openFormAndPickProcedure();
    await userEvent.type(document.querySelector('input[type="date"]') as HTMLInputElement, '2026-12-01');

    await userEvent.click(await screen.findByText('Pedir valor diferente (exceção)'));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Valor pedido (R$)'), '60');
    await userEvent.type(within(dialog).getByLabelText(/Justificativa/), 'Bolsa parcial concedida pela direção');
    await userEvent.click(within(dialog).getByText('Enviar pedido'));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/charge-exceptions', {
      studentId: 1, academicLevel: 'GRADUATION', chargePriceId: 7, dueDate: '2026-12-01', description: 'Certidão de nada consta',
      requestedAmount: 60, reason: 'Bolsa parcial concedida pela direção',
    }));
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringMatching(/aprovação de um administrador/));
  });

  test('justificativa curta não envia o pedido', async () => {
    mockFinance(POLICY_SECRETARIA);
    await openFormAndPickProcedure();
    await userEvent.type(document.querySelector('input[type="date"]') as HTMLInputElement, '2026-12-01');
    await userEvent.click(await screen.findByText('Pedir valor diferente (exceção)'));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Valor pedido (R$)'), '60');
    await userEvent.type(within(dialog).getByLabelText(/Justificativa/), 'curta');
    await userEvent.click(within(dialog).getByText('Enviar pedido'));

    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/justificativa/i));
    expect(postMock).not.toHaveBeenCalledWith('/finance/charge-exceptions', expect.anything());
  });

  test('quem não pode pedir exceção não vê o botão nem o link', async () => {
    mockFinance({ enforceTablePrice: true, canRequestException: false, canDecideException: false });
    await openFormAndPickProcedure();

    expect(screen.queryByText('Pedir valor diferente (exceção)')).not.toBeInTheDocument();
    expect(screen.queryByText('Pedidos de exceção')).not.toBeInTheDocument();
  });

  test('com a regra desligada nada muda: opção manual presente e sem botão de exceção', async () => {
    mockFinance({ enforceTablePrice: false, canRequestException: false, canDecideException: false });
    render(<FinancePage />);
    await userEvent.click(await screen.findByText(/Gerar Nova Cobrança/));

    expect(await screen.findByRole('option', { name: /informar o valor manualmente/ })).toBeInTheDocument();
    expect((screen.getByLabelText('Valor (R$)') as HTMLInputElement).readOnly).toBe(false);
  });
});

describe('Página de pedidos de exceção', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    postMock.mockResolvedValue({ data: {} });
  });

  function mockList(policy: unknown, rows: unknown[]) {
    getMock.mockImplementation((url: string) =>
      Promise.resolve({ data: url === '/finance/charge-policy' ? policy : rows }));
  }

  test('o administrador vê os botões de decisão, e aprovar chama a API', async () => {
    mockList(POLICY_ADMIN, [PENDING]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ChargeExceptionsPage />);

    expect(await screen.findByText('Maria Teste')).toBeInTheDocument();
    expect(screen.getByText('Aguardando aprovação')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Aprovar'));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/charge-exceptions/9/approve', {}));
  });

  test('rejeitar exige o motivo e o envia', async () => {
    mockList(POLICY_ADMIN, [PENDING]);
    vi.spyOn(window, 'prompt').mockReturnValue('Fora da política de bolsas');
    render(<ChargeExceptionsPage />);

    await userEvent.click(await screen.findByText('Rejeitar'));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/finance/charge-exceptions/9/reject', { reason: 'Fora da política de bolsas' }));
  });

  test('motivo em branco na rejeição não chama a API', async () => {
    mockList(POLICY_ADMIN, [PENDING]);
    vi.spyOn(window, 'prompt').mockReturnValue('   ');
    render(<ChargeExceptionsPage />);

    await userEvent.click(await screen.findByText('Rejeitar'));

    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/motivo/i));
    expect(postMock).not.toHaveBeenCalled();
  });

  test('a secretária acompanha os pedidos mas não vê botões de decisão', async () => {
    mockList({ enforceTablePrice: true, canRequestException: true, canDecideException: false }, [PENDING]);
    render(<ChargeExceptionsPage />);

    expect(await screen.findByText('Maria Teste')).toBeInTheDocument();
    expect(screen.queryByText('Aprovar')).not.toBeInTheDocument();
    expect(screen.queryByText('Rejeitar')).not.toBeInTheDocument();
  });

  test('pedido já decidido não mostra botões nem para o administrador', async () => {
    mockList(POLICY_ADMIN, [{ ...PENDING, status: 'APPROVED', decidedBy: 'adm2', paymentId: 55 }]);
    render(<ChargeExceptionsPage />);

    expect(await screen.findByText('Aprovado')).toBeInTheDocument();
    expect(screen.queryByText('Aprovar')).not.toBeInTheDocument();
  });

  test('falha ao carregar mostra aviso', async () => {
    getMock.mockRejectedValue({ response: { status: 403 } });
    render(<ChargeExceptionsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/permissão/);
  });

  test('lista vazia mostra a mensagem', async () => {
    mockList(POLICY_ADMIN, []);
    render(<ChargeExceptionsPage />);

    expect(await screen.findByText('Nenhum pedido de exceção.')).toBeInTheDocument();
  });
});
