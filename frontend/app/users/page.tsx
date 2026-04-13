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

const GLASS_SECTION = {
    background: 'rgba(255, 255, 255, 0.02)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: '24px',
    padding: '2rem',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)'
};

const INPUT_STYLE = {
    width: '100%',
    padding: '0.9rem 1.2rem',
    borderRadius: '12px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    backgroundColor: 'rgba(0, 0, 0, 0.2)',
    color: '#fff',
    fontSize: '0.95rem',
    transition: 'all 0.3s ease'
};

const ROLE_CONFIG: { [key: string]: { label: string, color: string, bg: string, level: string } } = {
    ROLE_ROOT_MASTER: { label: 'Governança Suprema (RootMaster)', color: '#fff', bg: '#e74c3c', level: 'Soberano' },
    ROLE_ADMIN: { label: 'Diretoria Geral (Administrador)', color: '#000', bg: '#f1c40f', level: 'Nível II' },
    ROLE_COORDENADOR: { label: 'Coordenação Acadêmica', color: '#fff', bg: '#9b59b6', level: 'Nível III' },
    ROLE_SECRETARIA: { label: 'Secretaria Executiva', color: '#fff', bg: '#3498db', level: 'Nível IV' },
    ROLE_FINANCEIRO: { label: 'Gestão Financeira', color: '#000', bg: '#f39c12', level: 'Nível IV' },
    ROLE_PROFESSOR: { label: 'Corpo Docente', color: '#fff', bg: '#2ecc71', level: 'Nível V' },
    ROLE_ACADEMICO: { label: 'Registro Acadêmico', color: '#fff', bg: '#2980b9', level: 'Nível VI' },
    ROLE_MATRICULA: { label: 'Central de Matrículas', color: '#fff', bg: '#27ae60', level: 'Nível VI' },
    ROLE_BIBLIOTECARIO: { label: 'Sistemas de Pesquisa', color: '#fff', bg: '#34495e', level: 'Apoio' },
    ROLE_MONITOR: { label: 'Apoio Pedagógico', color: '#fff', bg: '#16a085', level: 'Apoio' },
    ROLE_ALUNO: { label: 'Perfil Discente', color: '#fff', bg: '#7f8c8d', level: 'Discente' }
};

const LABEL_STYLE = {
    display: 'block',
    fontSize: '0.75rem',
    fontWeight: 700,
    textTransform: 'uppercase' as const,
    opacity: 0.5,
    marginBottom: '0.6rem',
    letterSpacing: '1px'
};

const SELECT_STYLE = {
    ...INPUT_STYLE,
    appearance: 'none' as const,
    backgroundImage: 'url("data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2224%22%20height%3D%2224%22%20viewBox%3D%220%200%2024%2024%22%20fill%3D%22none%22%20stroke%3D%22white%22%20stroke-width%3D%222%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpolyline%20points%3D%226%209%2012%2015%2018%209%22%3E%3C%2Fpolyline%3E%3C%2Fsvg%3E")',
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 1rem center',
    backgroundSize: '1.2em'
};

export default function UsersManagementPage() {
    const { user: currentUser } = useAuth();
    const [users, setUsers] = useState<User[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [editingUser, setEditingUser] = useState<User | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [isMounted, setIsMounted] = useState(false);
    const [newRoles, setNewRoles] = useState<string[]>([]);

    useEffect(() => { setIsMounted(true); }, []);

    const getRoleLevel = (roleName: string) => {
        const rn = roleName.startsWith('ROLE_') ? roleName : `ROLE_${roleName}`;
        if (rn === 'ROLE_ROOT_MASTER') return 100;
        if (rn === 'ROLE_ADMIN') return 90;
        if (rn === 'ROLE_COORDENADOR') return 80;
        if (rn === 'ROLE_SECRETARIA' || rn === 'ROLE_FINANCEIRO') return 70;
        if (rn === 'ROLE_PROFESSOR') return 60;
        if (rn === 'ROLE_ACADEMICO' || rn === 'ROLE_MATRICULA') return 50;
        if (rn === 'ROLE_BIBLIOTECARIO' || rn === 'ROLE_MONITOR') return 40;
        return 20;
    };

    const getMaxLevel = (user: any) => {
        if (!user?.roles) return 0;
        return Math.max(...user.roles.map((r: any) => getRoleLevel(typeof r === 'string' ? r : r.name)));
    };

    const loadUsers = async () => {
        try {
            setIsLoading(true);
            const res = await api.get('v1/users');
            setUsers(res.data);
        } catch (e: any) {
            setError('⚠️ Falha de rede ou permissão insuficiente para listar governança.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { if (isMounted) loadUsers(); }, [isMounted]);

    const [formData, setFormData] = useState({
        username: '',
        email: '',
        password: '',
        roles: ['ALUNO'],
        fullName: '',
        position: '',
        department: '',
        foto3x4File: null as File | null
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        setError(null);
        setSuccess(null);

        try {
            const data = new FormData();
            data.append('username', formData.username);
            data.append('email', formData.email);
            data.append('password', formData.password);
            formData.roles.forEach(role => data.append('roles', role));
            data.append('fullName', formData.fullName);
            data.append('position', formData.position);
            data.append('department', formData.department);
            if (formData.foto3x4File) data.append('foto3x4File', formData.foto3x4File);

            setSuccess('⏳ Sincronizando ativação institucional...');
            await api.post('v1/users', data, { headers: { 'Content-Type': 'multipart/form-data' } });
            
            setSuccess('✅ Perfil ativado com sucesso!');
            setShowForm(false);
            setFormData({ username: '', email: '', password: '', roles: ['ALUNO'], fullName: '', position: '', department: '', foto3x4File: null });
            loadUsers();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro na sincronização atômica.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (user: User) => {
        if (user.username === 'rootmaster') return;
        if (!confirm(`Confirmar remoção irrevogável de ${user.username}?`)) return;
        
        try {
            await api.delete(`v1/users/${user.id}`);
            setSuccess('🗑️ Usuário removido da base institucional.');
            loadUsers();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro ao remover colaborador.');
        }
    };

    const handleUpdateRoles = async (userId: number, roles: string[]) => {
        setSubmitting(true);
        try {
            await api.put(`v1/users/${userId}/roles`, roles);
            setSuccess('🛡️ Nível de acesso atualizado com segurança.');
            setEditingUser(null);
            loadUsers();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro ao redefinir autoridade.');
        } finally {
            setSubmitting(false);
        }
    };

    if (!isMounted) return null;

    const opLevel = getMaxLevel(currentUser);

    return (
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem', animation: 'fadeIn 0.5s ease' }}>
            {/* Header */}
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '4rem' }}>
                <div>
                    <h1 style={{ fontSize: '3.5rem', fontWeight: 900, letterSpacing: '-3px', color: '#fff', marginBottom: '0.5rem' }}>
                        GOVERNANÇA <span style={{ color: 'var(--secondary-color)' }}>NG</span>
                    </h1>
                    <p style={{ opacity: 0.4, fontWeight: 700, letterSpacing: '2px', fontSize: '0.8rem' }}>PROTOCOL V50.0 // HIERARCHICAL ENGINE</p>
                </div>
                {opLevel >= 40 && (
                    <button 
                        onClick={() => setShowForm(true)}
                        style={{
                            padding: '1rem 2.5rem', borderRadius: '50px', backgroundColor: 'var(--secondary-color)',
                            color: '#000', fontWeight: 900, border: 'none', cursor: 'pointer',
                            boxShadow: '0 20px 40px rgba(0,0,0,0.3)', transition: 'all 0.3s'
                        }}
                    >
                        + NOVO COLABORADOR
                    </button>
                )}
            </header>

            {error && <div style={{ background: '#300', color: '#f88', padding: '1.2rem', borderRadius: '12px', border: '1px solid #600', marginBottom: '2rem' }}>{error}</div>}
            {success && <div style={{ background: '#030', color: '#8f8', padding: '1.2rem', borderRadius: '12px', border: '1px solid #060', marginBottom: '2rem' }}>{success}</div>}

            {/* User List Table */}
            <div style={GLASS_SECTION}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                        <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', textAlign: 'left' }}>
                            <th style={TH_STYLE}>Identidade Institucional</th>
                            <th style={TH_STYLE}>Autoridade</th>
                            <th style={{ ...TH_STYLE, textAlign: 'right' }}>Ações de Controle</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading ? (
                            <tr><td colSpan={3} style={{ padding: '4rem', textAlign: 'center', opacity: 0.3 }}>Sincronizando registros da nuvem...</td></tr>
                        ) : users.map(user => {
                            const userLevel = getMaxLevel(user);
                            const canManage = (opLevel > userLevel || opLevel === 100) && user.username !== 'rootmaster' && user.username !== currentUser?.username;
                            
                            return (
                                <tr key={user.id} style={TR_STYLE}>
                                    <td style={{ padding: '1.5rem' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '1.2rem' }}>
                                            <div style={AVATAR_CONTAINER}>
                                                {user.fotoUrl ? (
                                                    <img src={user.fotoUrl.startsWith('http') ? user.fotoUrl : `${BASE_URL}/uploads/${user.fotoUrl}`} style={AVATAR_IMG} />
                                                ) : <div style={AVATAR_PLACEHOLDER}>👤</div>}
                                            </div>
                                            <div>
                                                <div style={{ fontWeight: 800, fontSize: '1.1rem', color: '#fff' }}>{user.username}</div>
                                                <div style={{ fontSize: '0.85rem', opacity: 0.4 }}>{user.email}</div>
                                            </div>
                                        </div>
                                    </td>
                                    <td style={{ padding: '1.5rem' }}>
                                        <div style={{ display: 'flex', gap: '8px' }}>
                                            {user.roles.map(r => {
                                                const rn = typeof r === 'string' ? r : r.name;
                                                const config = ROLE_CONFIG[rn] || { label: rn, bg: '#333', color: '#fff' };
                                                return (
                                                    <span key={rn} style={{ ...BADGE_STYLE, backgroundColor: config.bg, color: config.color }}>
                                                        {config.label}
                                                    </span>
                                                );
                                            })}
                                        </div>
                                    </td>
                                    <td style={{ padding: '1.5rem', textAlign: 'right' }}>
                                        <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                                            <button 
                                                onClick={() => { setEditingUser(user); setNewRoles(user.roles.map((r: any) => (typeof r === 'string' ? r : r.name).replace('ROLE_', ''))); }}
                                                disabled={!canManage}
                                                style={{ ...ACTION_BTN, color: canManage ? 'var(--secondary-color)' : 'rgba(255,255,255,0.05)' }}
                                            >ALTERAR</button>
                                            <button 
                                                onClick={() => handleDelete(user)}
                                                disabled={!canManage}
                                                style={{ ...ACTION_BTN, color: canManage ? '#ff4757' : 'rgba(255,255,255,0.05)' }}
                                            >REMOVER</button>
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {/* Creation Modal */}
            {showForm && (
                <div style={MODAL_OVERLAY}>
                    <div style={{ ...GLASS_SECTION, width: '90%', maxWidth: '700px', backgroundColor: '#050505' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem' }}>
                            <h2 style={{ fontSize: '1.8rem', fontWeight: 900 }}>NOVO REGISTRO INSTITUCIONAL</h2>
                            <button onClick={() => setShowForm(false)} style={CLOSE_BTN}>✕</button>
                        </div>
                        <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                            <div style={{ gridColumn: 'span 2', display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
                                <PhotoUpload3x4 onPhotoSelected={(file) => setFormData({...formData, foto3x4File: file})} label="FOTO IDENTITÁRIA (3X4)" />
                            </div>
                            <div style={{ gridColumn: 'span 2' }}>
                                <label style={LABEL_STYLE}>Nome Operacional Completo</label>
                                <input style={INPUT_STYLE} required value={formData.fullName} onChange={e => setFormData({...formData, fullName: e.target.value})} placeholder="Nome completo para crachá e sistemas" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Cargo Institucional</label>
                                <input style={INPUT_STYLE} required value={formData.position} onChange={e => setFormData({...formData, position: e.target.value})} placeholder="ex: Coordenador Pedagógico" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Departamento / Setor</label>
                                <input style={INPUT_STYLE} required value={formData.department} onChange={e => setFormData({...formData, department: e.target.value})} placeholder="ex: Acadêmico" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>ID de Usuário (Username)</label>
                                <input style={INPUT_STYLE} required value={formData.username} onChange={e => setFormData({...formData, username: e.target.value})} placeholder="ex: claudio.braga" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Email Institucional</label>
                                <input style={INPUT_STYLE} required type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} placeholder="ex: institucional@portal.edu.br" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Senha Provisória</label>
                                <input style={INPUT_STYLE} required type="password" value={formData.password} onChange={e => setFormData({...formData, password: e.target.value})} placeholder="••••••••" />
                            </div>
                            <div>
                                <label style={LABEL_STYLE}>Nível de Autoridade</label>
                                <select style={SELECT_STYLE} value={formData.roles[0].replace('ROLE_', '')} onChange={e => setFormData({...formData, roles: [e.target.value]})}>
                                    {Object.keys(ROLE_CONFIG)
                                        .filter(rk => getRoleLevel(rk) < opLevel || opLevel === 100)
                                        .map(rk => (
                                            <option key={rk} value={rk.replace('ROLE_', '')} style={{ backgroundColor: '#000' }}>
                                                [{ROLE_CONFIG[rk].level}] {ROLE_CONFIG[rk].label}
                                            </option>
                                        ))
                                    }
                                </select>
                            </div>
                            <div style={{ gridColumn: 'span 2', marginTop: '2rem', display: 'flex', gap: '1rem' }}>
                                <button type="button" onClick={() => setShowForm(false)} style={{ ...MODAL_BTN, background: 'rgba(255,255,255,0.05)' }}>CANCELAR</button>
                                <button type="submit" disabled={submitting} style={{ ...MODAL_BTN, background: 'var(--secondary-color)', color: '#000', fontWeight: 900 }}>
                                    {submitting ? 'ATIVANDO...' : 'ATUALIZAR GOVERNANÇA'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Edit Modal */}
            {editingUser && (
                <div style={MODAL_OVERLAY}>
                    <div style={{ ...GLASS_SECTION, width: '90%', maxWidth: '400px', background: '#080808' }}>
                        <h2 style={{ fontSize: '1.4rem', fontWeight: 900, marginBottom: '0.5rem' }}>REDEFINIR AUTORIDADE</h2>
                        <p style={{ opacity: 0.4, fontSize: '0.8rem', marginBottom: '2rem' }}>Usuário: {editingUser.username}</p>
                        
                        <div style={{ marginBottom: '2rem' }}>
                            <label style={LABEL_STYLE}>Novo Nível Institucional</label>
                            <select style={SELECT_STYLE} value={newRoles[0]} onChange={e => setNewRoles([e.target.value])}>
                                {Object.keys(ROLE_CONFIG)
                                    .filter(rk => getRoleLevel(rk) < opLevel || opLevel === 100)
                                    .map(rk => (
                                        <option key={rk} value={rk.replace('ROLE_', '')} style={{ backgroundColor: '#000' }}>
                                            {ROLE_CONFIG[rk].label}
                                        </option>
                                    ))
                                }
                            </select>
                        </div>
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <button onClick={() => setEditingUser(null)} style={{ ...MODAL_BTN, padding: '0.8rem', fontSize: '0.8rem' }}>VOLTAR</button>
                            <button 
                                onClick={() => handleUpdateRoles(editingUser.id, newRoles)} 
                                disabled={submitting}
                                style={{ ...MODAL_BTN, padding: '0.8rem', fontSize: '0.8rem', background: 'var(--secondary-color)', color: '#000', fontWeight: 900 }}
                            >CONFIRMAR</button>
                        </div>
                    </div>
                </div>
            )}

            <style jsx>{`
                @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
            `}</style>
        </div>
    );
}

const TH_STYLE = { padding: '1.5rem', opacity: 0.3, fontSize: '0.7rem', textTransform: 'uppercase' as const, letterSpacing: '2px', fontWeight: 800 };
const TR_STYLE = { borderBottom: '1px solid rgba(255,255,255,0.03)', transition: 'background 0.3s' };
const BADGE_STYLE = { fontSize: '0.6rem', fontWeight: 900, padding: '4px 10px', borderRadius: '4px', letterSpacing: '0.5px' };
const AVATAR_CONTAINER = { width: '45px', height: '60px', borderRadius: '4px', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)' };
const AVATAR_IMG = { width: '100%', height: '100%', objectFit: 'cover' as const };
const AVATAR_PLACEHOLDER = { width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2, fontSize: '1.2rem' };
const ACTION_BTN = { background: 'none', border: 'none', fontStyle: 'italic', fontWeight: 900, fontSize: '0.7rem', cursor: 'pointer', transition: 'all 0.2s', letterSpacing: '1px' };
const MODAL_OVERLAY = { position: 'fixed' as const, top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.92)', backdropFilter: 'blur(30px)', zIndex: 3000, display: 'flex', alignItems: 'center', justifyContent: 'center' };
const CLOSE_BTN = { background: 'none', border: 'none', color: '#fff', fontSize: '1.5rem', cursor: 'pointer', opacity: 0.3 };
const MODAL_BTN = { flex: 1, padding: '1.2rem', borderRadius: '12px', border: 'none', color: '#fff', cursor: 'pointer', transition: 'all 0.3s' };

