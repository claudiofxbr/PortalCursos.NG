import React from 'react';
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import SecurityGate from '@/components/SecurityGate';

const fetchMock = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const renderGate = () =>
  render(
    <SecurityGate>
      <p>Conteúdo protegido</p>
    </SecurityGate>,
  );

describe('SecurityGate (tela "Acesso Restrito")', () => {
  test('mostra a tela de acesso e esconde o conteúdo antes da validação', () => {
    renderGate();

    expect(screen.getByRole('heading', { name: 'Acesso Restrito' })).toBeInTheDocument();
    expect(screen.getByLabelText('Código de Acesso')).toBeInTheDocument();
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument();
  });

  // Regressão de E-03: o ícone usava classes Tailwind inexistentes neste projeto e ocupava a largura toda.
  test('o ícone do cadeado tem tamanho definido por estilo próprio (não depende de Tailwind)', () => {
    const { container } = renderGate();
    const svg = container.querySelector('svg') as SVGElement;

    expect(svg.getAttribute('width')).toBe('32');
    expect(svg.getAttribute('height')).toBe('32');
    expect((svg as unknown as HTMLElement).style.width).toBe('32px');
    expect(container.querySelector('[class*="h-8"], [class*="w-8"], [class*="bg-slate"]')).toBeNull();
  });

  test('o cartão e o fundo têm estilo inline (tela cheia, centralizada)', () => {
    const { container } = renderGate();
    const overlay = container.firstElementChild as HTMLElement;

    expect(overlay.style.position).toBe('fixed');
    expect(overlay.style.display).toBe('flex');
    expect((overlay.firstElementChild as HTMLElement).style.maxWidth).toBe('448px');
  });

  test('campo vazio mostra a validação e não chama o servidor', async () => {
    renderGate();

    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Digite o código de acesso.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('só espaços também é tratado como vazio', async () => {
    renderGate();

    await userEvent.type(screen.getByLabelText('Código de Acesso'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Digite o código de acesso.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test('código incorreto mostra a mensagem, limpa o campo e continua bloqueado', async () => {
    fetchMock.mockResolvedValue({ ok: false, json: async () => ({ ok: false }) });
    renderGate();

    await userEvent.type(screen.getByLabelText('Código de Acesso'), 'errado');
    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Senha incorreta. Tente novamente.');
    expect(screen.getByLabelText('Código de Acesso')).toHaveValue('');
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument();
    expect(sessionStorage.getItem('portal_authorized')).toBeNull();
  });

  test('falha de rede também mostra a mensagem e mantém o bloqueio', async () => {
    fetchMock.mockRejectedValue(new TypeError('network'));
    renderGate();

    await userEvent.type(screen.getByLabelText('Código de Acesso'), 'qualquer');
    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Senha incorreta');
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument();
  });

  test('código correto libera o conteúdo e lembra na sessão; o código vai só no corpo do POST', async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    renderGate();

    await userEvent.type(screen.getByLabelText('Código de Acesso'), 'codigo-de-teste');
    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    expect(await screen.findByText('Conteúdo protegido')).toBeInTheDocument();
    expect(sessionStorage.getItem('portal_authorized')).toBe('true');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/portal-auth$/);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ code: 'codigo-de-teste' });
  });

  test('sessão já autorizada pula a tela', async () => {
    sessionStorage.setItem('portal_authorized', 'true');
    renderGate();

    await waitFor(() => expect(screen.getByText('Conteúdo protegido')).toBeInTheDocument());
    expect(screen.queryByRole('heading', { name: 'Acesso Restrito' })).not.toBeInTheDocument();
  });

  test('durante a validação o botão mostra "Validando..." e fica desabilitado', async () => {
    let resolveFetch: (v: unknown) => void = () => {};
    fetchMock.mockReturnValue(new Promise((resolve) => { resolveFetch = resolve; }));
    renderGate();

    await userEvent.type(screen.getByLabelText('Código de Acesso'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Validar Acesso' }));

    const busy = await screen.findByRole('button', { name: 'Validando...' });
    expect(busy).toBeDisabled();
    resolveFetch({ ok: false, json: async () => ({ ok: false }) });
    await screen.findByRole('alert');
  });
});
