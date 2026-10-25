'use client';

import React from 'react';

type FinancePagerProps = {
  page: number;
  totalItems: number;
  pageSize: number;
  disabled: boolean;
  onPageChange: (page: number) => void;
};

export function FinancePager({ page, totalItems, pageSize, disabled, onPageChange }: FinancePagerProps) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  if (totalItems <= pageSize) return null;

  const buttonStyle: React.CSSProperties = { padding: '6px 14px', borderRadius: '6px', border: '1px solid #ddd', background: 'white', cursor: 'pointer' };

  return (
    <nav aria-label="Paginação" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '1rem', marginTop: '1rem' }}>
      <button type="button" style={buttonStyle} disabled={disabled || page === 0} onClick={() => onPageChange(page - 1)}>
        Anterior
      </button>
      <span style={{ fontSize: '0.9rem', color: '#666' }}>Página {page + 1} de {totalPages}</span>
      <button type="button" style={buttonStyle} disabled={disabled || page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        Próxima
      </button>
    </nav>
  );
}
