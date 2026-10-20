'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import api from '../../../services/api';
import { courseLabel, parseMoney, priceErrorMessage, type PriceRow, type PriceTable } from '../priceTable';

interface Scope {
  id: number;
  staffId: number;
  courseId: string;
  head: boolean;
}
interface Staff {
  id: number;
  fullName: string;
  position?: string;
}
interface Course {
  id: string;
  denominacaoCurso?: string;
}

type ErrorLike = { response?: { status?: number; data?: { message?: unknown } } };

const courseName = (c: Course) => c.denominacaoCurso || `Curso ${c.id.slice(0, 8)}…`;

/** Administração da tabela de valores: quem atende cada curso (e quem chefia) e os valores por curso. */
export default function PriceAdminPage() {
  const [scopes, setScopes] = useState<Scope[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [rows, setRows] = useState<PriceRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [busy, setBusy] = useState(false);

  const [staffId, setStaffId] = useState('');
  const [scopeCourseId, setScopeCourseId] = useState('');
  const [head, setHead] = useState(false);

  const [sourceId, setSourceId] = useState('');
  const [rowCourseId, setRowCourseId] = useState('');
  const [rowValue, setRowValue] = useState('');

  const load = useCallback(async () => {
    try {
      const [s, st, c, p] = await Promise.all([
        api.get<Scope[]>('/finance/price-scopes'),
        api.get<Staff[]>('v1/staff'),
        api.get<Course[]>('v1/courses'),
        api.get<PriceTable>('/finance/prices'),
      ]);
      setScopes(s.data);
      setStaff(st.data);
      setCourses(c.data);
      setRows(p.data.rows);
      setDenied(false);
    } catch (err: unknown) {
      if ((err as ErrorLike)?.response?.status === 403) setDenied(true);
      else toast.error('Não foi possível carregar os dados de administração.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Carga inicial dos dados do servidor ao abrir a página.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const staffById = useMemo(() => new Map(staff.map((s) => [s.id, s])), [staff]);
  const courseById = useMemo(() => new Map(courses.map((c) => [c.id, c])), [courses]);
  const globalRows = useMemo(() => rows.filter((r) => r.courseId === null), [rows]);
  const courseRows = useMemo(() => rows.filter((r) => r.courseId !== null), [rows]);

  const run = async (action: () => Promise<unknown>, okMessage: string): Promise<boolean> => {
    setBusy(true);
    try {
      await action();
      toast.success(okMessage);
      await load();
      return true;
    } catch (err: unknown) {
      const response = (err as ErrorLike)?.response;
      toast.error(priceErrorMessage(response?.status, response?.data?.message));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const addScope = async () => {
    if (!staffId || !scopeCourseId) return toast.error('Escolha o colaborador e o curso.');
    const ok = await run(
      () => api.post('/finance/price-scopes', { staffId: Number(staffId), courseId: scopeCourseId, head }),
      'Vínculo salvo.',
    );
    if (ok) {
      setStaffId('');
      setScopeCourseId('');
      setHead(false);
    }
  };

  const toggleHead = (s: Scope) =>
    run(() => api.post('/finance/price-scopes', { staffId: s.staffId, courseId: s.courseId, head: !s.head }),
      s.head ? 'Chefia removida.' : 'Marcado como chefe.');

  const removeScope = (s: Scope) => {
    if (!confirm('Remover este vínculo? O colaborador perde o acesso à tabela deste curso.')) return;
    void run(() => api.delete(`/finance/price-scopes/${s.id}`), 'Vínculo removido.');
  };

  const addCourseRow = async () => {
    if (!sourceId || !rowCourseId) return toast.error('Escolha a cobrança e o curso.');
    let value: number | null = null;
    if (rowValue.trim()) {
      value = parseMoney(rowValue);
      if (value === null) return toast.error('Informe um valor válido, de 0,00 até 9.999.999,99.');
    }
    const ok = await run(
      () => api.post('/finance/prices/course-rows', { sourceId: Number(sourceId), courseId: rowCourseId, value }),
      'Valor por curso criado.',
    );
    if (ok) {
      setSourceId('');
      setRowCourseId('');
      setRowValue('');
    }
  };

  if (loading) return <p style={{ padding: '2rem' }}>Carregando…</p>;

  if (denied) {
    return (
      <div className="glass-panel" style={{ padding: '2rem', maxWidth: 640 }}>
        <h2>Administração da tabela de valores</h2>
        <p>Somente administradores podem acessar esta página.</p>
        <Link href="/finance">Voltar à Gestão Financeira</Link>
      </div>
    );
  }

  return (
    <div className="fade-in" style={{ paddingBottom: '4rem' }}>
      <header style={{ marginBottom: '1.5rem' }}>
        <Link href="/finance/prices" style={{ fontSize: '0.9rem' }}>← Tabela de valores</Link>
        <h2 style={{ fontSize: '2rem', fontWeight: 800, margin: '0.5rem 0' }}>Responsáveis e valores por curso</h2>
        <p style={{ color: 'var(--text-secondary)' }}>
          Defina quem atende cada curso e quem é o chefe da secretaria. Só o chefe propõe valores, e só administradores confirmam.
        </p>
      </header>

      <section aria-label="Responsáveis por curso" className="glass-panel" style={{ padding: '1.25rem', marginBottom: '2rem' }}>
        <h3 style={{ marginTop: 0 }}>Responsáveis por curso</h3>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
                <th style={{ padding: 8 }}>Colaborador</th>
                <th style={{ padding: 8 }}>Curso</th>
                <th style={{ padding: 8 }}>Chefe da secretaria</th>
                <th style={{ padding: 8 }}>Ação</th>
              </tr>
            </thead>
            <tbody>
              {scopes.length === 0 && (
                <tr><td colSpan={4} style={{ padding: 8, color: '#888' }}>Nenhum vínculo cadastrado.</td></tr>
              )}
              {scopes.map((s) => (
                <tr key={s.id} style={{ borderBottom: '1px solid #eee' }}>
                  <td style={{ padding: 8 }}>{staffById.get(s.staffId)?.fullName ?? `Colaborador ${s.staffId}`}</td>
                  <td style={{ padding: 8 }}>{courseById.has(s.courseId) ? courseName(courseById.get(s.courseId)!) : `Curso ${s.courseId.slice(0, 8)}…`}</td>
                  <td style={{ padding: 8 }}>
                    <label>
                      <input type="checkbox" checked={s.head} disabled={busy} onChange={() => void toggleHead(s)} /> {s.head ? 'Chefe' : 'Leitura'}
                    </label>
                  </td>
                  <td style={{ padding: 8 }}>
                    <button disabled={busy} onClick={() => removeScope(s)}>Remover</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: '1rem', alignItems: 'center' }}>
          <select aria-label="Colaborador" value={staffId} disabled={busy} onChange={(e) => setStaffId(e.target.value)}>
            <option value="">Colaborador…</option>
            {staff.map((s) => <option key={s.id} value={s.id}>{s.fullName}{s.position ? ` — ${s.position}` : ''}</option>)}
          </select>
          <select aria-label="Curso do vínculo" value={scopeCourseId} disabled={busy} onChange={(e) => setScopeCourseId(e.target.value)}>
            <option value="">Curso…</option>
            {courses.map((c) => <option key={c.id} value={c.id}>{courseName(c)}</option>)}
          </select>
          <label><input type="checkbox" checked={head} disabled={busy} onChange={(e) => setHead(e.target.checked)} /> Chefe da secretaria</label>
          <button disabled={busy} onClick={() => void addScope()}>Vincular</button>
        </div>
      </section>

      <section aria-label="Valores por curso" className="glass-panel" style={{ padding: '1.25rem' }}>
        <h3 style={{ marginTop: 0 }}>Valores por curso</h3>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          O valor global vale para todos os cursos. Crie um valor específico para que o chefe daquele curso possa propor alterações.
          Depois de criado, o valor é ajustado na tabela principal.
        </p>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <select aria-label="Cobrança" value={sourceId} disabled={busy} onChange={(e) => setSourceId(e.target.value)}>
            <option value="">Cobrança…</option>
            {globalRows.map((r) => <option key={r.id} value={r.id}>{r.category} — {r.name}</option>)}
          </select>
          <select aria-label="Curso do valor" value={rowCourseId} disabled={busy} onChange={(e) => setRowCourseId(e.target.value)}>
            <option value="">Curso…</option>
            {courses.map((c) => <option key={c.id} value={c.id}>{courseName(c)}</option>)}
          </select>
          <input aria-label="Valor inicial (opcional)" placeholder="Valor inicial (opcional)" inputMode="decimal" value={rowValue} disabled={busy} onChange={(e) => setRowValue(e.target.value)} />
          <button disabled={busy} onClick={() => void addCourseRow()}>Criar valor do curso</button>
        </div>

        <h4 style={{ marginBottom: 4 }}>Valores por curso já criados ({courseRows.length})</h4>
        <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.9rem' }}>
          {courseRows.length === 0 && <li style={{ color: '#888' }}>Nenhum ainda.</li>}
          {courseRows.map((r) => <li key={r.id}>{r.name} — {courseLabel(r)}</li>)}
        </ul>
      </section>
    </div>
  );
}
