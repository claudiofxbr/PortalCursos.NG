'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '@/app/services/api';
import { useAuth } from '@/app/context/AuthContext';
import PhotoUpload3x4 from '@/components/PhotoUpload3x4';

interface Role {
    id: number;
    name: string;
}

interface User {
    id: number;
    username: string;
    email: string;
    roles: Role[];
    fotoUrl?: string;
}

const SECTION_STYLE = {
    backgroundColor: 'rgba(255,255,255,0.03)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '12px',
    padding: '1.5rem',
    marginBottom: '1.5rem'
};

const INPUT_STYLE = {
    width: '100%',
    padding: '0.7rem',
    borderRadius: '8px',
    border: '1px solid rgba(255,255,255,0.15)',
    backgroundColor: 'rgba(255,255,255,0.05)',
    color: 'inherit',
    boxSizing: 'border-box' as const,
    fontSize: '0.9rem'
};

const ROLE_CONFIG: { [key: string]: { label: string, color: string, bg: string, level: string } } = {
    ROLE_ROOT_MASTER: { label: 'Root Master (TI)', color: '#fff', bg: '#e74c3c', level: 'Nível 1' },
    ROLE_ADMIN: { label: 'Administrador (Reitoria)', color: '#000', bg: '#f1c40f', level: 'Nível 2' },
    ROLE_SECRETARIA: { label: 'Secretaria (Acadêmico)', color: '#fff', bg: '#3498db', level: 'Nível 3' },
    ROLE_FINANCEIRO: { label: 'Financeiro', color: '#fff', bg: '#d35400', level: 'Nível 4' },
    ROLE_ACADEMICO: { label: 'Acadêmico (Notas)', color: '#fff', bg: '#2980b9', level: 'Nível 5' },
    ROLE_MATRICULA: { label: 'Matrícula (Vendas)', color: '#fff', bg: '#27ae60', level: 'Nível 6' },
    ROLE_COORDENADOR: { label: 'Coordenador', color: '#fff', bg: '#9b59b6', level: 'Nível 7' },
    ROLE_PROFESSOR: { label: 'Professor', color: '#fff', bg: '#2ecc71', level: 'Nível 8' },
    ROLE_MONITOR: { label: 'Monitor', color: '#fff', bg: '#1abc9c', level: 'Nível 9' },
    ROLE_BIBLIOTECARIO: { label: 'Bibliotecário', color: '#fff', bg: '#34495e', level: 'Nível 10' },
    ROLE_ALUNO: { label: 'Aluno (Discente)', color: '#fff', bg: '#7f8c8d', level: 'Usuário' },
    ROLE_CANDIDATO: { label: 'Candidato', color: '#fff', bg: '#bdc3c7', level: 'Visitante' }
};

const SELECT_STYLE = {
    ...INPUT_STYLE,
    appearance: 'none' as const,
    backgroundImage: 'url("data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2224%22%20height%3D%2224%22%20viewBox%3D%220%200%2024%2024%22%20fill%3D%22none%22%20stroke%3D%22white%22%20stroke-width%3D%222%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpolyline%20points%3D%226%209%2012%2015%2018%209%22%3E%3C%2Fpolyline%3E%3C%2Fsvg%3E")',
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 0.7rem center',
    backgroundSize: '1.2em'
};

export default function UsersManagementPage() {
    const { user: currentUser } = useAuth();
    const [users, setUsers] = useState<User[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const [isMounted, setIsMounted] = useState(false);
    useEffect(() => { setIsMounted(true); }, []);

    // Verificação de Permissão para ver o formulário
    const canCreateUser = currentUser?.roles?.some((r: any) => {
        const roleName = typeof r === 'string' ? r : r.name;
        return roleName === 'ROLE_ROOT_MASTER' || roleName === 'ROLE_ADMIN';
    });

    // Form State
    const [formData, setFormData] = useState({
        username: '',
        email: '',
        password: '',
        roles: ['ALUNO'],
        foto3x4File: null as File | null
    });

    const loadUsers = async () => {
        setIsLoading(true);
        try {
            const res = await api.get('v1/users');
            setUsers(res.data);
        } catch (e: any) {
            setError('Erro ao carregar lista de usuários. Verifique suas permissões.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadUsers(); }, []);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        
        // Bloqueio de Segurança no Frontend
        const isCurrentRoot = currentUser?.roles?.some((r: any) => (typeof r === 'string' ? r : r.name) === 'ROLE_ROOT_MASTER');
        if (formData.roles.includes('ROOT_MASTER') && !isCurrentRoot) {
            setError('Somente o Root Master original pode criar outros perfis de infraestrutura.');
            return;
        }

        setSubmitting(true);
        setError(null);
        setSuccess(null);

        try {
            const data = new FormData();
            data.append('username', formData.username);
            data.append('email', formData.email);
            data.append('password', formData.password);
            data.append('roles', formData.roles.join(','));
            if (formData.foto3x4File) {
                data.append('foto3x4File', formData.foto3x4File);
            }

            await api.post('v1/users', data, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            setSuccess('🚀 Perfil institucional registrado e ativado com sucesso!');
            setFormData({ username: '', email: '', password: '', roles: ['ALUNO'], foto3x4File: null });
            setShowForm(false);
            loadUsers();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro ao criar usuário.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (id: number, targetUsername: string, targetRoles: Role[]) => {
        // Regras de Proteção
        if (targetUsername === 'rootmaster') {
            alert('O perfil Root Master é protegido e não pode ser removido.');
            return;
        }

        // Impede que ADMIN remova ROOT_MASTER ou outro ADMIN de nível igual (opcional)
        const isTargetRootOrAdmin = targetRoles.some((r: any) => {
            const rn = typeof r === 'string' ? r : r.name;
            return rn === 'ROLE_ROOT_MASTER' || rn === 'ROLE_ADMIN';
        });
        const isCurrentRoot = currentUser?.roles?.some((r: any) => (typeof r === 'string' ? r : r.name) === 'ROLE_ROOT_MASTER');

        if (isTargetRootOrAdmin && !isCurrentRoot) {
            alert('Você não possui permissão para remover perfis de Alta Diretoria.');
            return;
        }

        if (!confirm(`Deseja remover ${targetUsername} do sistema?`)) return;
        
        try {
            await api.delete(`v1/users/${id}`);
            loadUsers();
        } catch (e) { setError('Erro ao remover usuário.'); }
    };

    if (!isMounted) return null;

    return (
        <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '1.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '3rem' }}>
                <div>
                    <h1 style={{ fontSize: '2.5rem', fontWeight: 900, letterSpacing: '-1px', background: 'linear-gradient(90deg, #fff, #888)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                        🛡️ Controle Institucional
                    </h1>
                    <p style={{ opacity: 0.5, fontSize: '0.9rem', marginTop: '0.4rem' }}>Hierarquia de 10 Níveis: Do Desenvolvedor ao Candidato</p>
                </div>
                {canCreateUser && (
                    <button 
                        onClick={() => setShowForm(!showForm)}
                        style={{
                            padding: '1rem 2rem', borderRadius: '12px',
                            backgroundColor: showForm ? 'rgba(255,255,255,0.1)' : 'var(--secondary-color)', 
                            color: showForm ? '#fff' : '#000',
                            border: 'none', cursor: 'pointer', fontWeight: 800,
                            transition: 'all 0.3s ease',
                            boxShadow: showForm ? 'none' : '0 10px 20px rgba(0,0,0,0.3)'
                        }}
                    >
                        {showForm ? '✕ Cancelar' : '+ Adicionar Colaborador'}
                    </button>
                )}
            </div>

            {error && <div style={{ backgroundColor: '#441111', border: '1px solid #ff3c3c', color: '#ffaaaa', padding: '1rem', borderRadius: '10px', marginBottom: '1.5rem' }}>⚠️ {error}</div>}
            {success && <div style={{ backgroundColor: '#114411', border: '1px solid #3cff3c', color: '#aaffaa', padding: '1rem', borderRadius: '10px', marginBottom: '1.5rem' }}>✨ {success}</div>}

            {showForm && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
                    backgroundColor: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(8px)',
                    zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center',
                    animation: 'fadeIn 0.3s ease'
                }}>
                    <form 
                        onSubmit={handleSubmit} 
                        style={{
                            ...SECTION_STYLE, 
                            width: '90%', maxWidth: '600px', 
                            backgroundColor: 'rgba(30,30,30,0.95)',
                            boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
                            border: '1px solid rgba(255,255,255,0.1)'
                        }}
                    >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                            <h2 style={{ fontSize: '1.5rem', fontWeight: 800 }}>➕ Cadastrar Novo Colaborador</h2>
                            <button 
                                type="button"
                                onClick={() => setShowForm(false)}
                                style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', fontSize: '1.5rem', opacity: 0.5 }}
                            >✕</button>
                        </div>

                        <div style={{ marginBottom: '1.5rem', display: 'flex', justifyContent: 'center' }}>
                            <PhotoUpload3x4 
                                onPhotoSelected={(file) => setFormData({...formData, foto3x4File: file})}
                                label="Foto do Colaborador (3x4)"
                            />
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.2rem' }}>
                            <div>
                                <label style={LABEL_STYLE}>Identificação do Usuário (Username)</label>
                                <input 
                                    style={INPUT_STYLE} 
                                    required 
                                    value={formData.username} 
                                    onChange={e => setFormData({...formData, username: e.target.value})}
                                    placeholder="ex: diretor.geral"
                                />
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                                <div>
                                    <label style={LABEL_STYLE}>Email Corporativo</label>
                                    <input 
                                        style={INPUT_STYLE} 
                                        required type="email" 
                                        value={formData.email} 
                                        onChange={e => setFormData({...formData, email: e.target.value})}
                                        placeholder="ex: institucional@portal.edu.br"
                                    />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Senha de Acesso</label>
                                    <input 
                                        style={INPUT_STYLE} 
                                        required type="password" 
                                        value={formData.password} 
                                        onChange={e => setFormData({...formData, password: e.target.value})}
                                        placeholder="••••••••"
                                    />
                                </div>
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Nível Hierárquico e Perfil de Função</label>
                                <select 
                                    style={SELECT_STYLE} 
                                    value={formData.roles[0]} 
                                    onChange={e => setFormData({...formData, roles: [e.target.value]})}
                                >
                                    {Object.keys(ROLE_CONFIG).map(roleKey => (
                                        <option key={roleKey} value={roleKey.replace('ROLE_', '')} style={{ backgroundColor: '#222' }}>
                                            {ROLE_CONFIG[roleKey].level} - {ROLE_CONFIG[roleKey].label}
                                        </option>
                                    ))}
                                </select>
                                <p style={{ fontSize: '0.7rem', opacity: 0.4, marginTop: '0.5rem' }}>
                                    * O perfil selecionado define as permissões automáticas conforme o regimento institucional.
                                </p>
                            </div>
                        </div>

                        <div style={{ marginTop: '2.5rem', display: 'flex', gap: '1rem' }}>
                            <button 
                                type="button"
                                onClick={() => setShowForm(false)}
                                style={{
                                    flex: 1, padding: '1rem', borderRadius: '10px',
                                    backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff',
                                    border: '1px solid rgba(255,255,255,0.1)', cursor: 'pointer', fontWeight: 600
                                }}
                            >Descartar</button>
                            <button 
                                type="submit" 
                                disabled={submitting}
                                style={{
                                    flex: 2, padding: '1rem', borderRadius: '10px',
                                    backgroundColor: 'var(--secondary-color)', color: '#000',
                                    border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 800,
                                    boxShadow: '0 10px 20px rgba(255,255,255,0.1)'
                                }}
                            >
                                {submitting ? 'Sincronizando...' : 'Confirmar e Ativar Registro'}
                            </button>
                        </div>
                    </form>
                </div>
            )}

            <div style={{ backgroundColor: 'rgba(255,255,255,0.01)', borderRadius: '20px', border: '1px solid rgba(255,255,255,0.05)', padding: '1.5rem', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                        <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', textAlign: 'left' }}>
                            <th style={{ padding: '1.2rem', opacity: 0.4, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '1px' }}>Colaborador</th>
                            <th style={{ padding: '1.2rem', opacity: 0.4, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '1px' }}>Nível de Acesso</th>
                            <th style={{ padding: '1.2rem', opacity: 0.4, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '1px', textAlign: 'right' }}>Ações</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading ? (
                            <tr><td colSpan={3} style={{ textAlign: 'center', padding: '5rem', opacity: 0.3 }}>Sincronizando com o Neon...</td></tr>
                        ) : users.map(u => (
                            <tr key={u.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)', transition: 'background 0.2s' }}>
                                <td style={{ padding: '1.2rem' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                                        <div style={{ 
                                            width: '45px', 
                                            height: '60px', 
                                            borderRadius: '4px', 
                                            backgroundColor: 'rgba(255,255,255,0.05)',
                                            overflow: 'hidden',
                                            border: '1px solid rgba(255,255,255,0.1)',
                                            flexShrink: 0
                                        }}>
                                            {u.fotoUrl ? (
                                                <img 
                                                    src={u.fotoUrl ? (u.fotoUrl.startsWith('http') ? u.fotoUrl : `${BASE_URL}/uploads/${u.fotoUrl}`) : ''} 
                                                    alt={u.username}
                                                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                />
                                            ) : (
                                                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.2rem', opacity: 0.2 }}>👤</div>
                                            )}
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: 700, fontSize: '1rem' }}>{u.username}</div>
                                            <div style={{ fontSize: '0.8rem', opacity: 0.4 }}>{u.email}</div>
                                        </div>
                                    </div>
                                </td>
                                <td style={{ padding: '1.2rem' }}>
                                    <div style={{ display: 'flex', gap: '6px' }}>
                                        {u.roles.map((r: any) => {
                                            const roleName = typeof r === 'string' ? r : r.name;
                                            const config = ROLE_CONFIG[roleName] || { label: roleName, color: '#fff', bg: '#333' };
                                            return (
                                                <span key={roleName} style={{ 
                                                    fontSize: '0.65rem', padding: '4px 10px', borderRadius: '20px', 
                                                    backgroundColor: config.bg, color: config.color,
                                                    fontWeight: 900, textTransform: 'uppercase'
                                                }}>
                                                    {config.label}
                                                </span>
                                            );
                                        })}
                                    </div>
                                </td>
                                <td style={{ padding: '1.2rem', textAlign: 'right' }}>
                                    <button 
                                        onClick={() => handleDelete(u.id, u.username, u.roles)}
                                        disabled={u.username === currentUser?.username || u.username === 'rootmaster'}
                                        style={{ 
                                            backgroundColor: 'transparent', border: '1px solid rgba(255, 60, 60, 0.3)', 
                                            color: '#ff3c3c', padding: '6px 14px', borderRadius: '8px', 
                                            cursor: 'pointer', fontSize: '0.75rem', fontWeight: 600,
                                            opacity: (u.username === currentUser?.username || u.username === 'rootmaster') ? 0.2 : 1,
                                            transition: 'all 0.2s ease'
                                        }}
                                    >
                                        Remover
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

const LABEL_STYLE = {
    display: 'block',
    fontSize: '0.8rem',
    fontWeight: 600,
    opacity: 0.6,
    marginBottom: '0.6rem'
};
