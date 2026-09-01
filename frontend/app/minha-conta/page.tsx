'use client';

import Link from 'next/link';
import React, { useEffect, useState } from 'react';
import api from '@/app/services/api';

const GLASS_SECTION: React.CSSProperties = {
    background: 'rgba(255, 255, 255, 0.02)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: '24px',
    padding: '2rem',
    marginBottom: '1.5rem',
};

const FIELD_LABEL: React.CSSProperties = { fontSize: '0.75rem', opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.5px' };
const FIELD_VALUE: React.CSSProperties = { fontSize: '1rem', marginBottom: '1rem' };

export default function MinhaContaPage() {
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [requestStatus, setRequestStatus] = useState<string | null>(null);
    const [requesting, setRequesting] = useState(false);

    useEffect(() => {
        api.get('privacy/my-data')
            .then(res => setData(res.data))
            .catch(() => setError('Não foi possível carregar seus dados.'))
            .finally(() => setLoading(false));
    }, []);

    const handleDownload = () => {
        if (!data) return;
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'meus-dados-portalcursos.json';
        a.click();
        URL.revokeObjectURL(url);
    };

    const handleDeletionRequest = async () => {
        if (!confirm('Confirma a solicitação de eliminação dos seus dados? A administração irá analisar e executar, observados os prazos legais de guarda.')) return;
        setRequesting(true);
        setRequestStatus(null);
        try {
            const res = await api.post('privacy/data-deletion-request');
            setRequestStatus(res.data?.message || 'Solicitação registrada.');
        } catch (err: any) {
            setRequestStatus(err.response?.data?.message || 'Não foi possível registrar a solicitação.');
        } finally {
            setRequesting(false);
        }
    };

    if (loading) return <div className="fade-in">Carregando seus dados...</div>;
    if (error) return <div className="fade-in" style={{ color: '#e74c3c' }}>{error}</div>;

    const account = data?.account || {};
    const academic = data?.academic;

    return (
        <div className="fade-in">
            <header style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.8rem', color: 'var(--primary-color)' }}>Meus Dados</h2>
                <p style={{ color: 'var(--text-secondary)' }}>
                    Acesso aos seus dados pessoais conforme LGPD (art. 18) e GDPR (art. 15). Consulte também a{' '}
                    <Link href="/privacidade" target="_blank" style={{ color: 'var(--accent-color)' }}>Política de Privacidade</Link>.
                </p>
            </header>

            <section style={GLASS_SECTION}>
                <h3 style={{ marginBottom: '1.5rem' }}>Conta</h3>
                <div style={FIELD_LABEL}>Usuário</div>
                <div style={FIELD_VALUE}>{account.username}</div>
                <div style={FIELD_LABEL}>E-mail</div>
                <div style={FIELD_VALUE}>{account.email}</div>
                <div style={FIELD_LABEL}>Perfis de acesso</div>
                <div style={FIELD_VALUE}>{(account.roles || []).join(', ')}</div>
                <div style={FIELD_LABEL}>Consentimento à Política de Privacidade</div>
                <div style={FIELD_VALUE}>
                    {account.privacyConsentAccepted
                        ? `Aceito (versão ${account.privacyConsentVersion || '-'} em ${account.privacyConsentAt ? new Date(account.privacyConsentAt).toLocaleString('pt-BR') : '-'})`
                        : 'Não registrado (conta criada antes da captura de consentimento)'}
                </div>
            </section>

            {academic && (
                <section style={GLASS_SECTION}>
                    <h3 style={{ marginBottom: '1.5rem' }}>Dados Acadêmicos</h3>
                    <div style={FIELD_LABEL}>Nome completo</div>
                    <div style={FIELD_VALUE}>{academic.fullName}</div>
                    <div style={FIELD_LABEL}>Matrícula</div>
                    <div style={FIELD_VALUE}>{academic.registrationNumber}</div>
                    <div style={FIELD_LABEL}>CPF</div>
                    <div style={FIELD_VALUE}>{academic.cpf}</div>
                    <div style={FIELD_LABEL}>Telefone</div>
                    <div style={FIELD_VALUE}>{academic.phone || '-'}</div>
                    <div style={FIELD_LABEL}>Endereço</div>
                    <div style={FIELD_VALUE}>{academic.address || '-'}</div>
                    <div style={FIELD_LABEL}>Curso</div>
                    <div style={FIELD_VALUE}>{academic.course || '-'}</div>
                    <div style={FIELD_LABEL}>Situação de matrícula</div>
                    <div style={FIELD_VALUE}>{academic.enrollmentStatus}</div>
                </section>
            )}

            <section style={GLASS_SECTION}>
                <h3 style={{ marginBottom: '1rem' }}>Seus direitos</h3>
                <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem', fontSize: '0.9rem' }}>
                    Você pode baixar uma cópia dos seus dados (portabilidade) ou solicitar a eliminação deles.
                    A eliminação passa por análise da administração antes de ser executada, pois registros
                    acadêmicos e financeiros podem estar sujeitos a prazo legal de guarda.
                </p>
                <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                    <button className="btn-primary" style={{ padding: '10px 20px' }} onClick={handleDownload}>
                        ⬇️ Baixar meus dados
                    </button>
                    <button
                        disabled={requesting}
                        onClick={handleDeletionRequest}
                        style={{
                            padding: '10px 20px',
                            backgroundColor: 'rgba(255, 60, 60, 0.15)',
                            color: '#ff6b6b',
                            border: '1px solid rgba(255, 107, 107, 0.5)',
                            borderRadius: '8px',
                            cursor: requesting ? 'default' : 'pointer',
                            fontWeight: 600,
                            opacity: requesting ? 0.7 : 1,
                        }}
                    >
                        🗑️ Solicitar eliminação dos meus dados
                    </button>
                </div>
                {requestStatus && (
                    <p style={{ marginTop: '1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{requestStatus}</p>
                )}
            </section>
        </div>
    );
}
