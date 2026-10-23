'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import api from '../../services/api';
import { normalizePolicy, ruleBanner, type ChargePolicy } from '../chargeExceptions';
import {
  courseLabel,
  formatMoney,
  groupByCategory,
  parseMoney,
  priceErrorMessage,
  roleLabel,
  scopeLabel,
  type PriceRow,
  type PriceTable,
} from './priceTable';

type ErrorLike = { response?: { status?: number; data?: { message?: unknown } } };

export default function PriceTablePage() {
  const [table, setTable] = useState<PriceTable | null>(null);
  const [policy, setPolicy] = useState<ChargePolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await api.get<PriceTable>('/finance/prices');
      setTable(res.data);
      setDenied(false);
      // O aviso da regra é complementar: se a política não carregar, a tabela continua funcionando sem ele.
      api.get('/finance/charge-policy').then((p) => setPolicy(normalizePolicy(p.data))).catch(() => setPolicy(null));
    } catch (err: unknown) {
      if ((err as ErrorLike)?.response?.status === 403) setDenied(true);
      else toast.error('Não foi possível carregar a tabela de valores.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Carga inicial dos dados do servidor ao abrir a página.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const run = async (row: PriceRow, action: () => Promise<unknown>, okMessage: string) => {
    setBusy(row.id);
    try {
      await action();
      toast.success(okMessage);
      setDrafts((d) => ({ ...d, [row.id]: '' }));
      setReasons((r) => ({ ...r, [row.id]: '' }));
      await load();
    } catch (err: unknown) {
      const response = (err as ErrorLike)?.response;
      toast.error(priceErrorMessage(response?.status, response?.data?.message));
    } finally {
      setBusy(null);
    }
  };

  const save = (row: PriceRow) => {
    const value = parseMoney(drafts[row.id] ?? '');
    if (value === null) return toast.error('Informe um valor válido, de 0,00 até 9.999.999,99.');
    void run(row, () => api.put(`/finance/prices/${row.id}`, { value, version: row.version }), 'Valor atualizado.');
  };

  const propose = (row: PriceRow) => {
    const value = parseMoney(drafts[row.id] ?? '');
    if (value === null) return toast.error('Informe um valor válido, de 0,00 até 9.999.999,99.');
    void run(
      row,
      () => api.post(`/finance/prices/${row.id}/proposals`, { value, reason: reasons[row.id] ?? '' }),
      'Proposta enviada. Aguarda a confirmação de um administrador.',
    );
  };

  const approve = (row: PriceRow) =>
    row.pending && void run(row, () => api.post(`/finance/prices/proposals/${row.pending!.id}/approve`), 'Proposta confirmada.');

  const reject = (row: PriceRow) =>
    row.pending &&
    void run(row, () => api.post(`/finance/prices/proposals/${row.pending!.id}/reject`, { reason: reasons[row.id] ?? '' }), 'Proposta rejeitada.');

  if (loading) return <p style={{ padding: '2rem' }}>Carregando tabela de valores…</p>;

  if (denied || !table) {
    return (
      <div className="glass-panel" style={{ padding: '2rem', maxWidth: 640 }}>
        <h2>Tabela de valores</h2>
        <p>Você não tem acesso a esta tabela. Ela é restrita a administradores e às secretarias dos cursos.</p>
        <Link href="/finance">Voltar à Gestão Financeira</Link>
      </div>
    );
  }

  const groups = groupByCategory(table.rows);

  return (
    <div className="fade-in" style={{ paddingBottom: '4rem' }}>
      <header style={{ marginBottom: '1.5rem' }}>
        <Link href="/finance" style={{ fontSize: '0.9rem' }}>← Gestão Financeira</Link>
        {table.role === 'ADMIN' && (
          <Link href="/finance/prices/admin" style={{ fontSize: '0.9rem', marginLeft: '1rem', fontWeight: 700 }}>
            Administração: responsáveis e valores por curso
          </Link>
        )}
        <h2 style={{ fontSize: '2rem', fontWeight: 800, margin: '0.5rem 0' }}>Tabela de valores das cobranças</h2>
        <p style={{ color: 'var(--text-secondary)' }}>
          Seu perfil: <strong>{roleLabel(table.role)}</strong>. Faturas já emitidas não mudam quando um valor é alterado.
        </p>
      </header>

      {policy && (() => {
        const banner = ruleBanner(policy);
        const colors = { waiting: ['#fff7e0', '#8a6100'], active: ['#e6f6ea', '#1e7a34'], off: ['#f0f0f0', '#555'] }[banner.tone];
        return (
          <section role="status" aria-label="Regra do valor da cobrança"
            style={{ padding: '0.9rem 1rem', marginBottom: '1.5rem', borderRadius: 10, background: colors[0], color: colors[1], fontSize: '0.9rem' }}>
            {banner.text}
          </section>
        );
      })()}

      <section aria-label="Quem pode o quê" className="glass-panel" style={{ padding: '1rem', marginBottom: '1.5rem', fontSize: '0.85rem' }}>
        <strong>Quem pode o quê</strong>
        <ul style={{ margin: '0.5rem 0 0', paddingLeft: '1.2rem' }}>
          <li><strong>Administrador:</strong> vê e altera todos os valores e é o único que confirma propostas.</li>
          <li><strong>Chefe de secretaria:</strong> propõe valores só para os cursos que chefia; a proposta só vale após a confirmação de um administrador.</li>
          <li><strong>Secretaria:</strong> somente leitura, nos seus cursos.</li>
        </ul>
      </section>

      {groups.length === 0 && <p>Nenhuma cobrança disponível para o seu perfil.</p>}

      {groups.map((group) => (
        <section key={group.category} style={{ marginBottom: '2rem' }}>
          <h3 style={{ fontSize: '1.1rem', margin: '0 0 0.5rem' }}>{group.category}</h3>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
              <thead>
                <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
                  <th style={{ padding: '8px' }}>Cobrança</th>
                  <th style={{ padding: '8px' }}>Escopo</th>
                  <th style={{ padding: '8px' }}>Curso</th>
                  <th style={{ padding: '8px' }}>Valor vigente</th>
                  <th style={{ padding: '8px' }}>Proposta / novo valor</th>
                  <th style={{ padding: '8px' }}>Situação</th>
                </tr>
              </thead>
              <tbody>
                {group.rows.map((row) => {
                  const editable = row.canEdit || row.canPropose;
                  const disabled = busy === row.id;
                  return (
                    <tr key={row.id} style={{ borderBottom: '1px solid #eee', verticalAlign: 'top' }}>
                      <td style={{ padding: '8px' }}>{row.name}</td>
                      <td style={{ padding: '8px' }}>{scopeLabel(row.academicScope)}</td>
                      <td style={{ padding: '8px' }}>{courseLabel(row)}</td>
                      <td style={{ padding: '8px', fontVariantNumeric: 'tabular-nums' }}>{formatMoney(row.currentValue)}</td>
                      <td style={{ padding: '8px' }}>
                        {row.pending && (
                          <div style={{ background: '#fffbea', padding: '6px', borderRadius: 6, marginBottom: 6 }}>
                            <strong>{formatMoney(row.pending.value)}</strong> proposto por {row.pending.proposedBy}
                            {row.pending.reason ? ` — ${row.pending.reason}` : ''}
                            {table.role === 'ADMIN' && (
                              <div style={{ marginTop: 6, display: 'flex', gap: 6 }}>
                                <button disabled={disabled} onClick={() => approve(row)}>Confirmar</button>
                                <button disabled={disabled} onClick={() => reject(row)}>Rejeitar</button>
                              </div>
                            )}
                          </div>
                        )}
                        {editable ? (
                          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                            <input
                              aria-label={`Novo valor de ${row.name}`}
                              inputMode="decimal"
                              placeholder="0,00"
                              value={drafts[row.id] ?? ''}
                              disabled={disabled}
                              onChange={(e) => setDrafts((d) => ({ ...d, [row.id]: e.target.value }))}
                              style={{ width: 110 }}
                            />
                            {row.canPropose && (
                              <input
                                aria-label={`Justificativa para ${row.name}`}
                                placeholder="Justificativa"
                                value={reasons[row.id] ?? ''}
                                disabled={disabled}
                                onChange={(e) => setReasons((r) => ({ ...r, [row.id]: e.target.value }))}
                                style={{ width: 160 }}
                              />
                            )}
                            {row.canEdit && <button disabled={disabled} onClick={() => save(row)}>Salvar</button>}
                            {row.canPropose && !row.pending && <button disabled={disabled} onClick={() => propose(row)}>Propor</button>}
                          </div>
                        ) : (
                          !row.pending && <span style={{ color: '#888' }}>somente leitura</span>
                        )}
                      </td>
                      <td style={{ padding: '8px' }}>
                        {row.pending ? 'Aguardando confirmação' : row.currentValue === null ? 'A preencher' : 'Vigente'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </div>
  );
}
