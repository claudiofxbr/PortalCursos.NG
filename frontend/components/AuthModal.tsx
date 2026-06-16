'use client';

import React, { useState } from 'react';
import { useAuth } from '../app/context/AuthContext';
import api from '../app/services/api';

export default function AuthModal() {
    const { isAuthModalOpen, lastUser, loginSuccess } = useAuth();
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    if (!isAuthModalOpen) return null;

    const handleReLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            const response = await api.post('auth/signin', {
                username: lastUser || 'admin',
                password: password
            });

            // Tokens chegam via cookie HttpOnly — não são acessíveis ao JavaScript.
            // O backend seta Set-Cookie na resposta; withCredentials no api.ts envia automaticamente.
            loginSuccess(response.data);
            setPassword('');
        } catch (err: any) {
            console.error("[AUTH MODAL] Erro no re-login:", err);
            // Distingue falha de rede/DNS (sem response) de credenciais erradas
            // (401/403 do backend), para o usuário saber se deve checar a
            // senha ou a própria conexão.
            if (!err?.response) {
                setError("Falha de conexão. Verifique sua internet e tente novamente.");
            } else {
                setError("Senha incorreta ou erro no servidor.");
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(0, 0, 0, 0.7)',
            backdropFilter: 'blur(5px)',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: 9999,
        }}>
            <div style={{
                backgroundColor: 'var(--card-bg, #1e1e1e)',
                padding: '2.5rem',
                borderRadius: '1rem',
                width: '400px',
                border: '1px solid var(--border-color, rgba(255,255,255,0.1))',
                boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)'
            }}>
                <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
                    <h2 style={{ color: 'var(--secondary-color)', fontSize: '1.5rem', marginBottom: '0.5rem' }}>Acesso Expirado</h2>
                    <p style={{ opacity: 0.7, fontSize: '0.9rem' }}>Olá, {lastUser}. <br/> Por segurança, digite sua senha para continuar de onde parou.</p>
                </div>

                <form onSubmit={handleReLogin}>
                    <div style={{ marginBottom: '1.5rem' }}>
                        <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.8rem', opacity: 0.6 }}>Usuário</label>
                        <input 
                            type="text" 
                            value={lastUser || 'admin'} 
                            disabled 
                            style={{
                                width: '100%',
                                padding: '0.8rem',
                                borderRadius: '0.5rem',
                                backgroundColor: 'rgba(0,0,0,0.2)',
                                border: '1px solid rgba(255,255,255,0.05)',
                                color: 'rgba(255,255,255,0.5)',
                            }}
                        />
                    </div>

                    <div style={{ marginBottom: '1.5rem' }}>
                        <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.8rem', opacity: 0.6 }}>Sua Senha</label>
                        <input 
                            type="password" 
                            autoFocus
                            placeholder="Digite sua senha física..."
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            style={{
                                width: '100%',
                                padding: '0.8rem',
                                borderRadius: '0.5rem',
                                backgroundColor: 'rgba(255,255,255,0.05)',
                                border: '1px solid rgba(255,255,255,0.1)',
                                color: 'var(--white)',
                                outline: 'none'
                            }}
                            required
                        />
                    </div>

                    {error && (
                        <p style={{ color: 'var(--danger-color, #ff4d4d)', fontSize: '0.8rem', marginBottom: '1rem', textAlign: 'center' }}>{error}</p>
                    )}

                    <button 
                        type="submit" 
                        disabled={loading}
                        style={{
                            width: '100%',
                            padding: '1rem',
                            borderRadius: '0.5rem',
                            backgroundColor: 'var(--primary-color, #007bff)',
                            color: 'var(--white)',
                            fontWeight: 'bold',
                            border: 'none',
                            cursor: loading ? 'not-allowed' : 'pointer',
                            opacity: loading ? 0.7 : 1,
                            transition: 'all 0.2s',
                            boxShadow: '0 4px 12px rgba(0, 123, 255, 0.3)'
                        }}
                    >
                        {loading ? 'Validando...' : 'Liberar Acesso'}
                    </button>
                </form>
            </div>
        </div>
    );
}
