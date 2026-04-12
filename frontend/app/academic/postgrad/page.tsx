'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '@/app/services/api';
import PhotoUpload3x4 from "@/components/PhotoUpload3x4";
import { useAuditCRUD } from '@/app/hooks/useAuditCRUD';
import { AuditStamp } from '@/app/components/AuditStamp';
import { toast } from 'sonner';
import { User, FileText, Trash2, X, Eye, GraduationCap, Phone, Mail, MapPin, Calendar, ClipboardCheck } from 'lucide-react';

interface PostgradStudent {
    id: number;
    fullName: string;
    email: string;
    cpf: string;
    phone: string;
    dateOfBirth: string;
    address: string;
    graduationInstitution: string;
    graduationYear: number;
    desiredCourse: string;
    enrollmentStatus: string;
    diplomaFilePath: string | null;
    rgCpfFilePath: string | null;
    proofOfAddressFilePath: string | null;
    academicTranscriptFilePath: string | null;
    registrationDate: string;
    creatorName?: string;
    creatorPosition?: string;
    creatorPhotoUrl?: string;
    fotoMatricula?: string;
}

const STATUS_COLORS: Record<string, string> = {
    PENDENTE: '#f0a500',
    APROVADO: '#2ecc71',
    REJEITADO: '#e74c3c',
};

export default function PostgradStudentsPage() {
    const [students, setStudents] = useState<PostgradStudent[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [editingStudent, setEditingStudent] = useState<PostgradStudent | null>(null);
    const [viewingStudent, setViewingStudent] = useState<PostgradStudent | null>(null);

    // Contexto SUPREME
    const postgradService = useAuditCRUD('v1/postgrad-students');
    const isLoading = postgradService.loading;

    const loadStudents = async () => {
        try {
            const data = await postgradService.list();
            setStudents(data);
        } catch (e: any) {
            toast.error('Erro ao carregar lista de pós-graduandos.');
        }
    };

    useEffect(() => { loadStudents(); }, []);

    // Form State (Cadastro)
    const [formData, setFormData] = useState({
        fullName: '', email: '', cpf: '', phone: '',
        dateOfBirth: '', address: '', graduationInstitution: '',
        graduationYear: '', desiredCourse: '',
    });
    const [files, setFiles] = useState<Record<string, File | null>>({
        diplomaFile: null, rgCpfFile: null, proofOfAddressFile: null, academicTranscriptFile: null, foto3x4File: null
    });

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFiles({ ...files, [e.target.name]: e.target.files?.[0] || null });
    };

    const handlePhotoChange = (file: File | null) => {
        setFiles(prev => ({ ...prev, foto3x4File: file }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        setError(null);
        setSuccess(null);

        const form = new FormData();
        Object.entries(formData).forEach(([key, value]) => {
            if (value) form.append(key, value);
        });
        Object.entries(files).forEach(([key, file]) => {
            if (file) form.append(key, file);
        });

        try {
            await api.post('v1/postgrad-students', form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            setSuccess('✅ Aluno cadastrado com sucesso!');
            setShowForm(false);
            setFormData({ fullName: '', email: '', cpf: '', phone: '', dateOfBirth: '', address: '', graduationInstitution: '', graduationYear: '', desiredCourse: '' });
            setFiles({ diplomaFile: null, rgCpfFile: null, proofOfAddressFile: null, academicTranscriptFile: null });
            loadStudents();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro ao cadastrar aluno.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleStatusChange = async (id: number, status: string) => {
        try {
            await api.patch(`v1/postgrad-students/${id}/status?status=${status}`);
            toast.success('Status atualizado com sucesso!');
            loadStudents();
        } catch (e) { toast.error('Erro ao atualizar status.'); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Tem certeza que deseja remover este aluno? Esta operação é um Soft Delete (exclusão lógica).')) return;
        try {
            await postgradService.remove(id);
            loadStudents();
        } catch (e) { /* toast handled by hook */ }
    };

    const handleUpdate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingStudent) return;
        setSubmitting(true);

        try {
            const form = new FormData();
            form.append('fullName', editingStudent.fullName);
            form.append('email', editingStudent.email);
            form.append('phone', editingStudent.phone);
            form.append('desiredCourse', editingStudent.desiredCourse);
            
            if (files.foto3x4_update) {
                form.append('foto3x4File', files.foto3x4_update);
            }

            await api.put(`v1/postgrad-students/${editingStudent.id}`, form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            toast.success('Registro atualizado com sucesso!');
            setEditingStudent(null);
            loadStudents();
        } catch (e: any) {
            toast.error(e?.response?.data?.message || 'Erro ao atualizar registro.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
            {/* Cabeçalho */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                <div>
                    <h1 style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--secondary-color)' }}>
                        📋 Cadastro de Pós-Graduação
                    </h1>
                    <p style={{ opacity: 0.6, marginTop: '0.3rem' }}>Gerencie o cadastramento de alunos para cursos de pós-graduação</p>
                </div>
                <button
                    onClick={() => setShowForm(!showForm)}
                    style={{
                        padding: '0.7rem 1.5rem', borderRadius: '8px',
                        backgroundColor: 'var(--secondary-color)', color: '#000',
                        border: 'none', cursor: 'pointer', fontWeight: 700,
                        fontSize: '0.95rem',
                    }}
                >
                    {showForm ? '✕ Cancelar' : '+ Novo Aluno'}
                </button>
            </div>

            {/* Alertas */}
            {error && (
                <div style={{ backgroundColor: 'rgba(231,76,60,0.1)', border: '1px solid #e74c3c', borderRadius: '8px', padding: '1rem', marginBottom: '1rem', color: '#e74c3c' }}>
                    ⚠️ {error}
                </div>
            )}
            {success && (
                <div style={{ backgroundColor: 'rgba(46,204,113,0.1)', border: '1px solid #2ecc71', borderRadius: '8px', padding: '1rem', marginBottom: '1rem', color: '#2ecc71' }}>
                    {success}
                </div>
            )}

            {/* Formulário */}
            {showForm && (
                <form onSubmit={handleSubmit} style={{ backgroundColor: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', padding: '2rem', marginBottom: '2rem' }}>
                    <h2 style={{ marginBottom: '1.5rem', fontSize: '1.2rem' }}>📝 Dados do Candidato</h2>

                    <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '2rem' }}>
                        <PhotoUpload3x4 onPhotoSelected={handlePhotoChange} label="Foto Acadêmica 3x4" />
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                        {[
                            { label: 'Nome Completo *', name: 'fullName', type: 'text', required: true },
                            { label: 'E-mail *', name: 'email', type: 'email', required: true },
                            { label: 'CPF *', name: 'cpf', type: 'text', required: true, placeholder: '000.000.000-00' },
                            { label: 'Telefone', name: 'phone', type: 'text', placeholder: '(00) 00000-0000' },
                            { label: 'Data de Nascimento', name: 'dateOfBirth', type: 'date' },
                            { label: 'Endereço Completo', name: 'address', type: 'text' },
                            { label: 'Instituição de Graduação *', name: 'graduationInstitution', type: 'text', required: true },
                            { label: 'Ano de Formação', name: 'graduationYear', type: 'number', placeholder: '2020' },
                        ].map(f => (
                            <div key={f.name}>
                                <label style={{ display: 'block', marginBottom: '0.4rem', opacity: 0.8, fontSize: '0.85rem' }}>{f.label}</label>
                                <input
                                    name={f.name} type={f.type} required={f.required}
                                    placeholder={f.placeholder}
                                    value={(formData as any)[f.name]}
                                    onChange={handleInputChange}
                                    style={{ width: '100%', padding: '0.6rem', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.2)', backgroundColor: 'rgba(255,255,255,0.05)', color: 'inherit', boxSizing: 'border-box' }}
                                />
                            </div>
                        ))}
                    </div>

                    <div style={{ marginBottom: '1rem' }}>
                        <label style={{ display: 'block', marginBottom: '0.4rem', opacity: 0.8, fontSize: '0.85rem' }}>Curso Desejado *</label>
                        <select
                            name="desiredCourse" required value={formData.desiredCourse} onChange={handleInputChange}
                            style={{ width: '100%', padding: '0.6rem', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.2)', backgroundColor: 'rgba(30,30,50,0.9)', color: 'inherit' }}
                        >
                            <option value="">-- Selecione um Curso --</option>
                            <option value="MBA em Gestão de Negócios">MBA em Gestão de Negócios</option>
                            <option value="Especialização em Engenharia de Software">Especialização em Engenharia de Software</option>
                            <option value="MBA em Recursos Humanos">MBA em Recursos Humanos</option>
                            <option value="Especialização em Direito Digital">Especialização em Direito Digital</option>
                            <option value="Mestrado Profissional em Educação">Mestrado Profissional em Educação</option>
                        </select>
                    </div>

                    <h2 style={{ margin: '1.5rem 0 1rem', fontSize: '1.1rem' }}>📁 Documentos Obrigatórios</h2>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
                        {[
                            { label: '🎓 Diploma de Graduação (PDF/Imagem)', name: 'diplomaFile' },
                            { label: '🪪 RG + CPF (PDF/Imagem)', name: 'rgCpfFile' },
                            { label: '🏠 Comprovante de Residência (PDF/Imagem)', name: 'proofOfAddressFile' },
                            { label: '📄 Histórico Acadêmico (PDF)', name: 'academicTranscriptFile' },
                        ].map(f => (
                            <div key={f.name}>
                                <label style={{ display: 'block', marginBottom: '0.4rem', opacity: 0.8, fontSize: '0.85rem' }}>{f.label}</label>
                                <input
                                    type="file" name={f.name} accept=".pdf,.jpg,.jpeg,.png"
                                    onChange={handleFileChange}
                                    style={{ width: '100%', padding: '0.4rem', fontSize: '0.85rem', color: 'inherit' }}
                                />
                            </div>
                        ))}
                    </div>

                    <button type="submit" disabled={submitting} style={{ padding: '0.8rem 2rem', borderRadius: '8px', backgroundColor: 'var(--secondary-color)', color: '#000', border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700, opacity: submitting ? 0.7 : 1 }}>
                        {submitting ? '⏳ Salvando...' : '✅ Cadastrar Aluno'}
                    </button>
                </form>
            )}

            {/* Tabela de Alunos */}
            <div style={{ backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '12px', overflow: 'hidden' }}>
                <div style={{ padding: '1rem 1.5rem', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h2 style={{ fontSize: '1rem', opacity: 0.9 }}>Candidatos Cadastrados ({students.length})</h2>
                </div>

                {isLoading ? (
                    <div style={{ padding: '3rem', textAlign: 'center', opacity: 0.5 }}>⏳ Carregando...</div>
                ) : students.length === 0 ? (
                    <div style={{ padding: '3rem', textAlign: 'center', opacity: 0.5 }}>
                        <p>Nenhum aluno cadastrado ainda.</p>
                        <p style={{ fontSize: '0.85rem', marginTop: '0.5rem' }}>Clique em "+ Novo Aluno" para começar.</p>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
                            <thead>
                                <tr style={{ backgroundColor: 'rgba(255,255,255,0.05)' }}>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'left', opacity: 0.7, fontWeight: 600 }}>Candidato</th>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'left', opacity: 0.7, fontWeight: 600 }}>Curso / Instituição</th>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'left', opacity: 0.7, fontWeight: 600 }}>Documentos</th>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'left', opacity: 0.7, fontWeight: 600 }}>Auditoria MEC</th>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'left', opacity: 0.7, fontWeight: 600 }}>Status</th>
                                    <th style={{ padding: '1.2rem 1.5rem', textAlign: 'center', opacity: 0.7, fontWeight: 600 }}>Ações</th>
                                </tr>
                            </thead>
                            <tbody>
                                {students.map((s, i) => (
                                    <tr key={s.id} style={{ borderTop: '1px solid rgba(255,255,255,0.06)', backgroundColor: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)', transition: 'background 0.2s' }}>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '1.2rem' }}>
                                                <div style={{ width: '45px', height: '60px', borderRadius: '8px', backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', overflow: 'hidden', flexShrink: 0 }}>
                                                    {s.fotoMatricula ? (
                                                        <img src={`${BASE_URL}/uploads/${s.fotoMatricula}`} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                                    ) : (
                                                        <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2 }}><User size={20} /></div>
                                                    )}
                                                </div>
                                                <div>
                                                    <div style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '0.2rem' }}>{s.fullName}</div>
                                                    <div style={{ fontSize: '0.75rem', opacity: 0.5, display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                                                        <Mail size={12} /> {s.email}
                                                    </div>
                                                </div>
                                            </div>
                                        </td>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <div style={{ fontWeight: 600, color: 'var(--secondary-color)' }}>{s.desiredCourse}</div>
                                            <div style={{ fontSize: '0.75rem', opacity: 0.5 }}>{s.graduationInstitution} ({s.graduationYear})</div>
                                        </td>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <div style={{ display: 'flex', gap: '12px' }}>
                                                {[
                                                    { label: '🎓', key: 'diplomaFilePath', title: 'Diploma' },
                                                    { label: '🪪', key: 'rgCpfFilePath', title: 'RG/CPF' },
                                                    { label: '🏠', key: 'proofOfAddressFilePath', title: 'Residência' },
                                                    { label: '📄', key: 'academicTranscriptFilePath', title: 'Histórico' },
                                                ].map(d => {
                                                    const path = (s as any)[d.key];
                                                    return (
                                                        <a
                                                            key={d.key}
                                                            href={path ? `${BASE_URL}/uploads/${path}` : '#'}
                                                            target="_blank"
                                                            rel="noopener noreferrer"
                                                            title={path ? d.title : 'Não enviado'}
                                                            style={{
                                                                opacity: path ? 1 : 0.15,
                                                                fontSize: '1.3rem',
                                                                textDecoration: 'none',
                                                                cursor: path ? 'pointer' : 'default',
                                                                filter: path ? 'none' : 'grayscale(100%)',
                                                                transition: 'transform 0.2s',
                                                            }}
                                                            onMouseEnter={(e) => path && (e.currentTarget.style.transform = 'scale(1.2)')}
                                                            onMouseLeave={(e) => path && (e.currentTarget.style.transform = 'scale(1)')}
                                                        >
                                                            {d.label}
                                                        </a>
                                                    );
                                                })}
                                            </div>
                                        </td>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <AuditStamp 
                                                name={s.creatorName}
                                                position={s.creatorPosition}
                                                photoUrl={s.creatorPhotoUrl ? (s.creatorPhotoUrl.startsWith('http') ? s.creatorPhotoUrl : `${BASE_URL}/uploads/${s.creatorPhotoUrl}`) : undefined}
                                                date={s.registrationDate}
                                            />
                                        </td>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <select
                                                value={s.enrollmentStatus}
                                                onChange={(e) => handleStatusChange(s.id, e.target.value)}
                                                style={{ padding: '0.5rem 1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: STATUS_COLORS[s.enrollmentStatus] + '15', color: STATUS_COLORS[s.enrollmentStatus], fontWeight: 700, cursor: 'pointer', outline: 'none' }}
                                            >
                                                <option value="PENDENTE">PENDENTE</option>
                                                <option value="APROVADO">APROVADO</option>
                                                <option value="REJEITADO">REJEITADO</option>
                                            </select>
                                        </td>
                                        <td style={{ padding: '1.2rem 1.5rem' }}>
                                            <div style={{ display: 'flex', gap: '0.8rem', justifyContent: 'center' }}>
                                                <button onClick={() => setViewingStudent(s)} style={{ padding: '0.6rem', background: 'rgba(255,255,255,0.05)', color: '#fff', borderRadius: '10px', border: 'none', cursor: 'pointer', transition: 'background 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.1)'} onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.05)'} title="Visualizar Ficha">
                                                    <Eye size={18} />
                                                </button>
                                                <button onClick={() => setEditingStudent(s)} style={{ padding: '0.6rem', background: 'rgba(59,130,246,0.1)', color: '#3b82f6', borderRadius: '10px', border: 'none', cursor: 'pointer', transition: 'background 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(59,130,246,0.2)'} onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(59,130,246,0.1)'} title="Editar Cadastro">
                                                    <FileText size={18} />
                                                </button>
                                                <button onClick={() => handleDelete(s.id)} style={{ padding: '0.6rem', background: 'rgba(239,68,68,0.1)', color: '#ef4444', borderRadius: '10px', border: 'none', cursor: 'pointer', transition: 'background 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(239,68,68,0.2)'} onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(239,68,68,0.1)'} title="Excluir (Soft Delete)">
                                                    <Trash2 size={18} />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
            {/* MODAL: Edição de Aluno (Procedimento U) */}
            {editingStudent && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
                    <div className="glass-panel fade-in" style={{ padding: '2.5rem', width: '100%', maxWidth: '650px', backgroundColor: '#0f172a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '24px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                            <h3 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fff' }}>📝 Editar Registro Acadêmico</h3>
                            <button onClick={() => setEditingStudent(null)} style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#999' }}>&times;</button>
                        </div>

                        <form onSubmit={handleUpdate} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                            <div style={{ gridColumn: 'span 2' }}>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>Nome Completo</label>
                                <input value={editingStudent.fullName} onChange={e => setEditingStudent({...editingStudent, fullName: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '10px', background: '#1e293b', border: '1px solid #334155', color: '#fff' }} />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>E-mail</label>
                                <input value={editingStudent.email} onChange={e => setEditingStudent({...editingStudent, email: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '10px', background: '#1e293b', border: '1px solid #334155', color: '#fff' }} />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>Telefone</label>
                                <input value={editingStudent.phone} onChange={e => setEditingStudent({...editingStudent, phone: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '10px', background: '#1e293b', border: '1px solid #334155', color: '#fff' }} />
                            </div>
                            <div style={{ gridColumn: 'span 2' }}>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>Curso Atual</label>
                                <input value={editingStudent.desiredCourse} onChange={e => setEditingStudent({...editingStudent, desiredCourse: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '10px', background: '#1e293b', border: '1px solid #334155', color: '#fff' }} />
                            </div>
                            <div style={{ gridColumn: 'span 2' }}>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>Nova Foto (Opcional)</label>
                                <input type="file" accept="image/*" onChange={e => setFiles({...files, foto3x4_update: e.target.files?.[0] || null})} style={{ width: '100%', padding: '10px', borderRadius: '10px', background: 'rgba(255,255,255,0.05)', border: '1px dashed rgba(255,255,255,0.2)', color: '#fff' }} />
                            </div>
                            <div style={{ gridColumn: 'span 2', display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                                <button type="button" onClick={() => setEditingStudent(null)} style={{ padding: '12px 25px', borderRadius: '10px', background: 'rgba(255,255,255,0.05)', color: '#fff', border: 'none', cursor: 'pointer' }}>Cancelar</button>
                                <button type="submit" disabled={submitting} style={{ padding: '12px 30px', borderRadius: '10px', background: 'var(--secondary-color)', color: '#000', fontWeight: 800, border: 'none', cursor: 'pointer' }}>
                                    {submitting ? 'Salvando...' : 'Salvar Alterações'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* MODAL: Ficha Detalhada (Procedimento R) */}
            {viewingStudent && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(12px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
                    <div style={{ width: '100%', maxWidth: '850px', backgroundColor: '#0a0f1e', borderRadius: '32px', border: '1px solid rgba(255,255,255,0.1)', overflow: 'hidden', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
                        <div style={{ padding: '2.5rem', display: 'flex', gap: '2.5rem' }}>
                            <div style={{ width: '220px', flexShrink: 0 }}>
                                <div style={{ width: '100%', aspectRatio: '3/4', borderRadius: '20px', backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', overflow: 'hidden', marginBottom: '1.5rem' }}>
                                    {viewingStudent.fotoMatricula ? (
                                        <img src={viewingStudent.fotoMatricula.startsWith('http') ? viewingStudent.fotoMatricula : `${BASE_URL}/uploads/${viewingStudent.fotoMatricula}`} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                    ) : (
                                        <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2 }}><User size={64} /></div>
                                    )}
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                    <div style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.4)', textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: '0.4rem' }}>Matrícula Pós</div>
                                    <div style={{ fontWeight: 800, color: 'var(--secondary-color)' }}>{viewingStudent.id.toString().padStart(6, '0')}</div>
                                </div>
                            </div>

                            <div style={{ flexGrow: 1 }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
                                    <div>
                                        <h2 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: '0.5rem' }}>{viewingStudent.fullName}</h2>
                                        <span style={{ padding: '6px 14px', borderRadius: '12px', backgroundColor: (STATUS_COLORS[viewingStudent.enrollmentStatus] || '#7f8c8d') + '20', color: STATUS_COLORS[viewingStudent.enrollmentStatus], fontWeight: 700, fontSize: '0.8rem' }}>
                                            {viewingStudent.enrollmentStatus}
                                        </span>
                                    </div>
                                    <button onClick={() => setViewingStudent(null)} style={{ padding: '0.8rem', borderRadius: '50%', background: 'rgba(255,255,255,0.05)', color: '#fff', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
                                </div>

                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem' }}>
                                    <div>
                                        <h4 style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '0.8rem' }}>Acadêmico</h4>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>Curso</div><div style={{ fontWeight: 600 }}>{viewingStudent.desiredCourse}</div></div>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>Instituição</div><div style={{ fontWeight: 600 }}>{viewingStudent.graduationInstitution}</div></div>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>Ano Formação</div><div style={{ fontWeight: 600 }}>{viewingStudent.graduationYear}</div></div>
                                        </div>
                                    </div>
                                    <div>
                                        <h4 style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '0.8rem' }}>Contato</h4>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>E-mail</div><div style={{ fontWeight: 600 }}>{viewingStudent.email}</div></div>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>CPF</div><div style={{ fontWeight: 600 }}>{viewingStudent.cpf}</div></div>
                                            <div><div style={{ fontSize: '0.8rem', opacity: 0.5 }}>Telefone</div><div style={{ fontWeight: 600 }}>{viewingStudent.phone}</div></div>
                                        </div>
                                    </div>
                                </div>

                                <div style={{ marginTop: '2.5rem', padding: '1.5rem', borderRadius: '20px', backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                                    <AuditStamp 
                                        name={viewingStudent.creatorName || 'Sistema'}
                                        position={viewingStudent.creatorPosition || 'Administrador'}
                                        photoUrl={viewingStudent.creatorPhotoUrl ? `${BASE_URL}/uploads/${viewingStudent.creatorPhotoUrl}` : undefined}
                                        date={viewingStudent.registrationDate}
                                    />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
