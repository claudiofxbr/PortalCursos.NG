'use client';

import React, { useState, useEffect } from 'react';
import api from '@/app/services/api';
import { 
  User, 
  MapPin, 
  FileText, 
  CheckCircle, 
  ChevronRight, 
  ChevronLeft, 
  Upload,
  AlertCircle,
  GraduationCap,
  Trash2,
  Plus,
  X
} from "lucide-react";
import { toast } from "sonner";

interface GradStudent {
    id: number;
    fullName: string;
    email: string;
    cpf: string;
    phone: string;
    dateOfBirth: string;
    address: string;
    currentCourse: string;
    enrollmentStatus: string;
    registrationNumber: string;
    documents: any[];
}

const STATUS_COLORS: Record<string, string> = {
    PENDENTE: '#f0a500',
    PENDENTE_VALIDACAO: '#f0a500',
    APROVADO: '#2ecc71',
    REJEITADO: '#e74c3c',
};

export default function GradEnrollPage() {
    const [students, setStudents] = useState<GradStudent[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Form State
    const [formData, setFormData] = useState({
        fullName: "",
        email: "",
        cpf: "",
        phone: "",
        dateOfBirth: "",
        address: "",
        currentCourse: "",
        nacionalidade: "Brasileira",
        estadoCivil: "Solteiro(a)",
        sexo: "Masculino",
        numeroReservista: "",
        tituloEleitor: "",
        isEstrangeiro: "false",
        formaIngresso: "VESTIBULAR_PROPRIO",
        tipoCota: "NENHUMA",
    });

    const [files, setFiles] = useState<Record<string, File | null>>({
        foto3x4: null, rgCpf: null, comprovanteResidencia: null, certificadoEM: null, 
        historicoEM: null, enemSisu: null, diplomaAnt: null, historicoIesAnt: null, 
        laudoMedico: null, rnmRne: null
    });

    const loadStudents = async () => {
        setIsLoading(true);
        try {
            const res = await api.get('v1/grad-students');
            setStudents(res.data);
        } catch (e: any) {
            toast.error('Erro ao carregar alunos. Verifique o backend.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadStudents(); }, []);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFiles({ ...files, [e.target.name]: e.target.files?.[0] || null });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);

        const form = new FormData();
        Object.entries(formData).forEach(([key, value]) => {
            if (value) form.append(key, value);
        });
        Object.entries(files).forEach(([key, file]) => {
            if (file) form.append(key, file);
        });

        try {
            await api.post('v1/grad-students/enroll', form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            toast.success('✅ Aluno matriculado com sucesso!');
            setShowForm(false);
            resetForm();
            loadStudents();
        } catch (e: any) {
            toast.error(e?.response?.data?.message || 'Erro ao realizar matrícula.');
        } finally {
            setSubmitting(false);
        }
    };

    const resetForm = () => {
        setFormData({
            fullName: "", email: "", cpf: "", phone: "", dateOfBirth: "", address: "",
            currentCourse: "", nacionalidade: "Brasileira", estadoCivil: "Solteiro(a)",
            sexo: "Masculino", numeroReservista: "", tituloEleitor: "",
            isEstrangeiro: "false", formaIngresso: "VESTIBULAR_PROPRIO", tipoCota: "NENHUMA",
        });
        setFiles({
            foto3x4: null, rgCpf: null, comprovanteResidencia: null, certificadoEM: null,
            historicoEM: null, enemSisu: null, diplomaAnt: null, historicoIesAnt: null,
            laudoMedico: null, rnmRne: null
        });
    };

    const handleStatusChange = async (id: number, status: string) => {
        try {
            await api.put(`v1/grad-students/${id}/status?status=${status}`);
            loadStudents();
            toast.success('Status atualizado!');
        } catch (e) { toast.error('Erro ao atualizar status.'); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Tem certeza que deseja remover este aluno?')) return;
        try {
            await api.delete(`v1/grad-students/${id}`);
            loadStudents();
            toast.success('Aluno removido.');
        } catch (e) { toast.error('Erro ao remover aluno.'); }
    };

    return (
        <div style={{ maxWidth: '1280px', margin: '0 auto', padding: '1rem' }}>
            {/* Cabeçalho */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem' }}>
                <div>
                    <h1 style={{ fontSize: '2.2rem', fontWeight: 800, letterSpacing: '-0.025em', color: '#fff', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        🎓 Secretaria de Graduação
                    </h1>
                    <p style={{ color: 'rgba(255,255,255,0.5)', marginTop: '0.5rem', fontSize: '1.1rem' }}>Gerenciamento e validação de inscrições para graduação</p>
                </div>
                <button
                    onClick={() => setShowForm(!showForm)}
                    style={{
                        padding: '0.8rem 1.8rem', borderRadius: '12px',
                        backgroundColor: showForm ? 'rgba(255,255,255,0.1)' : 'var(--secondary-color)', 
                        color: showForm ? '#fff' : '#000',
                        border: showForm ? '1px solid rgba(255,255,255,0.2)' : 'none', 
                        cursor: 'pointer', fontWeight: 700,
                        fontSize: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem',
                        transition: 'all 0.2s ease'
                    }}
                >
                    {showForm ? <X size={18} /> : <Plus size={18} />}
                    {showForm ? 'Fechar Formulário' : 'Nova Inscrição'}
                </button>
            </div>

            {/* Formulário Estilo Unificado */}
            {showForm && (
                <div style={{ backgroundColor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '24px', padding: '2.5rem', marginBottom: '3rem', backdropFilter: 'blur(20px)', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
                    <form onSubmit={handleSubmit}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '2rem' }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: 'var(--secondary-color)', display: 'flex', alignItems: 'center', justifyItems: 'center', justifyContent: 'center', color: '#000' }}>
                                <User size={20} />
                            </div>
                            <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Dados do Candidato</h2>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.5rem', marginBottom: '2rem' }}>
                            {[
                                { label: 'Nome Completo *', name: 'fullName', type: 'text', required: true },
                                { label: 'E-mail *', name: 'email', type: 'email', required: true },
                                { label: 'CPF *', name: 'cpf', type: 'text', required: true, placeholder: '000.000.000-00' },
                                { label: 'Telefone', name: 'phone', type: 'text', placeholder: '(00) 00000-0000' },
                                { label: 'Data de Nascimento', name: 'dateOfBirth', type: 'date' },
                                { label: 'Nacionalidade', name: 'nacionalidade', type: 'text' },
                            ].map(f => (
                                <div key={f.name}>
                                    <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.9rem', fontWeight: 500 }}>{f.label}</label>
                                    <input
                                        name={f.name} type={f.type} required={f.required}
                                        placeholder={f.placeholder}
                                        value={(formData as any)[f.name]}
                                        onChange={handleInputChange}
                                        style={{ width: '100%', padding: '0.8rem 1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: '1rem', outline: 'none', transition: 'border-color 0.2s' }}
                                    />
                                </div>
                            ))}
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.9rem', fontWeight: 500 }}>Ingresso *</label>
                                <select name="formaIngresso" value={formData.formaIngresso} onChange={handleInputChange} style={{ width: '100%', padding: '0.8rem 1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: '1rem', outline: 'none' }}>
                                    <option value="VESTIBULAR_PROPRIO">Vestibular Próprio</option>
                                    <option value="ENEM_SISU">ENEM / SISU</option>
                                    <option value="GRAD_2">2ª Graduação / Portador de Diploma</option>
                                    <option value="TRANSFERENCIA">Transferência Externa</option>
                                </select>
                            </div>
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.9rem', fontWeight: 500 }}>Cota *</label>
                                <select name="tipoCota" value={formData.tipoCota} onChange={handleInputChange} style={{ width: '100%', padding: '0.8rem 1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: '1rem', outline: 'none' }}>
                                    <option value="NENHUMA">Ampla Concorrência</option>
                                    <option value="SOCIOECONOMICA">Cota Socioeconômica</option>
                                    <option value="RACIAL">Cota Racial</option>
                                    <option value="PCD">Pessoa com Deficiência (PCD)</option>
                                </select>
                            </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '2rem', marginTop: '3rem' }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: '#3498db', display: 'flex', alignItems: 'center', justifyItems: 'center', justifyContent: 'center', color: '#fff' }}>
                                <FileText size={20} />
                            </div>
                            <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Documentos Obrigatórios</h2>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '1.5rem', marginBottom: '3rem' }}>
                            {[
                                { label: '📸 Foto 3x4 / Selfie', name: 'foto3x4' },
                                { label: '🪪 RG e CPF (Frente/Verso)', name: 'rgCpf' },
                                { label: '🏠 Comprovante de Residência', name: 'comprovanteResidencia' },
                                { label: '📜 Certificado Ensino Médio', name: 'certificadoEM' },
                                { label: '📑 Histórico Ensino Médio', name: 'historicoEM' },
                                { label: '📊 Boletim ENEM/SISU (Opc)', name: 'enemSisu' },
                                { label: '🎓 Diploma Anterior (Opc)', name: 'diplomaAnt' },
                                { label: '🩺 Laudo Médico (Cotas)', name: 'laudoMedico' },
                            ].map(f => (
                                <div key={f.name} style={{ position: 'relative' }}>
                                    <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.85rem' }}>{f.label}</label>
                                    <div style={{ position: 'relative', height: '50px', backgroundColor: 'rgba(255,255,255,0.03)', border: '2px dashed rgba(255,255,255,0.1)', borderRadius: '12px', display: 'flex', alignItems: 'center', padding: '0 1rem', overflow: 'hidden' }}>
                                        <input
                                            type="file" name={f.name} onChange={handleFileChange}
                                            style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', zIndex: 2 }}
                                        />
                                        <Upload size={16} style={{ marginRight: '0.75rem', opacity: 0.5 }} />
                                        <span style={{ fontSize: '0.8rem', opacity: 0.7, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                            {(files as any)[f.name] ? (files as any)[f.name].name : 'Selecionar arquivo...'}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div style={{ display: 'flex', gap: '1rem', borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '2rem' }}>
                            <button 
                                type="submit" 
                                disabled={submitting} 
                                style={{ 
                                    padding: '1rem 2.5rem', borderRadius: '12px', 
                                    backgroundColor: 'var(--secondary-color)', color: '#000', 
                                    border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', 
                                    fontWeight: 800, fontSize: '1rem', 
                                    boxShadow: '0 10px 15px -3px rgba(139, 92, 246, 0.3)',
                                    opacity: submitting ? 0.7 : 1
                                }}
                            >
                                {submitting ? 'Processando...' : 'Finalizar Matrícula'}
                            </button>
                            <button 
                                type="button" 
                                onClick={() => setShowForm(false)}
                                style={{ padding: '1rem 2rem', borderRadius: '12px', backgroundColor: 'transparent', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', cursor: 'pointer', fontWeight: 600 }}
                            >
                                Cancelar
                            </button>
                        </div>
                    </form>
                </div>
            )}

            {/* Listagem de Alunos */}
            <div style={{ backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '24px', overflow: 'hidden' }}>
                <div style={{ padding: '1.5rem 2rem', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.01)' }}>
                    <h2 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Alunos Registrados ({students.length})</h2>
                </div>

                {isLoading ? (
                    <div style={{ padding: '5rem', textAlign: 'center' }}>
                        <div style={{ width: '40px', height: '40px', border: '3px solid rgba(255,255,255,0.1)', borderTopColor: 'var(--secondary-color)', borderRadius: '50%', margin: '0 auto 1rem', animation: 'spin 1s linear infinite' }} />
                        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
                        <p style={{ opacity: 0.5 }}>Carregando registros...</p>
                    </div>
                ) : students.length === 0 ? (
                    <div style={{ padding: '5rem', textAlign: 'center', opacity: 0.5 }}>
                        <GraduationCap size={48} style={{ margin: '0 auto 1rem', opacity: 0.2 }} />
                        <p style={{ fontSize: '1.1rem' }}>Nenhuma matrícula encontrada na graduação.</p>
                        <p style={{ fontSize: '0.9rem', marginTop: '0.5rem' }}>Use o botão "Nova Inscrição" para matricular um aluno.</p>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', color: 'rgba(255,255,255,0.4)', fontSize: '0.85rem' }}>
                                    {['Aluno', 'Contato', 'CPF', 'Curso / Ingresso', 'Docs', 'Status', 'Ações'].map(h => (
                                        <th key={h} style={{ padding: '1.25rem 1.5rem', textAlign: 'left', fontWeight: 600 }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody style={{ fontSize: '0.95rem' }}>
                                {students.map((s, i) => (
                                    <tr key={s.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)', transition: 'background-color 0.2s' }}>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <div style={{ fontWeight: 700, color: '#fff' }}>{s.fullName}</div>
                                            <div style={{ fontSize: '0.75rem', color: 'rgba(255,255,255,0.3)', marginTop: '0.2rem' }}>{s.registrationNumber}</div>
                                        </td>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <div style={{ color: 'rgba(255,255,255,0.7)' }}>{s.email}</div>
                                            <div style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.4)' }}>{s.phone}</div>
                                        </td>
                                        <td style={{ padding: '1.25rem 1.5rem', color: 'rgba(255,255,255,0.7)' }}>{s.cpf}</td>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <div style={{ fontWeight: 500, color: 'var(--secondary-color)' }}>{s.currentCourse}</div>
                                            <div style={{ fontSize: '0.8rem', opacity: 0.5 }}>{(s as any).formaIngresso || '-'}</div>
                                        </td>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <div style={{ display: 'flex', gap: '6px' }}>
                                                {s.documents?.length > 0 ? (
                                                    <span style={{ backgroundColor: 'rgba(46,204,113,0.1)', color: '#2ecc71', padding: '2px 8px', borderRadius: '20px', fontSize: '0.7rem', fontWeight: 700 }}>
                                                        {s.documents.length} ANEXOS
                                                    </span>
                                                ) : (
                                                    <span style={{ backgroundColor: 'rgba(231,76,60,0.1)', color: '#e74c3c', padding: '2px 8px', borderRadius: '20px', fontSize: '0.7rem', fontWeight: 700 }}>
                                                        SEM DOCS
                                                    </span>
                                                )}
                                            </div>
                                        </td>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <select
                                                value={s.enrollmentStatus}
                                                onChange={(e) => handleStatusChange(s.id, e.target.value)}
                                                style={{ 
                                                    padding: '6px 12px', borderRadius: '8px', border: 'none', 
                                                    backgroundColor: (STATUS_COLORS[s.enrollmentStatus] || '#7f8c8d') + '22', 
                                                    color: STATUS_COLORS[s.enrollmentStatus] || '#7f8c8d', 
                                                    fontWeight: 700, cursor: 'pointer', fontSize: '0.8rem' 
                                                }}
                                            >
                                                <option value="PENDENTE_VALIDACAO">EM ANÁLISE</option>
                                                <option value="APROVADO">APROVADO</option>
                                                <option value="REJEITADO">REJEITADO</option>
                                            </select>
                                        </td>
                                        <td style={{ padding: '1.25rem 1.5rem' }}>
                                            <button
                                                onClick={() => handleDelete(s.id)}
                                                style={{ padding: '0.5rem', backgroundColor: 'transparent', color: 'rgba(231,76,60,0.6)', border: 'none', cursor: 'pointer', borderRadius: '8px', transition: 'all 0.2s' }}
                                                onMouseOver={(e) => e.currentTarget.style.color = '#e74c3c'}
                                                onMouseOut={(e) => e.currentTarget.style.color = 'rgba(231,76,60,0.6)'}
                                            >
                                                <Trash2 size={18} />
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
