'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import api from '../../services/api';
import { toast } from 'sonner';
import { asHttpError } from '@/app/services/httpError';
import {
  DEFAULT_POLICY, exceptionErrorMessage, formatMoney, normalizePolicy, statusLabel, type ChargeException, type ChargePolicy,
} from '../chargeExceptions';

const STATUS_COLOR: Record<ChargeException['status'], { bg: string; fg: string }> = {
  PENDING: { bg: '#fff7e0', fg: '#8a6100' },
  APPROVED: { bg: '#e6f6ea', fg: '#1e7a34' },
  REJECTED: { bg: '#fdeaea', fg: '#a82424' },
};

/** Lista os pedidos de exceção ao valor da tabela: o administrador decide; a secretária acompanha os dela. */
export default function ChargeExceptionsPage() {
  const [policy, setPolicy] = useState<ChargePolicy>(DEFAULT_POLICY);
  const [rows, setRows] = useState<ChargeException[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [policyRes, listRes] = await Promise.all([
        api.get('/finance/charge-policy'),
        api.get('/finance/charge-exceptions'),
      ]);
      setPolicy(normalizePolicy(policyRes.data));
      setRows(Array.isArray(listRes.data) ? listRes.data : []);
      setError(null);
    } catch (errCaught: unknown) {
      setError(exceptionErrorMessage(asHttpError(errCaught)?.response?.status));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- carga inicial dos pedidos ao abrir a página
    load();
  }, [load]);

  const decide = async (row: ChargeException, action: 'approve' | 'reject') => {
    let reason: string | null = null;
    if (action === 'reject') {
      reason = window.prompt('Motivo da rejeição (obrigatório):');
      if (reason === null) return;
      if (reason.trim() === '') {
        toast.error('Informe o motivo da rejeição.');
        return;
      }
    } else if (!window.confirm(`Aprovar ${formatMoney(row.requestedAmount)} para ${row.studentName ?? 'o aluno'}? A cobrança será emitida com esse valor.`)) {
      return;
    }
    setBusyId(row.id);
    try {
      await api.post(`/finance/charge-exceptions/${row.id}/${action}`, reason === null ? {} : { reason });
      toast.success(action === 'approve' ? 'Pedido aprovado e cobrança emitida.' : 'Pedido rejeitado.');
      await load();
    } catch (errCaught: unknown) {
      const response = asHttpError(errCaught)?.response;
      toast.error(exceptionErrorMessage(response?.status, response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="fade-in" style={{ paddingBottom: '4rem' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ fontSize: '2rem', fontWeight: 800 }}>Pedidos de exceção de valor</h2>
          <p style={{ color: 'var(--text-secondary)' }}>
            O valor das cobranças vem sempre da tabela. Um valor diferente só vale com a aprovação de um administrador.
          </p>
        </div>
        <Link href="/finance" style={{ padding: '12px 24px', borderRadius: '12px', fontWeight: 700, background: '#f3f0ff', color: '#5b3cc4' }}>Voltar às cobranças</Link>
      </header>

      {loading && <p>Carregando pedidos…</p>}
      {error && <p role="alert" style={{ color: '#c0392b' }}>{error}</p>}
      {!loading && !error && rows.length === 0 && <p>Nenhum pedido de exceção.</p>}

      {rows.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', fontSize: '0.8rem', color: '#666' }}>
                <th style={{ padding: '10px' }}>Aluno</th>
                <th style={{ padding: '10px' }}>Procedimento</th>
                <th style={{ padding: '10px', textAlign: 'right' }}>Tabela</th>
                <th style={{ padding: '10px', textAlign: 'right' }}>Pedido</th>
                <th style={{ padding: '10px' }}>Justificativa</th>
                <th style={{ padding: '10px' }}>Pedido por</th>
                <th style={{ padding: '10px' }}>Situação</th>
                <th style={{ padding: '10px' }}></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.id} style={{ borderTop: '1px solid #eee', verticalAlign: 'top' }}>
                  <td style={{ padding: '10px' }}>{row.studentName ?? `#${row.studentId}`}</td>
                  <td style={{ padding: '10px' }}>{row.procedureName ?? `#${row.chargePriceId}`}</td>
                  <td style={{ padding: '10px', textAlign: 'right' }}>{formatMoney(row.tableAmount)}</td>
                  <td style={{ padding: '10px', textAlign: 'right', fontWeight: 700 }}>{formatMoney(row.requestedAmount)}</td>
                  <td style={{ padding: '10px', maxWidth: '280px' }}>
                    {row.reason}
                    {row.decisionReason && <div style={{ color: '#666', fontSize: '0.8rem', marginTop: '4px' }}>Decisão: {row.decisionReason}</div>}
                  </td>
                  <td style={{ padding: '10px' }}>{row.requestedBy}</td>
                  <td style={{ padding: '10px' }}>
                    <span style={{ padding: '4px 10px', borderRadius: '999px', fontSize: '0.75rem', fontWeight: 700, background: STATUS_COLOR[row.status].bg, color: STATUS_COLOR[row.status].fg }}>
                      {statusLabel(row.status)}
                    </span>
                  </td>
                  <td style={{ padding: '10px', whiteSpace: 'nowrap' }}>
                    {policy.canDecideException && row.status === 'PENDING' && (
                      <>
                        <button type="button" disabled={busyId === row.id} onClick={() => decide(row, 'approve')}
                          style={{ marginRight: '8px', padding: '8px 14px', borderRadius: '8px', border: 'none', background: '#1e7a34', color: 'white', fontWeight: 700, cursor: 'pointer' }}>Aprovar</button>
                        <button type="button" disabled={busyId === row.id} onClick={() => decide(row, 'reject')}
                          style={{ padding: '8px 14px', borderRadius: '8px', border: '1px solid #a82424', background: 'white', color: '#a82424', fontWeight: 700, cursor: 'pointer' }}>Rejeitar</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
