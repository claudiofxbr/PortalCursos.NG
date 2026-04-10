'use client';

import React from 'react';
import Link from 'next/link';
import { useAuth } from '@/app/context/AuthContext';

export default function Home() {
  const { user } = useAuth();
  const isAdmin = user?.roles?.some((r: any) => {
    const roleName = typeof r === 'string' ? r : r.name;
    return roleName === 'ROLE_ROOT_MASTER' || roleName === 'ROLE_ADMIN';
  });

  return (
    <div className="fade-in">
      <header style={{ marginBottom: '2.5rem' }}>
        <h2 style={{ fontSize: '1.8rem', color: 'var(--primary-color)' }}>Visão Geral do Campus</h2>
        <p style={{ color: 'var(--text-secondary)' }}>Bem-vindo ao PortalCursos NG-02. Estas são as métricas de hoje.</p>
      </header>

      {/* Grid de KPIs */}
      <section style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', 
        gap: '1.5rem',
        marginBottom: '3rem'
      }}>
        {/* KPI: Alunos */}
        <div className="glass-panel hover-lift" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '1.5rem' }}>🎓</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>ACADÊMICO</span>
          </div>
          <h3 style={{ fontSize: '2.2rem', margin: '1rem 0' }}>1,280</h3>
          <p style={{ fontSize: '0.9rem', color: 'green' }}>↑ 12% est. ativos</p>
        </div>

        {/* KPI: Professores */}
        <div className="glass-panel hover-lift" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '1.5rem' }}>👔</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>DOCENTES</span>
          </div>
          <h3 style={{ fontSize: '2.2rem', margin: '1rem 0' }}>45</h3>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Mestres & Doutores</p>
        </div>

        {/* KPI: Financeiro */}
        <div className="glass-panel hover-lift" style={{ padding: '1.5rem', borderLeft: '4px solid var(--secondary-color)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '1.5rem' }}>💰</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>FINANCEIRO</span>
          </div>
          <h3 style={{ fontSize: '2.2rem', margin: '1rem 0' }}>R$ 42K</h3>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Mês vigente (Pix/Boleto)</p>
        </div>

        {/* KPI: Cursos */}
        <div className="glass-panel hover-lift" style={{ padding: '1.5rem', borderLeft: '4px solid #3498db' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '1.5rem' }}>📚</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>CURSOS</span>
          </div>
          <h3 style={{ fontSize: '2.2rem', margin: '1rem 0' }}>14</h3>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Graduação & Pós</p>
        </div>
      </section>

      {/* Seção de Atividade Recente e Ações Rápidas */}
      <div style={{ display: 'grid', gridTemplateColumns: isAdmin ? '2fr 1fr' : '1fr', gap: '2rem' }}>
        <section className="glass-panel" style={{ padding: '2rem' }}>
          <h3 style={{ marginBottom: '1.5rem' }}>Últimos Alunos Registrados</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                <th style={{ padding: '1rem 0', opacity: 0.6 }}>Nome</th>
                <th style={{ opacity: 0.6 }}>Matrícula</th>
                <th style={{ opacity: 0.6 }}>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <td style={{ padding: '1rem 0' }}>Ricardo Silva</td>
                <td>#2024-001</td>
                <td><span style={{ padding: '4px 10px', backgroundColor: 'rgba(39, 174, 96, 0.2)', color: '#2ecc71', borderRadius: '20px', fontSize: '0.75rem', fontWeight: 700 }}>Ativo</span></td>
              </tr>
              <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <td style={{ padding: '1rem 0' }}>Camila Pontes</td>
                <td>#2024-002</td>
                <td><span style={{ padding: '4px 10px', backgroundColor: 'rgba(39, 174, 96, 0.2)', color: '#2ecc71', borderRadius: '20px', fontSize: '0.75rem', fontWeight: 700 }}>Ativo</span></td>
              </tr>
            </tbody>
          </table>
        </section>

        {isAdmin && (
          <section className="glass-panel" style={{ padding: '2rem', border: '1px solid var(--secondary-color)', position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: '-10px', right: '-10px', fontSize: '4rem', opacity: 0.05 }}>🛡️</div>
            <h3 style={{ marginBottom: '1.5rem', color: 'var(--secondary-color)' }}>Controles Críticos</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <Link href="/users" style={ADMIN_LINK_STYLE}>
                <div style={{ fontSize: '1.2rem' }}>🛡️</div>
                <div>
                  <div style={{ fontWeight: 700 }}>Controle Institucional</div>
                  <div style={{ fontSize: '0.75rem', opacity: 0.6 }}>Gestão de 10 níveis e permissões</div>
                </div>
              </Link>
              <Link href="/courses" style={ADMIN_LINK_STYLE}>
                <div style={{ fontSize: '1.2rem' }}>📚</div>
                <div>
                  <div style={{ fontWeight: 700 }}>Gestão e-MEC 2026</div>
                  <div style={{ fontSize: '0.75rem', opacity: 0.6 }}>Regularização de cursos e IES</div>
                </div>
              </Link>
              <Link href="/roadmap" style={ADMIN_LINK_STYLE}>
                <div style={{ fontSize: '1.2rem' }}>🚀</div>
                <div>
                  <div style={{ fontWeight: 700 }}>Plano de Implementação</div>
                  <div style={{ fontSize: '0.75rem', opacity: 0.6 }}>Acompanhamento de metas</div>
                </div>
              </Link>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

const ADMIN_LINK_STYLE = {
  display: 'flex',
  alignItems: 'center',
  gap: '1rem',
  padding: '1rem',
  backgroundColor: 'rgba(255,255,255,0.03)',
  borderRadius: '12px',
  textDecoration: 'none',
  color: 'inherit',
  border: '1px solid rgba(255,255,255,0.05)',
  transition: 'all 0.2s ease',
};

