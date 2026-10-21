'use client';

import React, { useState } from 'react';
import api from '../services/api';
import { toast } from 'sonner';
import { asHttpError } from '@/app/services/httpError';
import type { CatalogItem } from './chargeCatalog';
import {
  buildExceptionPayload, EMPTY_EXCEPTION_FORM, exceptionErrorMessage, formatMoney, MIN_REASON_LENGTH,
  validateExceptionRequest, type ExceptionForm,
} from './chargeExceptions';

interface Props {
  item: CatalogItem;
  studentId: string;
  studentName?: string;
  academicLevel: 'GRADUATION' | 'POSTGRADUATE';
  dueDate: string;
  description: string;
  onClose: () => void;
  onSent: () => void;
}

/** Pedido de valor diferente do da tabela. Nada é cobrado até um administrador aprovar. */
export function ExceptionRequestModal({ item, studentId, studentName, academicLevel, dueDate, description, onClose, onSent }: Props) {
  const [form, setForm] = useState<ExceptionForm>(EMPTY_EXCEPTION_FORM);
  const [sending, setSending] = useState(false);
  const tableValue = item.value ?? 0;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const problem = validateExceptionRequest(form, tableValue);
    if (problem) {
      toast.error(problem);
      return;
    }
    if (!dueDate) {
      toast.error('Informe a data de vencimento no formulário antes de pedir a exceção.');
      return;
    }
    setSending(true);
    try {
      await api.post('/finance/charge-exceptions', buildExceptionPayload(form, item, studentId, academicLevel, dueDate, description));
      toast.success('Pedido enviado. A cobrança só será emitida depois da aprovação de um administrador.');
      onSent();
    } catch (errCaught: unknown) {
      const response = asHttpError(errCaught)?.response;
      toast.error(exceptionErrorMessage(response?.status, response?.data?.message));
    } finally {
      setSending(false);
    }
  };

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="exception-title" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, padding: '1rem' }}>
      <form onSubmit={submit} style={{ background: 'white', borderRadius: '16px', padding: '2rem', width: '100%', maxWidth: '520px', maxHeight: '90vh', overflowY: 'auto' }}>
        <h3 id="exception-title" style={{ fontSize: '1.3rem', fontWeight: 800, marginBottom: '0.5rem' }}>Pedir valor diferente do da tabela</h3>
        <p style={{ color: '#666', fontSize: '0.9rem', marginBottom: '1.25rem' }}>
          {item.name}{studentName ? ` — ${studentName}` : ''}. Valor da tabela: <strong>{formatMoney(tableValue)}</strong>.
          O pedido precisa ser aprovado por um administrador; enquanto isso, nada é cobrado.
        </p>

        <label htmlFor="exception-amount" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Valor pedido (R$)</label>
        <input id="exception-amount" type="number" step="0.01" min="0" required value={form.requestedAmount}
          onChange={e => setForm({ ...form, requestedAmount: e.target.value })}
          style={{ width: '100%', padding: '12px', borderRadius: '10px', border: '2px solid #eee', marginBottom: '1.25rem' }} placeholder="0.00" />

        <label htmlFor="exception-reason" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Justificativa (obrigatória)</label>
        <textarea id="exception-reason" rows={4} required value={form.reason}
          onChange={e => setForm({ ...form, reason: e.target.value })}
          style={{ width: '100%', padding: '12px', borderRadius: '10px', border: '2px solid #eee' }}
          placeholder={`Explique o motivo (mínimo de ${MIN_REASON_LENGTH} caracteres)`} />

        <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
          <button type="button" onClick={onClose} style={{ padding: '12px 22px', borderRadius: '10px', border: 'none', background: '#f5f5f5', color: '#666', fontWeight: 600, cursor: 'pointer' }}>Cancelar</button>
          <button type="submit" disabled={sending} className="btn-primary" style={{ padding: '12px 28px', borderRadius: '10px', fontWeight: 700 }}>
            {sending ? 'Enviando…' : 'Enviar pedido'}
          </button>
        </div>
      </form>
    </div>
  );
}
