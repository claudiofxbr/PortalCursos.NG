'use client';

import { useEffect, useState } from 'react';
import { V_BUILD_ID } from '../app/services/api';

/**
 * Protocolo V20.0-ULTRA: Infraestrutura Ativa & Resiliente
 * Fornece feedback visual premium (Glassmorphism) durante boot de serviços cloud.
 */
export default function InfrastructureIndicator() {
  const [status, setStatus] = useState<any>(null);

  useEffect(() => {
    // Sincronização de BUILD_ID (Silenciosa)
    if (typeof window !== 'undefined') {
      localStorage.setItem('PC_ULTRA_V', V_BUILD_ID);
    }

    // Listener para o Motor ULTRA
    const handleUltraBooting = (e: any) => {
      setStatus(e.detail);
    };

    const handleHealthSuccess = () => {
        setStatus(null); // Esconde a barra ao normalizar
    };

    window.addEventListener('ULTRA_BOOTING' as any, handleUltraBooting);
    window.addEventListener('HEALTH_SUCCESS' as any, handleHealthSuccess);
    
    return () => {
      window.removeEventListener('ULTRA_BOOTING' as any, handleUltraBooting);
      window.removeEventListener('HEALTH_SUCCESS' as any, handleHealthSuccess);
    };
  }, []);

  if (!status || !status.isBooting) return null;

  return (
    <div style={CONTAINER_STYLE}>
      <div style={GLASS_STYLE}>
        <div style={DOT_STYLE} />
        <span style={TEXT_STYLE}>
          Infraestrutura em Nuvem: <strong>Despertando</strong> (Tentativa {status.attempt}/10)
        </span>
        <div style={PROGRESS_CONTAINER}>
            <div style={{ ...PROGRESS_BAR, width: `${(status.attempt / 10) * 100}%` }} />
        </div>
      </div>
    </div>
  );
}

const CONTAINER_STYLE: React.CSSProperties = {
  position: 'fixed',
  top: '20px',
  left: '50%',
  transform: 'translateX(-50%)',
  zIndex: 9999,
  width: 'auto',
  minWidth: '320px',
};

const GLASS_STYLE: React.CSSProperties = {
  background: 'rgba(15, 23, 42, 0.8)',
  backdropFilter: 'blur(12px)',
  border: '1px solid rgba(255, 255, 255, 0.1)',
  borderRadius: '16px',
  padding: '12px 20px',
  display: 'flex',
  flexDirection: 'column',
  gap: '8px',
  boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.3)',
};

const TEXT_STYLE: React.CSSProperties = {
  color: '#f8fafc',
  fontSize: '0.85rem',
  textAlign: 'center',
};

const DOT_STYLE: React.CSSProperties = {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    backgroundColor: '#38bdf8',
    boxShadow: '0 0 10px #38bdf8',
    position: 'absolute',
    top: '12px',
    right: '12px',
    animation: 'pulse 1.5s infinite',
};

const PROGRESS_CONTAINER: React.CSSProperties = {
    width: '100%',
    height: '4px',
    background: 'rgba(255,255,255,0.1)',
    borderRadius: '2px',
    overflow: 'hidden',
};

const PROGRESS_BAR: React.CSSProperties = {
    height: '100%',
    background: 'linear-gradient(90deg, #0ea5e9, #38bdf8)',
    transition: 'width 0.5s ease-out',
};
