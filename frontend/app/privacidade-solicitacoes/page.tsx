'use client';

import React, { useEffect, useState } from 'react';
import api from '@/app/services/api';

interface DeletionRequest {
    id: number;
    userId: number;
    requestedUsername: string;
    status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED';
    requestedAt: string;
    reviewedBy?: string;
    reviewedAt?: string;
    reviewNotes?: string;
}

const STATUS_LABEL: Record<string, { label: string; color: string }> = {
    PENDING: { label: 'Pendente', color: '#f39c12' },
    APPROVED: { label: 'Aprovada', color: '#2ecc71' },
    REJECTED: { label: 'Rejeitada', color: '#e74c3c' },
    COMPLETED: { label: 'Concluída', color: '#3498db' },
};

const GLASS_SECTION: React.CSSProperties = {
    background: 'rgba(255, 255, 255, 0.02)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: '16px',
    padding: '1.5rem',
    marginBottom: '1rem',
};

export default function PrivacyRequestsPage() {
    const [requests, setRequests] = useState<DeletionRequest[]>([]);
    const [loading, setLoading] = useState(true);
    const [busyId, setBusyId] = useState<number | null>(null);

    const load = () => {
        setLoading(true);
        api.get('privacy/data-deletion-requests')
            .then(res => setRequests(res.data))
            .catch(() => setRequests([]))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const review = async (id: number, status: 'APPROVED' | 'REJECTED' | 'COMPLETED') => {
        const notes = prompt('Observações da análise (opcional):') || '';
        setBusyId(id);
        try {
            await api.patch(`privacy/data-deletion-requests/${id}`, { status, notes });
            load();
        } finally {
            setBusyId(null);
        }
    };

    return (
        <div className="fade-in">
            <header style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.8rem', color: 'var(--primary-color)' }}>Solicitações de Eliminação de Dados</h2>
                <p style={{ color: 'var(--text-secondary)' }}>
                    LGPD art. 18, VI / GDPR art. 17 — analise cada solicitação observando prazos legais de guarda
                    de registros acadêmicos e financeiros antes de aprovar a eliminação definitiva.
                </p>
            </header>

            {loading && <p>Carregando...</p>}
            {!loading && requests.length === 0 && <p style={{ color: 'var(--text-secondary)' }}>Nenhuma solicitação registrada.</p>}

            {requests.map(req => (
                <div key={req.id} style={GLASS_SECTION}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
                        <div>
                            <strong>{req.requestedUsername}</strong> (usuário id {req.userId})
                            <div style={{ fontSize: '0.8rem', opacity: 0.6 }}>
                                Solicitado em {new Date(req.requestedAt).toLocaleString('pt-BR')}
                            </div>
                            {req.reviewedBy && (
                                <div style={{ fontSize: '0.8rem', opacity: 0.6 }}>
                                    Revisado por {req.reviewedBy} em {req.reviewedAt ? new Date(req.reviewedAt).toLocaleString('pt-BR') : '-'}
                                    {req.reviewNotes ? ` — "${req.reviewNotes}"` : ''}
                                </div>
                            )}
                        </div>
                        <span style={{
                            padding: '4px 12px', borderRadius: '20px', fontSize: '0.75rem', fontWeight: 700,
                            backgroundColor: `${STATUS_LABEL[req.status]?.color}22`, color: STATUS_LABEL[req.status]?.color,
                        }}>
                            {STATUS_LABEL[req.status]?.label || req.status}
                        </span>
                    </div>

                    {req.status === 'PENDING' && (
                        <div style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem' }}>
                            <button
                                disabled={busyId === req.id}
                                onClick={() => review(req.id, 'APPROVED')}
                                className="btn-primary"
                                style={{ padding: '8px 16px', fontSize: '0.85rem' }}
                            >
                                Aprovar
                            </button>
                            <button
                                disabled={busyId === req.id}
                                onClick={() => review(req.id, 'REJECTED')}
                                style={{ padding: '8px 16px', fontSize: '0.85rem', borderRadius: '8px', border: '1px solid rgba(255,107,107,0.5)', backgroundColor: 'rgba(255,60,60,0.15)', color: '#ff6b6b', cursor: 'pointer' }}
                            >
                                Rejeitar
                            </button>
                        </div>
                    )}
                    {req.status === 'APPROVED' && (
                        <div style={{ marginTop: '1rem' }}>
                            <button
                                disabled={busyId === req.id}
                                onClick={() => review(req.id, 'COMPLETED')}
                                className="btn-primary"
                                style={{ padding: '8px 16px', fontSize: '0.85rem' }}
                            >
                                Marcar como concluída (após excluir manualmente os dados)
                            </button>
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}
