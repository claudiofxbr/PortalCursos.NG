'use client';

import { useEffect, useState } from 'react';
import { V_BUILD_ID } from '../app/services/api';

/**
 * Protocolo V30.0-SUPREME: Dashboard de Saúde de Infraestrutura
 * Monitora o estado síncrono da nuvem e fornece feedback não-intrusivo.
 */
export default function InfrastructureIndicator() {
  const [status, setStatus] = useState<any>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    
    const handleSupremeBooting = (e: any) => {
      setStatus(e.detail);
    };

    const handleSupremeHealth = (e: any) => {
        // Se estiver saudável, limpamos o status após um pequeno delay para suavidade
        if (e.detail.isHealthy && !e.detail.isBooting) {
            setTimeout(() => setStatus(null), 3000);
        } else {
            setStatus(e.detail);
        }
    };

    window.addEventListener('SUPREME_BOOTING' as any, handleSupremeBooting);
    window.addEventListener('SUPREME_HEALTH' as any, handleSupremeHealth);
    
    return () => {
      window.removeEventListener('SUPREME_BOOTING' as any, handleSupremeBooting);
      window.removeEventListener('SUPREME_HEALTH' as any, handleSupremeHealth);
    };
  }, []);

  if (!mounted || !status) return null;
  if (!status.isBooting && status.isHealthy) return null;

  return (
    <div style={CONTAINER_STYLE}>
      <div style={GLASS_STYLE}>
        <div style={{...DOT_STYLE, backgroundColor: status.isBooting ? '#fbbf24' : '#10b981'}} />
        <div style={CONTENT_STYLE}>
            <span style={TITLE_STYLE}>SUPREME INFRASTRUCTURE GUARD</span>
            <span style={TEXT_STYLE}>
              {status.isBooting 
                ? `Despertando Cloud (Tentativa ${status.attempt}/12)...` 
                : 'Sincronizado com Neon PostgreSQL'}
            </span>
            <div style={PROGRESS_CONTAINER}>
                <div style={{ 
                    ...PROGRESS_BAR, 
                    width: `${(status.attempt / 12) * 100}%`,
                    background: status.isBooting ? 'linear-gradient(90deg, #f59e0b, #fbbf24)' : '#10b981'
                }} />
            </div>
        </div>
        <span style={VERSION_STYLE}>{V_BUILD_ID}</span>
      </div>
    </div>
  );
}

const CONTAINER_STYLE: React.CSSProperties = {
  position: 'fixed',
  bottom: '24px',
  right: '24px',
  zIndex: 9999,
  width: '300px',
};

const GLASS_STYLE: React.CSSProperties = {
  background: 'rgba(2, 6, 23, 0.85)',
  backdropFilter: 'blur(16px)',
  border: '1px solid rgba(255, 255, 255, 0.08)',
  borderRadius: '12px',
  padding: '16px',
  display: 'flex',
  flexDirection: 'row',
  alignItems: 'flex-start',
  gap: '12px',
  boxShadow: '0 20px 50px -12px rgba(0, 0, 0, 0.5)',
  position: 'relative',
};

const CONTENT_STYLE: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
    flex: 1,
};

const TITLE_STYLE: React.CSSProperties = {
    color: '#94a3b8',
    fontSize: '0.65rem',
    fontWeight: '700',
    letterSpacing: '0.05em',
};

const TEXT_STYLE: React.CSSProperties = {
  color: '#f8fafc',
  fontSize: '0.8rem',
  fontWeight: '500',
};

const VERSION_STYLE: React.CSSProperties = {
    position: 'absolute',
    top: '8px',
    right: '12px',
    color: 'rgba(255,255,255,0.2)',
    fontSize: '0.6rem',
    fontFamily: 'monospace',
};

const DOT_STYLE: React.CSSProperties = {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    marginTop: '12px',
    boxShadow: '0 0 12px currentColor',
};

const PROGRESS_CONTAINER: React.CSSProperties = {
    width: '100%',
    height: '3px',
    background: 'rgba(255,255,255,0.05)',
    borderRadius: '2px',
    marginTop: '8px',
    overflow: 'hidden',
};

const PROGRESS_BAR: React.CSSProperties = {
    height: '100%',
    transition: 'width 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
};
