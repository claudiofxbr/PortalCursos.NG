'use client';

import { V_BUILD_ID } from '../../services/api';
import Link from 'next/link';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';

export default function SignInPage() {
  const router = useRouter();
  const { loginSuccess } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [retryStatus, setRetryStatus] = useState<string | null>(null);
  const [retryProgress, setRetryProgress] = useState(0);

  // Monitor de Cold Start Retentativas (V15 OMEGA)
  useEffect(() => {
    const handleRetry = (e: any) => {
      const { attempt, maxRetries } = e.detail;
      const progress = (attempt / maxRetries) * 100;
      setRetryProgress(progress);
      setRetryStatus(`🚀 Despertando Nuvem... ${Math.round(progress)}%`);
    };

    window.addEventListener('COLD_START_RETRY' as any, handleRetry);
    return () => window.removeEventListener('COLD_START_RETRY' as any, handleRetry);
  }, []);

  // --- MECANISMO DE CONVERGÊNCIA V11.7 ---
  useEffect(() => {
    // A sincronização principal agora é feita pelo VersionGuard no RootLayout.
    // Aqui apenas limpamos erros locais.
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setError(null);
      }
    };

    if (error) {
      window.addEventListener('keydown', handleKeyDown);
      const timer = setTimeout(() => {
        setError(null);
      }, 10000);
      
      return () => {
        window.removeEventListener('keydown', handleKeyDown);
        clearTimeout(timer);
      };
    }
  }, [error]);

  const forceReset = () => {
    localStorage.clear();
    sessionStorage.clear();
    // Usa o motor de purga oficial (sem alert/reload que travam o navegador)
    const basePath = process.env.NEXT_PUBLIC_BASE_PATH || '';
    var target = window.location.origin + basePath + '/auth/signin?purge=' + Date.now();
    window.location.replace(target);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await api.post('auth/signin', { username, password });
      
      if (response.data) {
        // Tokens chegam via cookie HttpOnly — não são acessíveis ao JavaScript
        loginSuccess(response.data);
        
        setSuccess(true);
        setTimeout(() => {
          router.replace('/');
        }, 800);
      }
    } catch (err: any) {
      console.error("[AUTH DEBUG] Erro no Login:", err);
      
      let msg = err.message || "Não foi possível conectar ao sistema.";
      
      // Tratamento especial para erro de rede / Cold Start
      if (err.isNetworkError) {
          if (err.isColdStart) {
              msg = "🚀 O servidor está acordando... Por favor, aguarde 30 segundos e tente entrar novamente. (Ambiente Cloud)";
          } else {
              msg = "🔌 Erro de Conexão Local: O Backend Spring Boot não parece estar rodando na porta 8080.";
          }
      } else if (err.response) {
        const status = err.response.status;
        const serverMsg = err.response.data?.message || err.response.data?.error;
        
        if (status === 401) {
          msg = "Usuário ou senha incorretos. Tente novamente.";
        } else if (status === 403) {
          msg = "Acesso Negado. Você não tem permissão para entrar.";
        } else if (status === 404) {
          msg = "Erro 404: Endpoint da API não encontrado. O sistema está tentando se auto-corrigir. Por favor, utilize 'Limpar Cache' se persistir.";
        } else if (status === 500) {
          msg = "Erro interno no servidor. Estamos trabalhando para corrigir.";
        } else if (serverMsg) {
          msg = serverMsg;
        }
      } 
      
      setError(msg);
      setRetryProgress(0);
      setRetryStatus(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      height: '100vh',
      width: '100vw',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #0d123d 0%, #1a237e 40%, #c5a059 100%)',
      position: 'fixed',
      top: 0,
      left: 0,
      overflow: 'hidden'
    }} className="fade-in">
      
      <div style={{ position: 'absolute', width: '300px', height: '300px', background: 'rgba(197, 160, 89, 0.1)', borderRadius: '50%', top: '-50px', right: '-50px', filter: 'blur(50px)' }}></div>
      <div style={{ position: 'absolute', width: '400px', height: '400px', background: 'rgba(63, 81, 181, 0.1)', borderRadius: '50%', bottom: '-100px', left: '-100px', filter: 'blur(70px)' }}></div>

      <div className="glass-panel" style={{
        width: '420px',
        padding: '3.5rem 3rem',
        textAlign: 'center',
        border: '1px solid rgba(255, 255, 255, 0.15)',
        boxShadow: '0 20px 50px rgba(0, 0, 0, 0.3)',
        zIndex: 10
      }}>
        <div style={{ marginBottom: '2.5rem' }}>
          <h1 style={{ 
            color: 'var(--white)', 
            fontSize: '2.2rem', 
            letterSpacing: '-1px',
            marginBottom: '0.4rem',
            background: 'linear-gradient(to bottom, #ffffff, #c5a059)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent'
          }}>
            PortalCursos.NG
          </h1>
          <p style={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: '0.95rem' }}>Evoluindo a gestão acadêmica</p>
        </div>

        {success ? (
          <div className="fade-in" style={{ padding: '2rem 0' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>✨</div>
            <h3 style={{ color: '#fff' }}>Acesso Autorizado</h3>
            <p style={{ color: 'rgba(255, 255, 255, 0.7)' }}>Carregando sua dashboard...</p>
          </div>
        ) : (
          <>
            {error && (
              <div className="fade-in" style={{ 
                color: '#ff8a8a', 
                backgroundColor: 'rgba(197, 48, 48, 0.15)', 
                padding: '12px', 
                borderRadius: '8px', 
                marginBottom: '1.5rem', 
                fontSize: '0.88rem',
                border: '1px solid rgba(197, 48, 48, 0.3)'
              }}>
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.4rem', textAlign: 'left' }}>
              <div className="hover-lift">
                <label style={{ fontSize: '0.75rem', fontWeight: 700, color: '#c5a059', letterSpacing: '1px', textTransform: 'uppercase' }}>Usuário</label>
                <input 
                  type="text" 
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin" 
                  style={{
                    width: '100%',
                    padding: '14px',
                    marginTop: '6px',
                    borderRadius: '10px',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                    color: '#fff',
                    outline: 'none',
                    transition: 'border 0.3s'
                  }} 
                />
              </div>

              <div className="hover-lift">
                <label style={{ fontSize: '0.75rem', fontWeight: 700, color: '#c5a059', letterSpacing: '1px', textTransform: 'uppercase' }}>Senha</label>
                <input 
                  type="password" 
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••" 
                  style={{
                    width: '100%',
                    padding: '14px',
                    marginTop: '6px',
                    borderRadius: '10px',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                    color: '#fff',
                    outline: 'none'
                  }} 
                />
              </div>

              <button 
                type="submit"
                disabled={loading}
                className="btn-primary hover-lift" 
                style={{ 
                  padding: '16px', 
                  marginTop: '1rem', 
                  fontSize: '1rem', 
                  fontWeight: 600,
                  backgroundColor: loading ? 'rgba(255,255,255,0.1)' : '#c5a059',
                  color: loading ? 'rgba(255,255,255,0.3)' : '#0d123d',
                  boxShadow: loading ? 'none' : '0 10px 20px rgba(197, 160, 89, 0.2)',
                  position: 'relative',
                  overflow: 'hidden'
                }}
              >
                {loading && retryProgress > 0 && (
                  <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    height: '4px',
                    width: `${retryProgress}%`,
                    backgroundColor: '#fff',
                    transition: 'width 0.5s ease-out'
                  }} />
                )}
                <span style={{ position: 'relative', zIndex: 1 }}>
                    {loading 
                    ? (retryStatus || "Validando Credenciais...") 
                    : "Entrar no Sistema (VITA-V17.1)"}
                </span>
              </button>

              <div style={{ textAlign: 'center', marginTop: '0.5rem', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <span style={{ fontSize: '0.65rem', color: '#c5a059', opacity: 0.8, letterSpacing: '1px', fontWeight: 'bold' }}>
                  [ V17.1 - PROTOCOLO VITA ]
                </span>
                <span style={{ fontSize: '0.6rem', color: 'rgba(255,255,255,0.4)', padding: '2px 6px', borderRadius: '4px', border: '1px solid rgba(255,255,255,0.1)' }}>
                  AUTO-CICATRIZAÇÃO ATIVA | {V_BUILD_ID}
                </span>
                <Link href="/health" style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.4)', textDecoration: 'underline' }}>
                  Rede & Diagnóstico de Conexão
                </Link>

                <button 
                  onClick={forceReset}
                  type="button"
                  style={{ 
                    background: 'none', 
                    border: '1px solid rgba(197, 160, 89, 0.3)', 
                    color: '#c5a059', 
                    fontSize: '0.65rem', 
                    padding: '4px 8px', 
                    borderRadius: '4px',
                    marginTop: '10px',
                    cursor: 'pointer'
                  }}
                >
                  ⚡ LIMPAR CACHE E FORÇAR ATUALIZAÇÃO
                </button>
              </div>
            </form>

            <div style={{ marginTop: '2.5rem', fontSize: '0.9rem' }}>
              <p style={{ color: 'rgba(255, 255, 255, 0.5)' }}>Novo por aqui? <Link href="/auth/signup" style={{ color: '#c5a059', fontWeight: 700, textDecoration: 'none', borderBottom: '1px solid rgba(197, 160, 89, 0.3)' }}>Criar Conta</Link></p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
