'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '@/app/services/api';
import PhotoUpload3x4 from "@/components/PhotoUpload3x4";

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
}

const STATUS_COLORS: Record<string, string> = {
    PENDENTE: '#f0a500',
    APROVADO: '#2ecc71',
    REJEITADO: '#e74c3c',
};

export default function PostgradStudentsPage() {
    const [students, setStudents] = useState<PostgradStudent[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    // Form State
    const [formData, setFormData] = useState({
        fullName: '', email: '', cpf: '', phone: '',
        dateOfBirth: '', address: '', graduationInstitution: '',
        graduationYear: '', desiredCourse: '',
    });
    const [files, setFiles] = useState<Record<string, File | null>>({
        diplomaFile: null, rgCpfFile: null, proofOfAddressFile: null, academicTranscriptFile: null, foto3x4File: null
    });

    const loadStudents = async () => {
        setIsLoading(true);
        try {
            const res = await api.get('v1/postgrad-students');
            setStudents(res.data);
        } catch (e: any) {
            setError('Erro ao carregar alunos. Verifique o backend.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadStudents(); }, []);

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
            await api.put(`v1/postgrad-students/${id}/status?status=${status}`);
            loadStudents();
        } catch (e) { setError('Erro ao atualizar status.'); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Tem certeza que deseja remover este aluno?')) return;
        try {
            await api.delete(`v1/postgrad-students/${id}`);
            loadStudents();
        } catch (e) { setError('Erro ao remover aluno.'); }
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
                                    {['Nome', 'E-mail', 'CPF', 'Curso', 'Documentos', 'Status', 'Ações'].map(h => (
                                        <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', opacity: 0.7, fontWeight: 600, whiteSpace: 'nowrap' }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {students.map((s, i) => (
                                    <tr key={s.id} style={{ borderTop: '1px solid rgba(255,255,255,0.06)', backgroundColor: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                                        <td style={{ padding: '0.8rem 1rem', fontWeight: 500 }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                                {(s as any).fotoUrl ? (
                                                    <img
                                                        src={`${BASE_URL}/uploads/fotos-perfil/${(s as any).fotoUrl}`}
                                                        alt={s.fullName}
                                                        style={{ width: '30px', height: '40px', borderRadius: '4px', objectFit: 'cover' }}
                                                    />
                                                ) : (
                                                    <div style={{ width: '30px', height: '40px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.2rem' }}>
                                                        👤
                                                    </div>
                                                )}
                                                {s.fullName}
                                            </div>
                                        </td>
                                        <td style={{ padding: '0.8rem 1rem', opacity: 0.8 }}>{s.email}</td>
                                        <td style={{ padding: '0.8rem 1rem', opacity: 0.8 }}>{s.cpf}</td>
                                        <td style={{ padding: '0.8rem 1rem', opacity: 0.8 }}>{s.desiredCourse}</td>
                                        <td style={{ padding: '0.8rem 1rem' }}>
                                            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                                {[
                                                    { label: '🎓', key: 'diplomaFilePath' },
                                                    { label: '🪪', key: 'rgCpfFilePath' },
                                                    { label: '🏠', key: 'proofOfAddressFilePath' },
                                                    { label: '📄', key: 'academicTranscriptFilePath' },
                                                ].map(d => {
                                                    const path = (s as any)[d.key];
                                                    return (
                                                        <a
                                                            key={d.key}
                                                            href={path ? `${BASE_URL}/uploads/${path}` : '#'}
                                                            target="_blank"
                                                            rel="noopener noreferrer"
                                                            title={path || 'Não enviado'}
                                                            style={{
                                                                opacity: path ? 1 : 0.2,
                                                                fontSize: '1.2rem',
                                                                textDecoration: 'none',
                                                                cursor: path ? 'pointer' : 'default',
                                                                filter: path ? 'none' : 'grayscale(100%)'
                                                            }}
                                                        >
                                                            {d.label}
                                                        </a>
                                                    );
                                                })}
                                            </div>
                                        </td>
                                        <td style={{ padding: '0.8rem 1rem' }}>
                                            <select
                                                value={s.enrollmentStatus}
                                                onChange={(e) => handleStatusChange(s.id, e.target.value)}
                                                style={{ padding: '4px 8px', borderRadius: '4px', border: 'none', backgroundColor: STATUS_COLORS[s.enrollmentStatus] + '33', color: STATUS_COLORS[s.enrollmentStatus], fontWeight: 600, cursor: 'pointer' }}
                                            >
                                                <option value="PENDENTE">PENDENTE</option>
                                                <option value="APROVADO">APROVADO</option>
                                                <option value="REJEITADO">REJEITADO</option>
                                            </select>
                                        </td>
                                        <td style={{ padding: '0.8rem 1rem' }}>
                                            <button
                                                onClick={() => handleDelete(s.id)}
                                                style={{ padding: '4px 10px', backgroundColor: 'rgba(231,76,60,0.15)', color: '#e74c3c', border: '1px solid #e74c3c', borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem' }}
                                            >
                                                🗑️ Remover
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
