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
  GraduationCap
} from 'lucide-react';
import { toast } from "sonner";
import PhotoUpload3x4 from "@/components/PhotoUpload3x4";
import { useAuditCRUD } from '@/app/hooks/useAuditCRUD';
import { AuditStamp } from '@/app/components/AuditStamp';

    status: string;
    rejectionReason?: string;
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
    fotoMatricula?: string;
    registrationDate?: string;
    creatorName?: string;
    creatorPosition?: string;
    creatorPhotoUrl?: string;
}

const STATUS_COLORS: Record<string, string> = {
    PENDENTE: '#f0a500',
    PENDENTE_VALIDACAO: '#f0a500',
    APROVADO: '#2ecc71',
    REJEITADO: '#e74c3c',
};

export default function GraduationPage() {
    const [students, setStudents] = useState<GradStudent[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [editingStudent, setEditingStudent] = useState<GradStudent | null>(null);
    const [viewingStudent, setViewingStudent] = useState<GradStudent | null>(null);
    const [showForm, setShowForm] = useState(false);

    // Contexto SUPREME
    const gradService = useAuditCRUD('v1/grad-students');
    const isLoading = gradService.loading;

    const loadStudents = async () => {
        const data = await gradService.list();
        setStudents(data);
    };

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

    // useEffect automático pelo useAuditCRUD

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

    const handleUpdate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingStudent) return;
        setSubmitting(true);
        try {
            const form = new FormData();
            form.append('fullName', editingStudent.fullName);
            form.append('phone', editingStudent.phone || "");
            form.append('address', editingStudent.address || "");
            form.append('currentCourse', editingStudent.currentCourse);
            form.append('enrollmentStatus', editingStudent.enrollmentStatus);
            
            if (files.foto3x4_update) {
                form.append('foto3x4', files.foto3x4_update);
            }

            await api.put(`v1/grad-students/${editingStudent.id}`, form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            
            toast.success('✅ Dados atualizados com sucesso!');
            setEditingStudent(null);
            // Limpar arquivo de update após sucesso
            setFiles(prev => ({ ...prev, foto3x4_update: null }));
            loadStudents();
        } catch (e: any) { 
            toast.error(e?.response?.data?.message || 'Erro ao atualizar registro.'); 
        } finally { 
            setSubmitting(false); 
        }
    };

    const handleDocumentAudit = async (docId: number, status: string) => {
        try {
            await api.patch(`v1/grad-students/documents/${docId}/status?status=${status}`);
            toast.success(`Documento ${status === 'APPROVED' ? 'aprovado' : 'rejeitado'}!`);
            // Recarregar os dados do aluno atual no modal
            if (viewingStudent) {
                const updated = await api.get(`v1/grad-students/${viewingStudent.id}`);
                setViewingStudent(updated.data);
                loadStudents();
            }
        } catch (e) {
            toast.error('Erro ao processar auditoria do documento.');
        }
    };

    const handleGenerateFee = async (studentId: number) => {
        try {
            const res = await api.post(`v1/grad-students/${studentId}/generate-fee`);
            toast.success(res.data.message);
            // Opcional: recarregar dados
        } catch (e: any) {
            toast.error(e?.response?.data?.message || 'Erro ao gerar taxa.');
        }
    };

    const handlePrintEnrollment = () => {
        window.print();
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
            await api.patch(`v1/grad-students/${id}/status?status=${status}`);
            loadStudents();
            toast.success('Status atualizado!');
        } catch (e) { toast.error('Erro ao atualizar status.'); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Tem certeza que deseja remover este aluno? Esta operação é um Soft Delete (exclusão lógica).')) return;
        try {
            await gradService.remove(id);
            loadStudents();
        } catch (e) { /* toast handled by hook */ }
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
                                    {['Aluno / Matrícula', 'Contato', 'Auditoria', 'Curso / Ingresso', 'Documentação', 'Status', 'Ações'].map(h => (
                                        <th key={h} style={{ padding: '1.5rem 2rem', fontWeight: 700 }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {students.map((s, i) => (
                                    <tr key={s.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)', backgroundColor: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.01)' }}>
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                                                {s.fotoMatricula ? (
                                                    <img 
                                                        src={`${BASE_URL}/uploads/${s.fotoMatricula}`} 
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
                                        <td style={{ padding: '1.5rem 2rem' }}>
                                            <AuditStamp 
                                                name={s.creatorName}
                                                position={s.creatorPosition}
                                                photoUrl={s.creatorPhotoUrl ? `${BASE_URL}/uploads/${s.creatorPhotoUrl}` : undefined}
                                                date={s.registrationDate}
                                            />
                                        </td>
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
                                            <div style={{ display: 'flex', gap: '8px' }}>
                                                <button
                                                    onClick={() => setViewingStudent(s)}
                                                    style={{ padding: '0.6rem', background: 'rgba(255,255,255,0.05)', color: '#fff', borderRadius: '8px', border: 'none', cursor: 'pointer' }}
                                                    title="Ver Ficha Detalhada"
                                                >
                                                    <User size={18} />
                                                </button>
                                                <button
                                                    onClick={() => setEditingStudent(s)}
                                                    style={{ padding: '0.6rem', background: 'rgba(59,130,246,0.1)', color: '#3b82f6', borderRadius: '8px', border: 'none', cursor: 'pointer' }}
                                                    title="Editar Prontuário"
                                                >
                                                    <FileText size={18} />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(s.id)}
                                                    style={{ padding: '0.6rem', background: 'rgba(231,76,60,0.1)', color: '#e74c3c', borderRadius: '8px', border: 'none', cursor: 'pointer' }}
                                                    title="Inativar Matrícula"
                                                >
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
            {/* MODAL: Edição de Aluno (CRUD Universal) */}
            {editingStudent && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
                    <div className="glass-panel fade-in" style={{ padding: '2.5rem', width: '100%', maxWidth: '650px', backgroundColor: '#0f172a', border: '1px solid rgba(255,255,255,0.1)' }}>
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
                                <input value={editingStudent.currentCourse} onChange={e => setEditingStudent({...editingStudent, currentCourse: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '10px', background: '#1e293b', border: '1px solid #334155', color: '#fff' }} />
                            </div>
                             
                            <div style={{ gridColumn: 'span 2' }}>
                                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '5px' }}>Nova Foto 3x4 (Opcional)</label>
                                <input type="file" accept="image/*" onChange={e => setFiles({...files, foto3x4_update: e.target.files?.[0]})} style={{ width: '100%', padding: '10px', borderRadius: '10px', background: 'rgba(255,255,255,0.05)', border: '1px dashed rgba(255,255,255,0.2)', color: '#fff' }} />
                            </div>

                            <div style={{ gridColumn: 'span 2', display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                                <button type="button" onClick={() => setEditingStudent(null)} style={{ padding: '12px 25px', borderRadius: '10px', background: 'rgba(255,255,255,0.05)', color: '#fff' }}>Cancelar</button>
                                <button type="submit" disabled={submitting} style={{ padding: '12px 30px', borderRadius: '10px', background: 'var(--secondary-color)', color: '#000', fontWeight: 800 }}>
                                    {submitting ? 'Salvando...' : 'Salvar Alterações'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* TEMPLATE DE IMPRESSÃO (Oculto na UI, Visível no Print) */}
            <div id="print-area" style={{ display: 'none' }}>
                {viewingStudent && (
                    <div style={{ padding: '40px', color: '#000', backgroundColor: '#fff', minHeight: '100vh', fontFamily: 'serif' }}>
                        <div style={{ textAlign: 'center', borderBottom: '2px solid #000', paddingBottom: '20px', marginBottom: '30px' }}>
                            <h1 style={{ margin: 0, fontSize: '24px' }}>PORTAL CURSOS NG - SISTEMA ACADÊMICO</h1>
                            <p style={{ margin: '5px 0', fontSize: '14px' }}>Atestado de Matrícula Oficial - Protocolo V30.9-SUPREME</p>
                        </div>
                        
                        <div style={{ display: 'flex', gap: '30px', marginBottom: '30px' }}>
                            <div style={{ width: '120px', height: '160px', border: '1px solid #ddd', overflow: 'hidden' }}>
                                {viewingStudent.fotoMatricula && (
                                    <img src={`${BASE_URL}/uploads/${viewingStudent.fotoMatricula}`} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                )}
                            </div>
                            <div style={{ flexGrow: 1 }}>
                                <h2 style={{ fontSize: '20px', marginBottom: '15px' }}>DADOS DO DISCENTE</h2>
                                <p><strong>NOME:</strong> {viewingStudent.fullName}</p>
                                <p><strong>RA (MATRÍCULA):</strong> {viewingStudent.registrationNumber}</p>
                                <p><strong>CPF:</strong> {viewingStudent.cpf}</p>
                                <p><strong>CURSO:</strong> {viewingStudent.currentCourse}</p>
                            </div>
                        </div>

                        <div style={{ marginBottom: '40px' }}>
                            <h2 style={{ fontSize: '20px', marginBottom: '15px' }}>SITUAÇÃO ACADÊMICA</h2>
                            <p>Declaramos para os devidos fins que o aluno acima citado encontra-se regularmente <strong>{(viewingStudent.enrollmentStatus === 'APROVADO' ? 'MATRICULADO' : 'EM PROCESSO DE ADMISSÃO')}</strong> nesta instituição de ensino.</p>
                        </div>

                        <div style={{ marginTop: '100px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                            <div style={{ textAlign: 'center', width: '250px' }}>
                                <div style={{ borderBottom: '1px solid #000', marginBottom: '5px' }}></div>
                                <p style={{ fontSize: '12px' }}>Assinatura do Aluno</p>
                            </div>
                            <div style={{ textAlign: 'center', width: '250px' }}>
                                <div style={{ marginBottom: '5px', fontWeight: 'bold' }}>{viewingStudent.creatorName || 'Secretaria Acadêmica'}</div>
                                <div style={{ borderBottom: '1px solid #000', marginBottom: '5px' }}></div>
                                <p style={{ fontSize: '12px' }}>{viewingStudent.creatorPosition || 'Responsável pelo Registro'}</p>
                            </div>
                        </div>

                        <div style={{ position: 'absolute', bottom: '40px', left: '40px', right: '40px', fontSize: '10px', color: '#666', textAlign: 'center', borderTop: '1px solid #eee', paddingTop: '10px' }}>
                            Este atestado foi gerado digitalmente em {new Date().toLocaleString('pt-BR')} e possui validade de 30 dias.
                            <br/>Código de Verificação: {viewingStudent.registrationNumber}-{new Date().getTime()}
                        </div>
                    </div>
                )}
            </div>

            <style jsx global>{`
                @media print {
                    body * { visibility: hidden; }
                    #print-area, #print-area * { visibility: visible; }
                    #print-area { 
                        position: absolute; 
                        left: 0; 
                        top: 0; 
                        width: 100%; 
                        display: block !important;
                    }
                    .no-print { display: none !important; }
                }
            `}</style>

            {/* MODAL: Visualização Detalhada (Procedimento R) */}
            {viewingStudent && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(12px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
                    <div style={{ width: '100%', maxWidth: '850px', backgroundColor: '#0a0f1e', borderRadius: '32px', border: '1px solid rgba(255,255,255,0.1)', overflow: 'hidden' }}>
                        <div style={{ padding: '2.5rem', display: 'flex', gap: '2.5rem' }}>
                            {/* Lateral: Foto e Matrícula */}
                            <div style={{ width: '220px', flexShrink: 0 }}>
                                <div style={{ width: '80px', height: '80px', borderRadius: '16px', overflow: 'hidden', background: 'rgba(255,255,255,0.05)', border: '2px solid var(--secondary-color)', marginBottom: '1.5rem' }}>
                                    {viewingStudent.fotoMatricula ? (
                                        <img src={`${BASE_URL}/uploads/${viewingStudent.fotoMatricula}`} alt="Foto 3x4" style={{ width: '100%', height: '100%', objectPosition: 'center', objectFit: 'cover' }} />
                                    ) : (
                                        <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.3 }}><User size={30} /></div>
                                    )}
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                    <div style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.4)', textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: '0.4rem' }}>Registro Acadêmico</div>
                                    <div style={{ fontWeight: 800, color: 'var(--secondary-color)', fontSize: '1.2rem' }}>{viewingStudent.registrationNumber}</div>
                                </div>
                            </div>

                            {/* Conteúdo Principal */}
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
                                        <h4 style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                            <GraduationCap size={14} /> Acadêmico
                                        </h4>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.2rem' }}>
                                            <div><div style={{ fontSize: '0.75rem', opacity: 0.5 }}>Curso</div><div style={{ fontWeight: 600, color: 'var(--secondary-color)' }}>{viewingStudent.currentCourse}</div></div>
                                            <div><div style={{ fontSize: '0.75rem', opacity: 0.5 }}>Forma de Ingresso</div><div style={{ fontWeight: 500 }}>{viewingStudent.formaIngresso}</div></div>
                                            <div><div style={{ fontSize: '0.75rem', opacity: 0.5 }}>Data de Cadastro</div><div style={{ fontWeight: 500 }}>{viewingStudent.registrationDate ? new Date(viewingStudent.registrationDate).toLocaleDateString('pt-BR') : '---'}</div></div>
                                        </div>

                                        <div style={{ marginTop: '2rem' }}>
                                            <h4 style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '1rem' }}>Ações de Processo</h4>
                                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.8rem' }}>
                                                <button 
                                                    onClick={() => handleGenerateFee(viewingStudent.id)}
                                                    style={{ padding: '0.6rem 1rem', borderRadius: '8px', background: 'rgba(59,130,246,0.1)', color: '#3b82f6', border: '1px solid rgba(59,130,246,0.2)', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer' }}
                                                >
                                                    💸 Gerar Taxa Matrícula
                                                </button>
                                                <button 
                                                    onClick={handlePrintEnrollment}
                                                    style={{ padding: '0.6rem 1rem', borderRadius: '8px', background: 'rgba(255,255,255,0.05)', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer' }}
                                                >
                                                    🖨️ Imprimir Atestado
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    <div>
                                        <h4 style={{ color: 'rgba(255,255,255,0.3)', fontSize: '0.75rem', textTransform: 'uppercase', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                            <FileText size={14} /> Auditoria de Documentos
                                        </h4>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem', maxHeight: '300px', overflowY: 'auto', paddingRight: '0.5rem' }}>
                                            {viewingStudent.documents && viewingStudent.documents.length > 0 ? (
                                                viewingStudent.documents.map((doc) => (
                                                    <div key={doc.id} style={{ padding: '0.8rem', borderRadius: '12px', background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                                                            <div style={{ color: doc.status === 'APPROVED' ? '#2ecc71' : doc.status === 'REJECTED' ? '#e74c3c' : '#f0a500' }}>
                                                                <FileText size={18} />
                                                            </div>
                                                            <div>
                                                                <div style={{ fontSize: '0.85rem', fontWeight: 500 }}>{doc.documentType}</div>
                                                                <div style={{ fontSize: '0.7rem', opacity: 0.4 }}>Status: {doc.status}</div>
                                                            </div>
                                                        </div>
                                                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                                                            <a href={`${BASE_URL}/uploads/grad-students/${doc.filePath}`} target="_blank" rel="noreferrer" style={{ width: '32px', height: '32px', borderRadius: '6px', background: 'rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }} title="Ver Arquivo">
                                                                <Upload size={14} style={{ transform: 'rotate(180deg)' }} />
                                                            </a>
                                                            <button 
                                                                onClick={() => handleDocumentAudit(doc.id, 'APPROVED')}
                                                                style={{ width: '32px', height: '32px', borderRadius: '6px', background: doc.status === 'APPROVED' ? '#2ecc71' : 'rgba(46,204,113,0.1)', color: doc.status === 'APPROVED' ? '#000' : '#2ecc71', border: 'none', cursor: 'pointer' }}
                                                                title="Aprovar"
                                                            >
                                                                ✓
                                                            </button>
                                                            <button 
                                                                onClick={() => handleDocumentAudit(doc.id, 'REJECTED')}
                                                                style={{ width: '32px', height: '32px', borderRadius: '6px', background: doc.status === 'REJECTED' ? '#e74c3c' : 'rgba(231,76,60,0.1)', color: doc.status === 'REJECTED' ? '#fff' : '#e74c3c', border: 'none', cursor: 'pointer' }}
                                                                title="Rejeitar"
                                                            >
                                                                ✗
                                                            </button>
                                                        </div>
                                                    </div>
                                                ))
                                            ) : <div style={{ opacity: 0.3, fontSize: '0.8rem', textAlign: 'center', padding: '1rem' }}>Nenhum documento anexado.</div>}
                                        </div>
                                    </div>
                                </div>

                                {/* Auditoria SUPREME */}
                                <div style={{ marginTop: '3rem', padding: '1.5rem', borderRadius: '24px', backgroundColor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                                    <div style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.3)', textTransform: 'uppercase', marginBottom: '1rem', letterSpacing: '0.05em' }}>Auditado por Protocolo V30.9-SUPREME</div>
                                    <AuditStamp 
                                        name={viewingStudent.creatorName}
                                        position={viewingStudent.creatorPosition}
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
