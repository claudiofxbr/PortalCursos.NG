'use client';

import React, { useEffect, useState } from 'react';

interface ConnectivityStatus {
    isBooting?: boolean;
    attempt?: number;
}

/**
 * ConnectivityGuard: mostra um overlay de "reconectando" enquanto o
 * interceptor de retry do axios (api.ts) tenta se recuperar de uma falha
 * transitória do backend (ex.: janela curta de indisponibilidade durante
 * um deploy blue-green). A VPS Hostinger fica sempre ativa — não há "cold
 * start" de infraestrutura — então esse overlay só aparece diante de uma
 * falha real de rede, nunca no carregamento normal.
 */
export default function ConnectivityGuard({ children }: { children: React.ReactNode }) {
    const [status, setStatus] = useState<ConnectivityStatus | null>(null);
    const [isMounted, setIsMounted] = useState(false);
    const [timedOut, setTimedOut] = useState(false);

    useEffect(() => {
        setIsMounted(true);

        const updateStatus = (e: Event) => {
            setStatus((e as CustomEvent<ConnectivityStatus>).detail);
        };

        window.addEventListener('OMEGA_BOOTING', updateStatus);
        window.addEventListener('OMEGA_HEALTH', updateStatus);

        // Timeout de segurança: se após 8s o backend não responder, libera o
        // app mesmo assim, para não travar o usuário indefinidamente.
        const safetyTimer = setTimeout(() => {
            setTimedOut(true);
        }, 8000);

        return () => {
            window.removeEventListener('OMEGA_BOOTING', updateStatus);
            window.removeEventListener('OMEGA_HEALTH', updateStatus);
            clearTimeout(safetyTimer);
        };
    }, []);

    if (!isMounted) return null;

    // Mostra overlay APENAS quando há retries ativos (falha real confirmada)
    const isBooting = status?.isBooting === true;

    if (timedOut || !isBooting) {
        return <>{children}</>;
    }

    return (
        <div style={OVERLAY_STYLE}>
            <div style={CONTENT_BOX_STYLE}>
                <div className="reconnect-spinner" />
                <h2 style={TITLE_STYLE}>Reconectando ao servidor</h2>
                <p style={SUBTITLE_STYLE}>
                    Tentativa {status?.attempt || 1} de 3...
                </p>
            </div>

            <style>{`
                .reconnect-spinner {
                    width: 48px;
                    height: 48px;
                    border: 3px solid rgba(255, 255, 255, 0.15);
                    border-top-color: #c5a059;
                    border-radius: 50%;
                    margin-bottom: 1.5rem;
                    animation: reconnect-spin 0.8s linear infinite;
                }
                @keyframes reconnect-spin {
                    to { transform: rotate(360deg); }
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
    textAlign: 'center',
};

const TITLE_STYLE: React.CSSProperties = {
    fontSize: '1.1rem',
    fontWeight: 600,
    margin: '0 0 0.5rem 0',
    color: '#f8fafc',
};

const SUBTITLE_STYLE: React.CSSProperties = {
    fontSize: '0.85rem',
    color: 'rgba(255,255,255,0.5)',
};
