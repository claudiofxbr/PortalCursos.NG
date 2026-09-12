import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock, postMock, putMock, deleteMock, toastSuccess, toastError } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: postMock, put: putMock, delete: deleteMock },
}));

vi.mock('sonner', () => ({
  toast: { success: toastSuccess, error: toastError },
}));

import GradEnrollPage from '@/app/academic/enroll/page';

const STUDENT = {
  id: 1,
  fullName: 'Maria Silva',
  email: 'maria@example.com',
  cpf: '111.111.111-11',
  phone: '(71) 99999-0000',
  currentCourse: 'Engenharia',
  enrollmentStatus: 'PENDENTE_VALIDACAO',
  registrationNumber: '2026001',
  documents: [],
};

describe('GradEnrollPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMock.mockResolvedValue({ data: [] });
  });

  test('carrega e lista alunos matriculados', async () => {
    getMock.mockResolvedValueOnce({ data: [STUDENT] });

    render(<GradEnrollPage />);

    expect(await screen.findByText('Maria Silva')).toBeInTheDocument();
    expect(getMock).toHaveBeenCalledWith('v1/grad-students');
    expect(screen.getByText('SEM DOCS')).toBeInTheDocument();
  });

  test('mostra erro quando a listagem falha', async () => {
    getMock.mockReset();
    getMock.mockRejectedValueOnce(new Error('network'));

    render(<GradEnrollPage />);

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Erro ao carregar alunos. Verifique o backend.'));
  });

  test('matricula aluno com upload de documento (multipart/form-data)', async () => {
    postMock.mockResolvedValueOnce({ data: {} });
    const user = userEvent.setup();

    render(<GradEnrollPage />);
    await waitFor(() => expect(getMock).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: /nova inscrição/i }));

    await user.type(document.querySelector('input[name="fullName"]') as HTMLInputElement, 'João Souza');
    await user.type(document.querySelector('input[name="email"]') as HTMLInputElement, 'joao@example.com');
    await user.type(document.querySelector('input[name="cpf"]') as HTMLInputElement, '222.222.222-22');

    const file = new File(['conteudo'], 'rg.pdf', { type: 'application/pdf' });
    const fileInput = document.querySelector('input[name="rgCpf"]') as HTMLInputElement;
    await user.upload(fileInput, file);
    expect(fileInput.files?.[0]).toBe(file);

    await user.click(screen.getByRole('button', { name: /finalizar matrícula/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    const [url, body, config] = postMock.mock.calls[0];
    expect(url).toBe('v1/grad-students/enroll');
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('fullName')).toBe('João Souza');
    expect(body.get('rgCpf')).toBeInstanceOf(File);
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('✅ Aluno matriculado com sucesso!'));
  });

  test('mostra erro da API quando a matricula falha', async () => {
    postMock.mockRejectedValueOnce({ response: { data: { message: 'CPF já cadastrado.' } } });
    const user = userEvent.setup();

    render(<GradEnrollPage />);
    await waitFor(() => expect(getMock).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: /nova inscrição/i }));
    await user.type(document.querySelector('input[name="fullName"]') as HTMLInputElement, 'João Souza');
    await user.type(document.querySelector('input[name="email"]') as HTMLInputElement, 'joao@example.com');
    await user.type(document.querySelector('input[name="cpf"]') as HTMLInputElement, '222.222.222-22');
    await user.click(screen.getByRole('button', { name: /finalizar matrícula/i }));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('CPF já cadastrado.'));
  });

  test('atualiza status do aluno via select', async () => {
    getMock.mockResolvedValueOnce({ data: [STUDENT] });
    putMock.mockResolvedValueOnce({ data: {} });
    const user = userEvent.setup();

    render(<GradEnrollPage />);
    await screen.findByText('Maria Silva');

    await user.selectOptions(screen.getByDisplayValue('EM ANÁLISE'), 'APROVADO');

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith('v1/grad-students/1/status?status=APROVADO')
    );
    expect(toastSuccess).toHaveBeenCalledWith('Status atualizado!');
  });

  test('remove aluno apos confirmacao', async () => {
    getMock.mockResolvedValueOnce({ data: [STUDENT] });
    deleteMock.mockResolvedValueOnce({ data: {} });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();

    render(<GradEnrollPage />);
    await screen.findByText('Maria Silva');

    const row = screen.getByText('Maria Silva').closest('tr') as HTMLElement;
    await user.click(within(row).getByRole('button'));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('v1/grad-students/1'));
    expect(toastSuccess).toHaveBeenCalledWith('Aluno removido.');
  });

  test('nao remove aluno se a confirmacao for cancelada', async () => {
    getMock.mockResolvedValueOnce({ data: [STUDENT] });
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();

    render(<GradEnrollPage />);
    await screen.findByText('Maria Silva');

    const row = screen.getByText('Maria Silva').closest('tr') as HTMLElement;
    await user.click(within(row).getByRole('button'));

    expect(deleteMock).not.toHaveBeenCalled();
  });
});
