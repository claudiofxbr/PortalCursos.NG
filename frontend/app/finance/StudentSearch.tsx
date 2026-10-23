'use client';

import React, { useEffect, useState } from 'react';
import api from '../services/api';

export interface StudentHit {
  id: number;
  fullName: string;
  cpfMasked: string;
  registrationNumber?: string;
}

const MIN_CHARS = 3;
const DEBOUNCE_MS = 400;

/** Quantos caracteres "contam" para liberar a busca: só os dígitos quando o texto parece um CPF. */
export function searchableLength(text: string): number {
  const trimmed = text.trim();
  return /^[\d.\-\s]+$/.test(trimmed) ? trimmed.replace(/\D/g, '').length : trimmed.length;
}

interface Props {
  level: 'GRADUATION' | 'POSTGRADUATE';
  onPick: (student: StudentHit) => void;
}

/** Busca o aluno por nome ou CPF; quem lança a cobrança não precisa conhecer o ID interno. */
export function StudentSearch({ level, onPick }: Props) {
  const [text, setText] = useState('');
  const [hits, setHits] = useState<StudentHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const ready = searchableLength(text) >= MIN_CHARS;

  useEffect(() => {
    if (!ready) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      setLoading(true);
      api.get('/finance/students/search', { params: { q: text.trim(), level } })
        .then(res => {
          if (cancelled) return;
          const list: StudentHit[] = Array.isArray(res.data) ? res.data : [];
          setHits(list);
          setMessage(list.length === 0 ? 'Nenhum aluno encontrado neste nível.' : null);
        })
        .catch(() => {
          if (cancelled) return;
          setHits([]);
          setMessage('Não foi possível buscar agora. Tente de novo ou informe o ID.');
        })
        .finally(() => { if (!cancelled) setLoading(false); });
    }, DEBOUNCE_MS);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [text, level, ready]);

  const pick = (student: StudentHit) => {
    onPick(student);
    setText('');
    setHits([]);
    setMessage(null);
  };

  return (
    <div style={{ marginBottom: '1rem' }}>
      <label htmlFor="student-search" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>
        Buscar aluno por nome ou CPF
      </label>
      <input
        id="student-search"
        type="text"
        autoComplete="off"
        value={text}
        onChange={e => setText(e.target.value)}
        placeholder="Digite ao menos 3 letras do nome ou 3 dígitos do CPF"
        style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee', outline: 'none' }}
      />
      {ready && loading && <small style={{ display: 'block', marginTop: '6px', color: '#888' }}>Buscando…</small>}
      {ready && message && <small role="status" style={{ display: 'block', marginTop: '6px', color: '#888' }}>{message}</small>}
      {ready && hits.length > 0 && (
        <ul role="listbox" aria-label="Alunos encontrados" style={{ listStyle: 'none', margin: '8px 0 0', padding: 0, border: '1px solid #eee', borderRadius: '12px', overflow: 'hidden', background: '#fff' }}>
          {hits.map(hit => (
            <li key={hit.id} role="option" aria-selected={false}>
              <button
                type="button"
                onClick={() => pick(hit)}
                style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', width: '100%', padding: '10px 14px', border: 'none', borderBottom: '1px solid #f3f3f3', background: 'transparent', cursor: 'pointer', textAlign: 'left', color: '#222' }}
              >
                <span style={{ fontWeight: 600 }}>{hit.fullName}</span>
                <span style={{ fontSize: '0.8rem', color: '#777', whiteSpace: 'nowrap' }}>{hit.cpfMasked}{hit.registrationNumber ? ` · ${hit.registrationNumber}` : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
