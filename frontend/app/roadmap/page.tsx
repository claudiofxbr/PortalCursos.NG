'use client';

import React from 'react';

const ROADMAP_DATA = [
    {
        title: 'Fase 1: Infraestrutura MEC 2026',
        status: 'Concluído',
        description: 'Configuração de banco de dados Neon, Spring Boot 3 e Next.js 14.',
        items: ['Migração PostgreSQL', 'Setup JWT', 'Design System Premium']
    },
    {
        title: 'Fase 2: Gestão de 10 Perfis Institucionais',
        status: 'Em Execução',
        description: 'Implementação de permissões granulares para Root, Admin, Professores e Alunos.',
        items: ['Cadastro de Usuários', 'RBAC (Role Based Access Control)', 'Dashboard Customizado']
    },
    {
        title: 'Fase 3: Gestão de Cursos (Novos Parâmetros)',
        status: 'Concluído',
        description: 'Adaptação dos formulários de curso para exigências regulatórias.',
        items: ['Código IES', 'Modalidade EaD/Presencial', 'Áreas CNPq/CAPES']
    },
    {
        title: 'Fase 4: Módulo Financeiro & Documental',
        status: 'Planejado',
        description: 'Integração de pagamentos Pix/Boleto e gestão de documentos de matrícula.',
        items: ['Checkout Transparente', 'Upload de Documentos', 'Validação de Matrícula']
    }
];

export default function RoadmapPage() {
    return (
        <div style={{ maxWidth: '1000px', margin: '0 auto', padding: '2rem 0' }}>
            <div style={{ textAlign: 'center', marginBottom: '4rem' }}>
                <h1 style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--secondary-color)', marginBottom: '1rem' }}>
                    🚀 Novo Plano de Implementação
                </h1>
                <p style={{ fontSize: '1.1rem', opacity: 0.7, maxWidth: '600px', margin: '0 auto' }}>
                    Acompanhamento em tempo real da modernização tecnológica do PortalCursos.NG-02 para conformidade institucional.
                </p>
            </div>

            <div style={{ position: 'relative' }}>
                {/* Vertical Line */}
                <div style={{ 
                    position: 'absolute', left: '50%', transform: 'translateX(-50%)', 
                    width: '4px', height: '100%', backgroundColor: 'rgba(255,255,255,0.05)', 
                    borderRadius: '2px', zIndex: 0 
                }} />

                {ROADMAP_DATA.map((phase, index) => (
                    <div key={index} style={{ 
                        display: 'flex', justifyContent: index % 2 === 0 ? 'flex-start' : 'flex-end', 
                        marginBottom: '3rem', position: 'relative', zIndex: 1 
                    }}>
                        <div style={{ 
                            width: '45%', backgroundColor: 'rgba(255,255,255,0.03)', 
                            border: '1px solid rgba(255,255,255,0.1)', borderRadius: '15px', 
                            padding: '1.5rem', backdropFilter: 'blur(10px)' 
                        }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                                <span style={{ 
                                    padding: '4px 12px', borderRadius: '20px', fontSize: '0.75rem', fontWeight: 700,
                                    backgroundColor: phase.status === 'Concluído' ? 'rgba(46, 204, 113, 0.2)' : 'rgba(241, 196, 15, 0.2)',
                                    color: phase.status === 'Concluído' ? '#2ecc71' : '#f1c40f'
                                }}>
                                    {phase.status}
                                </span>
                                <span style={{ opacity: 0.3, fontWeight: 900, fontSize: '1.5rem' }}>0{index + 1}</span>
                            </div>
                            <h3 style={{ fontSize: '1.2rem', marginBottom: '0.8rem', color: '#fff' }}>{phase.title}</h3>
                            <p style={{ fontSize: '0.9rem', opacity: 0.6, marginBottom: '1rem', lineHeight: '1.5' }}>{phase.description}</p>
                            <ul style={{ paddingLeft: '1.2rem', margin: 0 }}>
                                {phase.items.map((item, i) => (
                                    <li key={i} style={{ fontSize: '0.85rem', opacity: 0.8, marginBottom: '0.4rem' }}>{item}</li>
                                ))}
                            </ul>
                        </div>
                        {/* Dot in center */}
                        <div style={{ 
                            position: 'absolute', left: '50%', top: '20px', transform: 'translateX(-50%)', 
                            width: '20px', height: '20px', borderRadius: '50%', 
                            backgroundColor: phase.status === 'Concluído' ? '#2ecc71' : '#f1c40f',
                            border: '4px solid #0f172a', boxShadow: '0 0 15px rgba(0,0,0,0.5)'
                        }} />
                    </div>
                ))}
            </div>

            <div style={{ marginTop: '5rem', textAlign: 'center', opacity: 0.3, fontSize: '0.8rem' }}>
                PortalCursos.NG-02 | Sistema Autenticado MEC 2026
            </div>
        </div>
    );
}
