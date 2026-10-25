import React from 'react';
import { describe, test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { FinancePager } from '@/app/finance/FinancePager';

describe('FinancePager', () => {
  test('não aparece quando todos os itens cabem em uma página', () => {
    const { container } = render(
      <FinancePager page={0} totalItems={20} pageSize={20} disabled={false} onPageChange={vi.fn()} />
    );
    expect(container.querySelector('nav')).toBeNull();
  });

  test('mostra a página atual e navega para frente e para trás', () => {
    const onPageChange = vi.fn();
    render(<FinancePager page={1} totalItems={45} pageSize={20} disabled={false} onPageChange={onPageChange} />);

    expect(screen.getByText('Página 2 de 3')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    expect(onPageChange).toHaveBeenLastCalledWith(2);
    fireEvent.click(screen.getByRole('button', { name: 'Anterior' }));
    expect(onPageChange).toHaveBeenLastCalledWith(0);
  });

  test('desabilita Anterior na primeira página e Próxima na última', () => {
    const { rerender } = render(
      <FinancePager page={0} totalItems={45} pageSize={20} disabled={false} onPageChange={vi.fn()} />
    );
    expect((screen.getByRole('button', { name: 'Anterior' }) as HTMLButtonElement).disabled).toBe(true);

    rerender(<FinancePager page={2} totalItems={45} pageSize={20} disabled={false} onPageChange={vi.fn()} />);
    expect((screen.getByRole('button', { name: 'Próxima' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
