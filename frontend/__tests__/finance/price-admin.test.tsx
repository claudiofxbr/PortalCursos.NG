import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock, postMock, deleteMock, toastSuccess, toastError } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  deleteMock: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: postMock, put: vi.fn(), delete: deleteMock },
}));

vi.mock('sonner', () => ({
  toast: { success: toastSuccess, error: toastError },
}));

import PriceAdminPage from '@/app/finance/prices/admin/page';
import { courseLabel } from '@/app/finance/prices/priceTable';

const COURSE_A = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const COURSE_B = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

const globalRow = { id: 10, code: 'REMATRICULA', category: 'Renovação', name: 'Rematrícula periódica', academicScope: 'ALL', courseId: null, currentValue: null, version: 0, canEdit: true, canPropose: false, pending: null };

function serve(overrides: Partial<Record<string, unknown>> = {}) {
  const data: Record<string, unknown> = {
    '/finance/price-scopes': [{ id: 1, staffId: 100, courseId: COURSE_A, head: false }],
    'v1/staff': [{ id: 100, fullName: 'Ana Souza', position: 'Secretária' }, { id: 101, fullName: 'Bruno Lima' }],
    'v1/courses': [{ id: COURSE_A, denominacaoCurso: 'Engenharia' }, { id: COURSE_B, denominacaoCurso: 'Direito' }],
    '/finance/prices': { role: 'ADMIN', rows: [globalRow] },
    ...overrides,
  };
  getMock.mockImplementation((url: string) => {
    const value = data[url];
    if (value instanceof Error) return Promise.reject(value);
    return Promise.resolve({ data: value });
  });
}

describe('courseLabel', () => {
  test('global vira "Todos"; curso mostra o nome; sem nome cai no identificador curto', () => {
    expect(courseLabel({ courseId: null })).toBe('Todos');
    expect(courseLabel({ courseId: COURSE_A, courseName: 'Engenharia' })).toBe('Engenharia');
    expect(courseLabel({ courseId: COURSE_A })).toBe('Curso aaaaaaaa…');
  });
});

describe('PriceAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    postMock.mockResolvedValue({ data: {} });
    deleteMock.mockResolvedValue({ data: {} });
  });

  test('lista vínculos com nome do colaborador e do curso', async () => {
    serve();
    render(<PriceAdminPage />);

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.getAllByText('Engenharia').length).toBeGreaterThan(0);
    expect(screen.getByText('Leitura')).toBeInTheDocument();
  });

  test('vincula colaborador a curso como chefe', async () => {
    serve();
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.selectOptions(screen.getByLabelText('Colaborador'), '101');
    await userEvent.selectOptions(screen.getByLabelText('Curso do vínculo'), COURSE_B);
    await userEvent.click(screen.getByLabelText('Chefe da secretaria', { selector: 'input[type=checkbox]:not([aria-label])' }));
    await userEvent.click(screen.getByText('Vincular'));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith('/finance/price-scopes', { staffId: 101, courseId: COURSE_B, head: true }),
    );
  });

  test('não vincula sem escolher colaborador e curso', async () => {
    serve();
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.click(screen.getByText('Vincular'));

    expect(postMock).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith('Escolha o colaborador e o curso.');
  });

  test('alterna chefia de um vínculo existente', async () => {
    serve();
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.click(screen.getByLabelText('Leitura'));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith('/finance/price-scopes', { staffId: 100, courseId: COURSE_A, head: true }),
    );
  });

  test('remove vínculo só depois de confirmar', async () => {
    serve();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.click(screen.getByText('Remover'));
    expect(deleteMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByText('Remover'));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('/finance/price-scopes/1'));
    confirmSpy.mockRestore();
  });

  test('cria valor por curso a partir de uma cobrança global', async () => {
    serve();
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.selectOptions(screen.getByLabelText('Cobrança'), '10');
    await userEvent.selectOptions(screen.getByLabelText('Curso do valor'), COURSE_B);
    await userEvent.type(screen.getByLabelText('Valor inicial (opcional)'), '150,50');
    await userEvent.click(screen.getByText('Criar valor do curso'));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith('/finance/prices/course-rows', { sourceId: 10, courseId: COURSE_B, value: 150.5 }),
    );
  });

  test('valor inicial inválido não chama a API', async () => {
    serve();
    render(<PriceAdminPage />);
    await screen.findByText('Ana Souza');

    await userEvent.selectOptions(screen.getByLabelText('Cobrança'), '10');
    await userEvent.selectOptions(screen.getByLabelText('Curso do valor'), COURSE_B);
    await userEvent.type(screen.getByLabelText('Valor inicial (opcional)'), 'abc');
    await userEvent.click(screen.getByText('Criar valor do curso'));

    expect(postMock).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith(expect.stringMatching(/valor válido/));
  });

  test('403 mostra que só administradores acessam, sem formulários', async () => {
    serve({ '/finance/price-scopes': Object.assign(new Error('forbidden'), { response: { status: 403 } }) });
    render(<PriceAdminPage />);

    expect(await screen.findByText(/Somente administradores podem acessar/)).toBeInTheDocument();
    expect(screen.queryByText('Vincular')).not.toBeInTheDocument();
  });
});
