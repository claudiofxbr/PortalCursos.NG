import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock },
  BASE_URL: 'http://localhost:8080/api',
}));

import { StudentSearch, searchableLength } from '@/app/finance/StudentSearch';

const HIT = { id: 77, fullName: 'Maria Silva Souza', cpfMasked: '***.***.746-00', registrationNumber: 'G-1' };

describe('Busca de aluno por nome ou CPF', () => {
  beforeEach(() => vi.clearAllMocks());

  test('só os dígitos contam quando o texto parece um CPF', () => {
    expect(searchableLength('390.1')).toBe(4);
    expect(searchableLength('ma')).toBe(2);
    expect(searchableLength('  Maria ')).toBe(5);
  });

  test('abaixo de 3 caracteres não consulta o servidor', async () => {
    render(<StudentSearch level="GRADUATION" onPick={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/buscar aluno/i), 'ma');

    await new Promise(r => setTimeout(r, 600));
    expect(getMock).not.toHaveBeenCalled();
  });

  test('busca, mostra nome e CPF mascarado e entrega o aluno escolhido', async () => {
    getMock.mockResolvedValue({ data: [HIT] });
    const onPick = vi.fn();
    render(<StudentSearch level="POSTGRADUATE" onPick={onPick} />);
    await userEvent.type(screen.getByLabelText(/buscar aluno/i), 'Maria');

    const option = await screen.findByText('Maria Silva Souza');
    expect(screen.getByText(/\*\*\*\.\*\*\*\.746-00/)).toBeInTheDocument();
    expect(getMock).toHaveBeenCalledWith('/finance/students/search', { params: { q: 'Maria', level: 'POSTGRADUATE' } });

    await userEvent.click(option);
    expect(onPick).toHaveBeenCalledWith(HIT);
    expect(screen.queryByText('Maria Silva Souza')).not.toBeInTheDocument();
  });

  test('sem resultado avisa; com falha orienta a usar o ID', async () => {
    getMock.mockResolvedValueOnce({ data: [] });
    const { unmount } = render(<StudentSearch level="GRADUATION" onPick={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/buscar aluno/i), 'Zzzz');
    expect(await screen.findByText('Nenhum aluno encontrado neste nível.')).toBeInTheDocument();
    unmount();

    getMock.mockRejectedValueOnce(new Error('rede'));
    render(<StudentSearch level="GRADUATION" onPick={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/buscar aluno/i), 'Zzzz');
    await waitFor(() => expect(screen.getByText(/informe o ID/i)).toBeInTheDocument());
  });
});
