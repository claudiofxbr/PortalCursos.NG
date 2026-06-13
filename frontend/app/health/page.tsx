'use client';

import { useState, useEffect } from 'react';
import { API_BASE_URL, V_BUILD_ID, checkServerHealth } from '@/app/services/api';

/**
 * PortalCursos Diagnostics Center V18.1 - SIGMA
 * Central de Monitoramento e Estabilização de Rede.
 */
export default function HealthPage() {
    const [status, setStatus] = useState<'loading' | 'online' | 'offline'>('loading');
    const [details, setDetails] = useState<any>(null);
    const [environment, setEnvironment] = useState<any>(null);
    const [isColdStart, setIsColdStart] = useState(false);
    const [retryCount, setRetryCount] = useState(0);

    useEffect(() => {
        // Escuta eventos de Cold Start do nosso motor API
        const handleColdStart = (e: any) => {
            setIsColdStart(true);
            setRetryCount(e.detail?.attempt || 0);
        };

        window.addEventListener('COLD_START_RETRY', handleColdStart);

        const checkHealth = async (isManual = false) => {
            const rawApiUrl = process.env.NEXT_PUBLIC_API_URL || '';
            
            try {
                if (isManual) setStatus('loading');
                const start = Date.now();
                const data = await checkServerHealth();
                const end = Date.now();
                
                setStatus('online');
                setIsColdStart(false);
                setDetails({
                    latency: `${end - start}ms`,
                    backend: data?.message || data?.status || 'Ativo',
                    protocol: 'SIGMA-V18.1'
                });
            } catch (err: any) {
                console.error("Health check failed:", err);
                setStatus('offline');
                setDetails({
                    error: err.message || 'Servidor Indisponível',
                    endpoint: API_BASE_URL,
                    suggestion: "O servidor pode estar hibernando (Cold Start). Aguarde o despertar automático."
                });
            }
            
            setEnvironment({
                hostname: typeof window !== 'undefined' ? window.location.hostname : 'local',
                apiActual: API_BASE_URL,
                apiConfigured: rawApiUrl,
                build: V_BUILD_ID,
                userAgent: typeof navigator !== 'undefined' ? navigator.userAgent.substring(0, 50) + "..." : 'Server'
            });
        };

        (window as any).RECHECK_HEALTH = () => checkHealth(true);
        checkHealth();

        return () => window.removeEventListener('COLD_START_RETRY', handleColdStart);
    }, []);

    const nuclearPurge = async () => {
        if (typeof window !== 'undefined') {
            const confirmPurge = window.confirm("A Purga Nuclear irá limpar todo o cache e cookies. Você precisará fazer login novamente. Prosseguir?");
            if (!confirmPurge) return;

            console.warn("[SIGMA-V18.1] Iniciando Purga Nuclear de Cache...");
            
            localStorage.clear();
            sessionStorage.clear();
            
            // Limpa Cookies
            document.cookie.split(";").forEach(function(c) { 
                document.cookie = c.replace(/^ +/, "").replace(/=.*/, "=;expires=" + new Date().toUTCString() + ";path=/"); 
            });

            // Limpa Cache API
            if (window.caches) {
                const names = await caches.keys();
                await Promise.all(names.map(name => caches.delete(name)));
            }

            console.info("[SIGMA-V18.1] Purga Concluída. Reiniciando...");
            window.location.replace((process.env.NEXT_PUBLIC_BASE_PATH || '') + '/');
        }
    };

    return (
        <div style={{ 
            padding: '2rem', 
            fontFamily: 'Inter, system-ui, sans-serif', 
            maxWidth: '900px', 
            margin: '0 auto',
            color: '#1e293b',
            lineHeight: '1.5'
        }}>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                <div>
                    <h1 style={{ margin: 0, color: '#2563eb', fontSize: '1.875rem' }}>Diagnóstico de Conectividade</h1>
                    <p style={{ margin: '0.25rem 0', color: '#64748b' }}>Status da Infraestrutura Cloud PortalCursos</p>
                </div>
                <div style={{ 
                    padding: '0.5rem 1rem', 
                    borderRadius: '9999px', 
                    backgroundColor: '#f1f5f9', 
                    fontSize: '0.75rem', 
                    fontFamily: 'monospace',
                    fontWeight: 600
                }}>
                    BUILD: {V_BUILD_ID}
                </div>
            </header>

            <div style={{ 
                padding: '1rem', 
                backgroundColor: '#f0f9ff', 
                border: '1px solid #bae6fd', 
                borderRadius: '8px', 
                marginBottom: '1.5rem',
                color: '#0369a1',
                fontSize: '0.875rem'
            }}>
                <strong>🛡️ Protocolo SIGMA-V18.1 Ativo:</strong> Motor de conectividade unificado. Prevenção de timeouts de UI e estabilização de Cold Start ativa.
            </div>

            {isColdStart && (
                <div style={{ 
                    padding: '1rem', 
                    backgroundColor: '#fffbeb', 
                    border: '1px solid #fef3c7', 
                    borderRadius: '8px', 
                    marginBottom: '1.5rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px'
                }}>
                    <div className="spinner" style={{ 
                        width: '20px', 
                        height: '20px', 
                        border: '3px solid #f59e0b', 
                        borderTopColor: 'transparent',
                        borderRadius: '50%',
                        animation: 'spin 1s linear infinite'
                    }} />
                    <span style={{ color: '#92400e', fontWeight: 500 }}>
                        Servidor Render acordando... (Tentativa {retryCount}/7). Por favor, aguarde o despertar da nuvem.
                    </span>
                    <style dangerouslySetInnerHTML={{ __html: `
                        @keyframes spin { to { transform: rotate(360deg); } }
                    `}} />
                </div>
            )}
            
            <div style={{ 
                display: 'grid', 
                gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', 
                gap: '1rem',
                marginBottom: '2rem'
            }}>
                <div style={{ 
                    padding: '1.5rem', 
                    borderRadius: '12px', 
                    backgroundColor: status === 'online' ? '#f0fdf4' : status === 'offline' ? '#fef2f2' : '#f8fafc',
                    border: `1px solid ${status === 'online' ? '#bbf7d0' : status === 'offline' ? '#fecaca' : '#e2e8f0'}`,
                }}>
                    <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1rem' }}>Status do Backend</h3>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <div style={{ 
                            width: '12px', 
                            height: '12px', 
                            borderRadius: '50%', 
                            backgroundColor: status === 'online' ? '#22c55e' : status === 'offline' ? '#ef4444' : '#94a3b8' 
                        }} />
                        <span style={{ fontWeight: 700, fontSize: '1.25rem' }}>
                            {status === 'loading' ? 'VERIFICANDO...' : status.toUpperCase()}
                        </span>
                    </div>
                    {details?.latency && <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.875rem', color: '#166534' }}>Latência: {details.latency}</p>}
                </div>

                <div style={{ 
                    padding: '1.5rem', 
                    borderRadius: '12px', 
                    backgroundColor: '#f8fafc',
                    border: '1px solid #e2e8f0',
                }}>
                    <h3 style={{ margin: '0 0 1rem 0', fontSize: '1rem' }}>Recuperação</h3>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button 
                            onClick={() => (window as any).RECHECK_HEALTH?.()}
                            style={{ flex: 1, padding: '0.75rem', backgroundColor: '#2563eb', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
                        >
                            🔄 Re-testar
                        </button>
                        <button 
                            onClick={nuclearPurge}
                            style={{ flex: 1, padding: '0.75rem', backgroundColor: '#ef4444', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
                        >
                            🧹 Purga Nuclear
                        </button>
                    </div>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '1.5rem' }}>
                <section>
                    <h3 style={{ fontSize: '1.125rem', marginBottom: '0.75rem' }}>Inspeção de Ambiente</h3>
                    <pre style={{ backgroundColor: '#1e293b', color: '#e2e8f0', padding: '1.25rem', borderRadius: '8px', overflow: 'auto', fontSize: '0.8125rem', margin: 0 }}>
                        {JSON.stringify(environment, null, 2)}
                    </pre>
                </section>
                
                <section>
                    <h3 style={{ fontSize: '1.125rem', marginBottom: '0.75rem' }}>Dados Técnicos (Backend)</h3>
                    <pre style={{ backgroundColor: '#f1f5f9', color: '#475569', padding: '1.25rem', borderRadius: '8px', overflow: 'auto', fontSize: '0.8125rem', border: '1px solid #e2e8f0', margin: 0 }}>
                        {JSON.stringify(details, null, 2)}
                    </pre>
                </section>
            </div>

            <section style={{ marginTop: '2.5rem', padding: '1.5rem', backgroundColor: '#f8fafc', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                <h3 style={{ margin: '0 0 0.75rem 0', color: '#1e293b' }}>Resumo SIGMA-V18.1</h3>
                <ul style={{ margin: 0, paddingLeft: '1.25rem', color: '#475569', fontSize: '0.9375rem' }}>
                    <li style={{ marginBottom: '0.5rem' }}><strong>Sem Redirecionamentos</strong>: O sistema não recarrega mais a tela sozinho, evitando o loop de erros.</li>
                    <li style={{ marginBottom: '0.5rem' }}><strong>Roteamento Fixo</strong>: O frontend ignora configurações externas e busca o Render diretamente.</li>
                    <li><strong>Unfreeze Automático</strong>: Caso a nuvem demore para acordar, o sistema libera a interface em até 3.5 segundos.</li>
                </ul>
            </section>

            <footer style={{ marginTop: '3rem', textAlign: 'center', fontSize: '0.75rem', color: '#94a3b8' }}>
                PortalCursos NG-02 &copy; 2026 | Protocolo de Estabilização SIGMA-V18.1
            </footer>
        </div>
    );
}
