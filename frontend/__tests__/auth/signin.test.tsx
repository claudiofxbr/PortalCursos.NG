import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { replaceMock, loginSuccessMock, postMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  loginSuccessMock: vi.fn(),
  postMock: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

vi.mock('@/app/context/AuthContext', () => ({
  useAuth: () => ({ loginSuccess: loginSuccessMock }),
}));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { post: postMock },
  V_BUILD_ID: 'TEST-BUILD',
}));

import SignInPage from '@/app/auth/signin/page';

describe('SignInPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('faz login com sucesso e redireciona para a home', async () => {
    postMock.mockResolvedValueOnce({ data: { id: 1, username: 'admin', email: 'a@a.com', roles: ['ROLE_ADMIN'] } });
    const user = userEvent.setup();

    render(<SignInPage />);

    await user.type(screen.getByPlaceholderText('admin'), 'admin');
    await user.type(screen.getByPlaceholderText('••••••••'), 'qzWX312#!@');
    await user.click(screen.getByRole('button', { name: /entrar no sistema/i }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith('auth/signin', { username: 'admin', password: 'qzWX312#!@' });
    });
    expect(loginSuccessMock).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }));
    expect(screen.getByText('Acesso Autorizado')).toBeInTheDocument();
  });

  test('mostra mensagem de credenciais invalidas em 401', async () => {
    postMock.mockRejectedValueOnce({ response: { status: 401 } });
    const user = userEvent.setup();

    render(<SignInPage />);

    await user.type(screen.getByPlaceholderText('admin'), 'admin');
    await user.type(screen.getByPlaceholderText('••••••••'), 'senha-errada');
    await user.click(screen.getByRole('button', { name: /entrar no sistema/i }));

    expect(await screen.findByText(/usuário ou senha incorretos/i)).toBeInTheDocument();
    expect(loginSuccessMock).not.toHaveBeenCalled();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  test('mostra mensagem de bloqueio por forca bruta em 423', async () => {
    postMock.mockRejectedValueOnce({
      response: { status: 423, data: { message: 'Conta bloqueada por 15 minutos.' } },
    });
    const user = userEvent.setup();

    render(<SignInPage />);

    await user.type(screen.getByPlaceholderText('admin'), 'admin');
    await user.type(screen.getByPlaceholderText('••••••••'), 'qualquer');
    await user.click(screen.getByRole('button', { name: /entrar no sistema/i }));

    expect(await screen.findByText('Conta bloqueada por 15 minutos.')).toBeInTheDocument();
  });

  test('desabilita o botao de envio durante o carregamento', async () => {
    let resolvePost: (v: unknown) => void = () => {};
    postMock.mockReturnValueOnce(new Promise((resolve) => { resolvePost = resolve; }));
    const user = userEvent.setup();

    render(<SignInPage />);

    await user.type(screen.getByPlaceholderText('admin'), 'admin');
    await user.type(screen.getByPlaceholderText('••••••••'), 'qzWX312#!@');
    await user.click(screen.getByRole('button', { name: /entrar no sistema/i }));

    expect(screen.getByRole('button', { name: /validando credenciais/i })).toBeDisabled();

    resolvePost({ data: { id: 1, username: 'admin', roles: [] } });
    await waitFor(() => expect(loginSuccessMock).toHaveBeenCalled());
  });
});
