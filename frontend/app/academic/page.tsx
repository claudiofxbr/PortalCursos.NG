'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '@/app/services/api';
import { 
  User, 
  FileText, 
  Plus, 
  X, 
  Upload, 
  Trash2, 
  GraduationCap,
  LayoutDashboard,
  CheckCircle2,
  Clock,
  AlertCircle
import { toast } from "sonner";
import PhotoUpload3x4 from "@/components/PhotoUpload3x4";

interface StudentDocument {
    id: number;
    documentType: string;
    filePath: string;
    status: string;
}

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
    formaIngresso: string;
    tipoCota: string;
    documents: StudentDocument[];
}

const STATUS_COLORS: Record<string, string> = {
    PENDENTE: '#f0a500',
    PENDENTE_VALIDACAO: '#f0a500',
    APROVADO: '#2ecc71',
    REJEITADO: '#e74c3c',
};

export default function GraduationPage() {
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
        laudoMedico: null, rnmRne: null, tituloEleitorFile: null, reservistaFile: null,
        certidaoNascimentoFile: null, autodeclaracaoRacialFile: null
    });

    const loadStudents = async () => {
        const { isAuthenticated } = (window as any).authContext || {}; // Fallback se o contexto não estiver exposto globalmente, mas idealmente deve vir de um hook customizado
        
        // No PortalCursos, o AuthContext provê o estado via hook, mas se estivermos num componente funcional sem o hook invocado, vamos garantir a segurança.
        // Assumindo que o componente GraduationPage será envolvido pelo AppShell que já expõe o AuthContext.
        
        setIsLoading(true);
        try {
            const res = await api.get('v1/grad-students');
            setStudents(res.data);
        } catch (e: any) {
            if (e.silent) return; // Silenciar erros de logout
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

    const handlePhotoChange = (file: File | null) => {
        setFiles(prev => ({ ...prev, foto3x4: file }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);

        const form = new FormData();
        Object.entries(formData).forEach(([key, value]) => {
            if (value !== undefined && value !== null) form.append(key, value.toString());
        });
        Object.entries(files).forEach(([key, file]) => {
            if (file) form.append(key, file);
        });

        try {
            await api.post('v1/grad-students/enroll', form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            toast.success('✅ Matrícula realizada com sucesso!');
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
            laudoMedico: null, rnmRne: null, tituloEleitorFile: null, reservistaFile: null,
            certidaoNascimentoFile: null, autodeclaracaoRacialFile: null
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
        <div style={{ maxWidth: '1280px', margin: '0 auto' }}>
            {/* Header Unificado */}
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem' }}>
                <div>
                    <h1 style={{ fontSize: '2.4rem', fontWeight: 800, letterSpacing: '-0.025em', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                        <GraduationCap size={42} className="text-blue-400" />
                        Matrícula de Graduação
                    </h1>
                    <p style={{ opacity: 0.5, marginTop: '0.4rem', fontSize: '1.1rem' }}>Controle acadêmico e gestão de ingressantes</p>
                </div>
                <button
                    onClick={() => setShowForm(!showForm)}
                    style={{
                        padding: '0.8rem 1.6rem', borderRadius: '12px',
                        backgroundColor: showForm ? 'rgba(255,255,255,0.05)' : 'var(--secondary-color)', 
                        color: showForm ? '#fff' : '#000',
                        border: showForm ? '1px solid rgba(255,255,255,0.1)' : 'none', 
                        cursor: 'pointer', fontWeight: 700,
                        fontSize: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem',
                        transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)'
                    }}
                >
                    {showForm ? <X size={20} /> : <Plus size={20} />}
                    {showForm ? 'Cancelar Cadastro' : 'Novo Aluno'}
                </button>
            </header>

            {/* Form Toggle Selection (Idêntico ao postgrad) */}
            {showForm && (
                <div style={{ 
                    backgroundColor: 'rgba(255,255,255,0.02)', 
                    border: '1px solid rgba(255,255,255,0.08)', 
                    borderRadius: '24px', 
                    padding: '2.5rem', 
                    marginBottom: '3rem', 
                    backdropFilter: 'blur(10px)',
                    animation: 'fadeIn 0.3s ease-out'
                }}>
                    <style>{`@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }`}</style>
                    <form onSubmit={handleSubmit}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '2rem' }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: 'var(--secondary-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#000' }}>
                                <User size={20} />
                            </div>
                            <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Dados de Inscrição</h2>
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
                                        style={{ width: '100%', padding: '0.9rem 1.1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.03)', color: '#fff', fontSize: '1rem', outline: 'none' }}
                                    />
                                </div>
                            ))}
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.9rem', fontWeight: 500 }}>Forma de Ingresso *</label>
                                <select name="formaIngresso" value={formData.formaIngresso} onChange={handleInputChange} style={{ width: '100%', padding: '0.9rem 1.1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: '1rem', outline: 'none' }}>
                                    <option value="VESTIBULAR_PROPRIO">Vestibular Próprio</option>
                                    <option value="ENEM_SISU">ENEM / SISU</option>
                                    <option value="GRAD_2">2ª Graduação / Portador de Diploma</option>
                                    <option value="TRANSFERENCIA">Transferência Externa</option>
                                </select>
                            </div>
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.6)', fontSize: '0.9rem', fontWeight: 500 }}>Curso Escolhido *</label>
                                <input 
                                    name="currentCourse" required value={formData.currentCourse} onChange={handleInputChange}
                                    placeholder="Ex: Ciência da Computação"
                                    style={{ width: '100%', padding: '0.9rem 1.1rem', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: '1rem', outline: 'none' }}
                                />
                            </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '2rem', marginTop: '3rem' }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: '#3b82f6', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
                                <FileText size={20} />
                            </div>
                            <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Checklist de Documentos</h2>
                        </div>

                        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '2rem' }}>
                            <PhotoUpload3x4 onPhotoSelected={handlePhotoChange} label="Foto Acadêmica 3x4" />
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '1.5rem', marginBottom: '3rem' }}>
                            {[
                                { label: '🪪 RG e CPF (Unificado)', name: 'rgCpf' },
                                { label: '🏠 Comprovante Residência', name: 'comprovanteResidencia' },
                                { label: '📜 Certificado Ensino Médio', name: 'certificadoEM' },
                                { label: '📑 Histórico Ensino Médio', name: 'historicoEM' },
                                { label: '🗳️ Título de Eleitor', name: 'tituloEleitorFile' },
                                { label: '🎖️ Certificado Reservista', name: 'reservistaFile' },
                                { label: '💍 Certidão Nascimento/Casamento', name: 'certidaoNascimentoFile' },
                                { label: '🏷️ Autodeclaração Racial (Cota)', name: 'autodeclaracaoRacialFile' },
                                { label: '🩺 Laudo Médico (Opc)', name: 'laudoMedico' },
                            ].map(f => (
                                <div key={f.name}>
                                    <label style={{ display: 'block', marginBottom: '0.6rem', color: 'rgba(255,255,255,0.5)', fontSize: '0.85rem' }}>{f.label}</label>
                                    <div style={{ position: 'relative', height: '54px', border: '2px dashed rgba(255,255,255,0.1)', borderRadius: '14px', display: 'flex', alignItems: 'center', padding: '0 1rem', overflow: 'hidden', cursor: 'pointer' }}>
                                        <input
                                            type="file" name={f.name} onChange={handleFileChange}
                                            style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', zIndex: 2 }}
                                        />
                                        <Upload size={16} style={{ marginRight: '0.75rem', opacity: 0.4 }} />
                                        <span style={{ fontSize: '0.8rem', opacity: 0.6, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                            {(files as any)[f.name] ? (files as any)[f.name].name : 'Selecionar arquivo...'}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div style={{ display: 'flex', gap: '1rem', borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: '2.5rem' }}>
                            <button type="submit" disabled={submitting} style={{ padding: '1.1rem 3rem', borderRadius: '14px', backgroundColor: 'var(--secondary-color)', color: '#000', border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 800, fontSize: '1.05rem', opacity: submitting ? 0.7 : 1 }}>
                                {submitting ? 'Enviando...' : 'Finalizar Matrícula'}
                            </button>
                            <button type="button" onClick={() => setShowForm(false)} style={{ padding: '1.1rem 2rem', borderRadius: '14px', backgroundColor: 'rgba(255,255,255,0.05)', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', cursor: 'pointer', fontWeight: 600 }}>
                                Fechar
                            </button>
                        </div>
                    </form>
                </div>
            )}

            {/* Listagem Estilo Unificado */}
            <div style={{ backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '24px', overflow: 'hidden' }}>
                <div style={{ padding: '2rem', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h3 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Candidatos da Graduação ({students.length})</h3>
                    <div style={{ display: 'flex', gap: '1rem' }}>
                        <div style={{ padding: '0.5rem 1rem', borderRadius: '20px', backgroundColor: 'rgba(46,204,113,0.1)', color: '#2ecc71', fontSize: '0.75rem', fontWeight: 700 }}>
                            ● {students.filter(s => s.enrollmentStatus === 'APROVADO').length} APROVADOS
                        </div>
                        <div style={{ padding: '0.5rem 1rem', borderRadius: '20px', backgroundColor: 'rgba(240,165,0,0.1)', color: '#f0a500', fontSize: '0.75rem', fontWeight: 700 }}>
                            ● {students.filter(s => s.enrollmentStatus.startsWith('PENDENTE')).length} PENDENTES
                        </div>
                    </div>
                </div>

                {isLoading ? (
                    <div style={{ padding: '8rem', textAlign: 'center', opacity: 0.5 }}>⏳ Sincronizando dados acadêmicos...</div>
                ) : students.length === 0 ? (
                    <div style={{ padding: '8rem', textAlign: 'center' }}>
                        <p style={{ opacity: 0.3, fontSize: '1.2rem' }}>Nenhum aluno matriculado na graduação.</p>
                        <p style={{ opacity: 0.2, fontSize: '0.9rem', marginTop: '1rem' }}>Clique em "Novo Aluno" para iniciar um processo de matrícula.</p>
                    </div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', textAlign: 'left', color: 'rgba(255,255,255,0.4)', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                    {['Aluno / Matrícula', 'Contato', 'CPF', 'Curso / Ingresso', 'Documentação', 'Status', 'Ações'].map(h => (
                                        <th key={h} style={{ padding: '1.5rem 2rem', fontWeight: 700 }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {students.map((s, i) => (
                                    <tr key={s.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)', backgroundColor: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.01)' }}>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                                                {(s as any).fotoUrl ? (
                                                    <img 
                                                        src={`${BASE_URL}/uploads/fotos-perfil/${(s as any).fotoUrl}`} 
                                                        alt={s.fullName} 
                                                        style={{ width: '40px', height: '53px', borderRadius: '4px', objectFit: 'cover', border: '1px solid rgba(255,255,255,0.1)' }}
                                                    />
                                                ) : (
                                                    <div style={{ width: '40px', height: '53px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                                        <User size={20} opacity={0.3} />
                                                    </div>
                                                )}
                                                <div>
                                                    <div style={{ fontWeight: 700, fontSize: '1rem' }}>{s.fullName}</div>
                                                    <div style={{ fontSize: '0.75rem', opacity: 0.4, marginTop: '0.2rem' }}>{s.registrationNumber}</div>
                                                </div>
                                            </div>
                                        </td>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <div style={{ opacity: 0.8 }}>{s.email}</div>
                                            <div style={{ fontSize: '0.8rem', opacity: 0.4 }}>{s.phone}</div>
                                        </td>
                                        <td style={{ padding: '1.5rem 2rem', opacity: 0.8 }}>{s.cpf}</td>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <div style={{ fontWeight: 600, color: 'var(--secondary-color)' }}>{s.currentCourse}</div>
                                            <div style={{ fontSize: '0.75rem', opacity: 0.4 }}>{s.formaIngresso}</div>
                                        </td>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <div style={{ display: 'flex', gap: '4px' }}>
                                                {s.documents && s.documents.length > 0 ? (
                                                    s.documents.map((doc, idx) => (
                                                        <a 
                                                            key={doc.id}
                                                            href={`${BASE_URL}/uploads/grad-students/${doc.filePath}`}
                                                            target="_blank"
                                                            rel="noopener noreferrer"
                                                            style={{ width: '24px', height: '24px', borderRadius: '6px', backgroundColor: 'rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', textDecoration: 'none', color: doc.status === 'APPROVED' ? '#2ecc71' : '#f0a500' }}
                                                            title={doc.documentType}
                                                        >
                                                            <FileText size={12} />
                                                        </a>
                                                    ))
                                                ) : <span style={{ fontSize: '0.7rem', opacity: 0.2 }}>NENHUM</span>}
                                            </div>
                                        </td>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <select
                                                value={s.enrollmentStatus}
                                                onChange={(e) => handleStatusChange(s.id, e.target.value)}
                                                style={{ 
                                                    padding: '6px 12px', borderRadius: '10px', border: 'none', 
                                                    backgroundColor: (STATUS_COLORS[s.enrollmentStatus] || '#7f8c8d') + '15', 
                                                    color: STATUS_COLORS[s.enrollmentStatus] || '#7f8c8d', 
                                                    fontWeight: 700, cursor: 'pointer', fontSize: '0.8rem' 
                                                }}
                                            >
                                                <option value="PENDENTE_VALIDACAO">PENDENTE</option>
                                                <option value="APROVADO">APROVADO</option>
                                                <option value="REJEITADO">REJEITADO</option>
                                            </select>
                                        </td>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <button
                                                onClick={() => handleDelete(s.id)}
                                                style={{ padding: '0.6rem', background: 'transparent', color: '#e74c3c', border: 'none', cursor: 'pointer', opacity: 0.5 }}
                                                onMouseOver={(e) => e.currentTarget.style.opacity = '1'}
                                                onMouseOut={(e) => e.currentTarget.style.opacity = '0.5'}
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
