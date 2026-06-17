'use client';

import Link from 'next/link';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import api from '../../services/api';

export default function SignUpPage() {
  const router = useRouter();
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    role: ['aluno']
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Limpa o erro após 30 segundos OU ao apertar a tecla Esc
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setError(null);
      }
    };

    if (error) {
      window.addEventListener('keydown', handleKeyDown);
      const timer = setTimeout(() => {
        setError(null);
      }, 30000);
      
      return () => {
        window.removeEventListener('keydown', handleKeyDown);
        clearTimeout(timer);
      };
    }
  }, [error]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    // Validação cliente — espelha as constraints do SignupRequest
    if (/\s/.test(formData.username)) {
      setError("O nome de usuário não pode conter espaços.");
      setLoading(false);
      return;
    }
    if (formData.username.length < 3 || formData.username.length > 20) {
      setError("O nome de usuário deve ter entre 3 e 20 caracteres.");
      setLoading(false);
      return;
    }
    if (formData.password.length < 6 || formData.password.length > 40) {
      setError("A senha deve ter entre 6 e 40 caracteres.");
      setLoading(false);
      return;
    }

    try {
      await api.post('auth/signup', formData);
      alert("✅ Conta criada com sucesso! Redirecionando para o login...");
      router.push('/auth/signin');
    } catch (err: any) {
      console.error("Erro no cadastro:", err);
      const msg = err.response?.data?.message || "Não foi possível criar sua conta. Verifique os dados.";
      setError(msg);
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
      background: 'linear-gradient(135deg, var(--sidebar-bg) 0%, var(--primary-color) 100%)',
      position: 'fixed',
      top: 0,
      left: 0,
      zIndex: 1000
    }}>
      <div className="glass-panel fade-in" style={{
        width: '400px',
        padding: '3rem',
        textAlign: 'center',
        backgroundColor: 'rgba(255, 255, 255, 0.9)'
      }}>
        <h1 style={{ color: 'var(--primary-color)', marginBottom: '0.5rem' }}>PortalCursos</h1>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem' }}>Crie sua conta na plataforma.</p>

        {error && (
          <div style={{ color: '#c53030', backgroundColor: '#fff5f5', padding: '10px', borderRadius: '6px', marginBottom: '1rem', fontSize: '0.85rem' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', textAlign: 'left' }}>
          <div>
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>USUÁRIO</label>
            <input 
              type="text"
              name="username"
              required
              minLength={3}
              maxLength={20}
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value.replace(/\s/g, '') })}
              placeholder="nome_usuario (sem espaços)"
              style={{ width: '100%', padding: '10px', marginTop: '5px', borderRadius: '8px', border: '1px solid #ddd' }}
            />
          </div>

          <div>
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>E-MAIL</label>
            <input 
              type="email" 
              name="email"
              required
              value={formData.email}
              onChange={handleChange}
              placeholder="seu@email.com" 
              style={{ width: '100%', padding: '10px', marginTop: '5px', borderRadius: '8px', border: '1px solid #ddd' }} 
            />
          </div>

          <div>
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>SENHA</label>
            <input 
              type="password" 
              name="password"
              required
              value={formData.password}
              onChange={handleChange}
              placeholder="••••••••" 
              style={{ width: '100%', padding: '10px', marginTop: '5px', borderRadius: '8px', border: '1px solid #ddd' }} 
            />
          </div>

          <button 
            type="submit" 
            disabled={loading}
            className="btn-primary" 
            style={{ padding: '15px', marginTop: '1rem', opacity: loading ? 0.7 : 1 }}
          >
            {loading ? "Criando conta..." : "Criar Minha Conta"}
          </button>
        </form>

        <div style={{ marginTop: '2rem', fontSize: '0.9rem' }}>
          <p style={{ color: 'var(--text-secondary)' }}>Já tem acesso? <Link href="/auth/signin" style={{ color: 'var(--accent-color)', fontWeight: 600 }}>Fazer Login</Link></p>
        </div>
      </div>
    </div>
  );
}
