'use client';

import React, { useEffect, useState } from 'react';
import { V_BUILD_ID } from '../app/services/api';

/**
 * PROTOCOLO OMEGA-SUPREME: ConnectivityGuard
 * Componente de elite que protege a experiência do usuário durante o Cold Start da Cloud.
 * Fornece feedback visual premium e impede interações com um sistema ainda em boot.
 */
export default function ConnectivityGuard({ children }: { children: React.ReactNode }) {
    const [status, setStatus] = useState<any>(null);
    const [isMounted, setIsMounted] = useState(false);
    const [timedOut, setTimedOut] = useState(false); // Timeout de segurança

    useEffect(() => {
        setIsMounted(true);

        const updateStatus = (e: any) => {
            const data = e.detail;
            setStatus(data);
        };

        window.addEventListener('OMEGA_BOOTING' as any, updateStatus);
        window.addEventListener('OMEGA_HEALTH' as any, updateStatus);

        // Timeout de segurança: se após 8s o backend não responder, libera o app
        // Evita travamento eterno quando o backend está lento ou offline
        const safetyTimer = setTimeout(() => {
            setTimedOut(true);
            console.warn('[ConnectivityGuard] Timeout de segurança ativado. Liberando app.');
        }, 8000);

        return () => {
            window.removeEventListener('OMEGA_BOOTING' as any, updateStatus);
            window.removeEventListener('OMEGA_HEALTH' as any, updateStatus);
            clearTimeout(safetyTimer);
        };
    }, []);

    if (!isMounted) return null;

    // Se o sistema estiver saudável e NÃO estiver no boot inicial, renderiza o app normalmente
    const isBooting = status?.isBooting || (!status && typeof window !== 'undefined' && (window as any).PC_OMEGA_STATUS?.isBooting);

    // Libera o app se: backend respondeu com sucesso OU timeout de segurança ativado
    if (timedOut || (!isBooting && status?.isHealthy)) {
        return <>{children}</>;
    }

    // Se estiver em boot ou sem status inicial (primeira carga)
    return (
        <div style={OVERLAY_STYLE}>
            <div style={CONTENT_BOX_STYLE}>
                <div className="omega-pulse-container">
                    <div className="omega-ring" />
                    <div className="omega-ring" />
                    <div className="omega-ring" />
                    <div className="omega-logo">Ω</div>
                </div>

                <h2 style={TITLE_STYLE}>OMEGA CLOUD ENGINE</h2>
                <p style={SUBTITLE_STYLE}>
                    {status?.isBooting 
                        ? (status.attempt === 1 
                            ? "Iniciando Motores Cloud (Aguardando Neon...)" 
                            : `Despertando Cloud (Tentativa ${status.attempt}/15)`)
                        : "Sincronizando Ecossistema OMEGA..."}
                </p>

                <div style={LOADER_TRACK}>
                    <div style={{
                        ...LOADER_FILL,
                        width: status?.attempt === 1 ? '10%' : `${Math.min(((status?.attempt || 0) / 15) * 100, 100)}%`,
                        transition: status?.attempt === 1 ? 'width 10s linear' : 'width 0.8s cubic-bezier(0.4, 0, 0.2, 1)'
                    }} />
                </div>

                <div style={DIAGNOSTIC_BOX}>
                    <div style={DIAGNOSTIC_ROW}>
                        <span style={LABEL}>PROTOCOL:</span>
                        <span style={VALUE}>OMEGA-SUPREME V30.3</span>
                    </div>
                    <div style={DIAGNOSTIC_ROW}>
                        <span style={LABEL}>STATUS:</span>
                        <span style={{...VALUE, color: status?.attempt === 1 ? '#60a5fa' : '#fbbf24'}}>
                            {status?.attempt === 1 ? 'NEON_CLOUD_WAKING' : 'INITIALIZING_CLUSTER'}
                        </span>
                    </div>
                    <div style={DIAGNOSTIC_ROW}>
                        <span style={LABEL}>CLOUD_TYPE:</span>
                        <span style={VALUE}>HYBRID_HOSTINGER_NEON</span>
                    </div>
                </div>
            </div>

            <style>{`
                .omega-pulse-container {
                    position: relative;
                    width: 100px;
                    height: 100px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-bottom: 2rem;
                }
                .omega-logo {
                    font-size: 3rem;
                    color: #c5a059;
                    font-weight: 300;
                    z-index: 2;
                }
                .omega-ring {
                    position: absolute;
                    width: 100%;
                    height: 100%;
                    border: 2px solid rgba(197, 160, 89, 0.3);
                    border-radius: 50%;
                    animation: omega-ripple 3s infinite cubic-bezier(0.36, 0.11, 0.89, 0.32);
                }
                .omega-ring:nth-child(2) { animation-delay: 1s; }
                .omega-ring:nth-child(3) { animation-delay: 2s; }

                @keyframes omega-ripple {
                    0% { transform: scale(0.5); opacity: 0; }
                    50% { opacity: 0.5; }
                    100% { transform: scale(2.5); opacity: 0; }
                }
            `}</style>
        </div>
    );
}

const OVERLAY_STYLE: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#020617',
    zIndex: 100000,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: "'Inter Tight', sans-serif",
    color: '#fff',
};

const CONTENT_BOX_STYLE: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    width: '100%',
    maxWidth: '400px',
    padding: '2rem',
    textAlign: 'center',
};

const TITLE_STYLE: React.CSSProperties = {
    fontSize: '1.2rem',
    letterSpacing: '0.2em',
    fontWeight: 700,
    margin: '0 0 0.5rem 0',
    color: '#f8fafc',
};

const SUBTITLE_STYLE: React.CSSProperties = {
    fontSize: '0.9rem',
    color: 'rgba(255,255,255,0.5)',
    marginBottom: '2rem',
};

const LOADER_TRACK: React.CSSProperties = {
    width: '100%',
    height: '4px',
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: '2px',
    overflow: 'hidden',
    marginBottom: '2.5rem',
};

const LOADER_FILL: React.CSSProperties = {
    height: '100%',
    backgroundColor: '#c5a059',
    transition: 'width 0.8s cubic-bezier(0.4, 0, 0.2, 1)',
    boxShadow: '0 0 15px rgba(197, 160, 89, 0.5)',
};

const DIAGNOSTIC_BOX: React.CSSProperties = {
    width: '100%',
    backgroundColor: 'rgba(255,255,255,0.02)',
    border: '1px solid rgba(255,255,255,0.05)',
    borderRadius: '8px',
    padding: '1rem',
};

const DIAGNOSTIC_ROW: React.CSSProperties = {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '0.25rem 0',
    fontSize: '0.7rem',
    fontFamily: 'monospace',
};

const LABEL: React.CSSProperties = {
    color: 'rgba(255,255,255,0.3)',
};

const VALUE: React.CSSProperties = {
    color: 'rgba(255,255,255,0.7)',
};
