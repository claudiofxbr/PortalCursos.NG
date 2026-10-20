import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { AuthProvider, useAuth } from '@/app/context/AuthContext';

function Probe() {
  const { isAuthenticated, isLoading, user } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="user">{user?.username ?? '-'}</span>
    </div>
  );
}

const renderProvider = () =>
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );

/**
 * E-04: a consulta de sessão feita a cada carga de página usa ?optional=true, de modo que "sem sessão" vira
 * 200 {authenticated:false} em vez de 401 (que enchia o console do navegador de erros antes do login).
 */
describe('AuthProvider: consulta de sessão opcional', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('pede a sessão no modo optional', async () => {
    getMock.mockResolvedValue({ data: { authenticated: false } });
    renderProvider();

    await waitFor(() => expect(getMock).toHaveBeenCalled());
    expect(getMock).toHaveBeenCalledWith('auth/me', { params: { optional: true } });
  });

  test('{authenticated:false} deixa o usuário deslogado, sem erro e sem tratar o corpo como usuário', async () => {
    getMock.mockResolvedValue({ data: { authenticated: false } });
    renderProvider();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('auth').textContent).toBe('false');
    expect(screen.getByTestId('user').textContent).toBe('-');
  });

  test('sessão válida continua autenticando e mostrando o usuário', async () => {
    getMock.mockResolvedValue({ data: { id: 5, username: 'maria.teste', email: 'm@x.com', roles: ['ROLE_SECRETARIA'] } });
    renderProvider();

    await waitFor(() => expect(screen.getByTestId('auth').textContent).toBe('true'));
    expect(screen.getByTestId('user').textContent).toBe('maria.teste');
  });

  test('um backend antigo que responde 401 continua tratado como sem sessão', async () => {
    getMock.mockRejectedValue({ response: { status: 401 } });
    renderProvider();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('auth').textContent).toBe('false');
  });

  test('falha de rede também termina o carregamento sem autenticar', async () => {
    getMock.mockRejectedValue(new Error('network'));
    renderProvider();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('auth').textContent).toBe('false');
  });
});
