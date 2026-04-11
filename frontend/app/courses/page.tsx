'use client';

import React, { useState, useEffect } from 'react';
import api from '@/app/services/api';
import { useAuth } from '@/app/context/AuthContext';

interface Course {
    id: string;
    codigoIes: string;
    denominacaoCurso: string;
    nivelPosGraduacao: 'LATO_SENSU' | 'STRICTO_SENSU' | 'GRADUACAO';
    modalidade: 'PRESENCIAL' | 'EAD';
    areaConhecimento: string;
    cargaHorariaTotal: number;
    cursoGraduacaoVinculadoId?: string;
    numeroDocumentoCriacao: string;
    dataDocumentoCriacao: string;
    dataInicioOferta: string;
    cpfCoordenador: string;
    titulacaoCoordenador: string;
    percentualDocentesStrictoSensu: number;
    isLocked: boolean;
    // Legados
    name: string;
    description: string;
    monthlyFee: number;
    durationInSemesters: number;
    totalVacancies: number;
    creatorName?: string;
    creatorPosition?: string;
    creatorPhotoUrl?: string;
}

const SECTION_STYLE = {
    backgroundColor: 'rgba(255,255,255,0.03)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '12px',
    padding: '1.5rem',
    marginBottom: '1.5rem'
};

const LABEL_STYLE = {
    display: 'block',
    marginBottom: '0.4rem',
    opacity: 0.8,
    fontSize: '0.85rem',
    fontWeight: 600
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

const SELECT_STYLE = {
    ...INPUT_STYLE,
    backgroundColor: '#1a1a2e'
};

export default function CoursesPage() {
    const { user } = useAuth();
    const [courses, setCourses] = useState<Course[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    // Form State
    const [formData, setFormData] = useState({
        codigoIes: '',
        denominacaoCurso: '',
        nivelPosGraduacao: 'LATO_SENSU',
        modalidade: 'PRESENCIAL',
        areaConhecimento: '',
        cargaHorariaTotal: '',
        cursoGraduacaoVinculadoId: '',
        numeroDocumentoCriacao: '',
        dataDocumentoCriacao: '',
        dataInicioOferta: '',
        cpfCoordenador: '',
        titulacaoCoordenador: 'MESTRE',
        percentualDocentesStrictoSensu: '0',
        description: '',
        monthlyFee: '0',
        durationInSemesters: '2',
        totalVacancies: '40'
    });

    const loadCourses = async () => {
        setIsLoading(true);
        try {
            const res = await api.get('v1/courses');
            setCourses(res.data);
        } catch (e: any) {
            setError('Erro ao carregar cursos.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadCourses(); }, []);

    // Seções Colapsáveis
    const [openSections, setOpenSections] = useState({
        institucional: true,
        autorizativo: false,
        docente: false
    });

    const toggleSection = (section: keyof typeof openSections) => {
        setOpenSections(prev => ({ ...prev, [section]: !prev[section] }));
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        setError(null);
        setSuccess(null);

        // Validação Rigorosa MEC 2026
        const horas = parseInt(formData.cargaHorariaTotal);
        if (formData.nivelPosGraduacao === 'LATO_SENSU' && horas < 360) {
            setError('Regra MEC 2026: Cursos de Pós-Graduação Lato Sensu devem possuir no mínimo 360 horas.');
            setSubmitting(false);
            return;
        }

        if (formData.nivelPosGraduacao === 'GRADUACAO' && horas < 2400) {
            setError('Aviso: Cursos de Graduação geralmente requerem carga horária superior a 2400 horas.');
        }

        try {
            await api.post('v1/courses', {
                ...formData,
                cargaHorariaTotal: horas,
                percentualDocentesStrictoSensu: parseFloat(formData.percentualDocentesStrictoSensu),
                monthlyFee: parseFloat(formData.monthlyFee),
                durationInSemesters: parseInt(formData.durationInSemesters),
                totalVacancies: parseInt(formData.totalVacancies),
            });
            setSuccess('🚀 Curso cadastrado com sucesso e registrado no sistema institucional!');
            setShowForm(false);
            loadCourses();
        } catch (e: any) {
            setError(e?.response?.data?.message || 'Erro ao cadastrar curso. Verifique se os campos estão preenchidos corretamente.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Deseja realmente remover este curso? Esta ação requer permissão de Administrador.')) return;
        try {
            await api.delete(`v1/courses/${id}`);
            loadCourses();
        } catch (e) { setError('Erro ao remover curso.'); }
    };

    return (
        <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '1rem' }}>
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '3rem' }}>
                <div>
                    <h1 style={{ fontSize: '2.2rem', fontWeight: 800, background: 'linear-gradient(45deg, #fff, #aaa)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                        🎒 Gestão do MEC - Cursos
                    </h1>
                    <p style={{ opacity: 0.5, fontSize: '0.9rem', marginTop: '0.5rem' }}>Conformidade Institucional & Diretrizes MEC</p>
                </div>
                <button
                    onClick={() => setShowForm(!showForm)}
                    style={{
                        padding: '0.8rem 1.8rem', borderRadius: '10px',
                        backgroundColor: showForm ? '#444' : 'var(--secondary-color)', color: showForm ? '#fff' : '#000',
                        border: 'none', cursor: 'pointer', fontWeight: 700,
                        transition: 'all 0.3s ease', boxShadow: '0 4px 15px rgba(0,0,0,0.2)'
                    }}
                >
                    {showForm ? '✕ Cancelar' : '+ Registrar Novo Curso'}
                </button>
            </div>

            {/* Alerts */}
            {error && <div style={{ backgroundColor: 'rgba(255, 60, 60, 0.1)', border: '1px solid #ff4444', color: '#ffaaaa', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>⚠️ {error}</div>}
            {success && <div style={{ backgroundColor: 'rgba(60, 255, 60, 0.1)', border: '1px solid #44ff44', color: '#aaffaa', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>✅ {success}</div>}

            {/* Form */}
            {showForm && (
                <form onSubmit={handleSubmit} style={{ animation: 'fadeIn 0.5s ease' }}>

                    {/* Seção 1: Dados Institucionais */}
                    <div style={SECTION_STYLE}>
                        <div
                            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
                            onClick={() => toggleSection('institucional')}
                        >
                            <h3 style={{ margin: 0, color: 'var(--secondary-color)', fontSize: '1.1rem', fontWeight: 700 }}>
                                🏛️ I. Dados Institucionais e Estrutura
                            </h3>
                            <span style={{ transform: openSections.institucional ? 'rotate(180deg)' : 'rotate(0)' }}>▼</span>
                        </div>

                        {openSections.institucional && (
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr', gap: '1rem', marginTop: '1.5rem' }}>
                                <div>
                                    <label style={LABEL_STYLE}>Cód. IES (e-MEC)</label>
                                    <input name="codigoIes" required style={INPUT_STYLE} value={formData.codigoIes} onChange={handleInputChange} placeholder="Ex: 1234" />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Denominação Oficial</label>
                                    <input name="denominacaoCurso" required style={INPUT_STYLE} value={formData.denominacaoCurso} onChange={handleInputChange} placeholder="Nome conforme aprovação interna" />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Nível Acadêmico</label>
                                    <select name="nivelPosGraduacao" style={SELECT_STYLE} value={formData.nivelPosGraduacao} onChange={handleInputChange}>
                                        <option value="LATO_SENSU">PÓS-GRADUAÇÃO LATO SENSU</option>
                                        <option value="STRICTO_SENSU">PÓS-GRADUAÇÃO STRICTO SENSU</option>
                                        <option value="GRADUACAO">GRADUAÇÃO</option>
                                    </select>
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Modalidade de Oferta</label>
                                    <select name="modalidade" style={SELECT_STYLE} value={formData.modalidade} onChange={handleInputChange}>
                                        <option value="PRESENCIAL">PRESENCIAL</option>
                                        <option value="EAD">EAD / DIGITAL</option>
                                    </select>
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Área CNPq/CAPES</label>
                                    <input name="areaConhecimento" style={INPUT_STYLE} value={formData.areaConhecimento} onChange={handleInputChange} placeholder="Ex: Ciências da Saúde" />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Carga Horária Total (h)</label>
                                    <input name="cargaHorariaTotal" type="number" required style={INPUT_STYLE} value={formData.cargaHorariaTotal} onChange={handleInputChange} />
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Seção 2: Atos Autorizativos */}
                    <div style={SECTION_STYLE}>
                        <div
                            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
                            onClick={() => toggleSection('autorizativo')}
                        >
                            <h3 style={{ margin: 0, color: 'var(--secondary-color)', fontSize: '1.1rem', fontWeight: 700 }}>
                                📜 II. Atos Autorizativos e Resoluções
                            </h3>
                            <span style={{ transform: openSections.autorizativo ? 'rotate(180deg)' : 'rotate(0)' }}>▼</span>
                        </div>

                        {openSections.autorizativo && (
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem', marginTop: '1.5rem' }}>
                                <div>
                                    <label style={LABEL_STYLE}>Nº Resolução Interna</label>
                                    <input name="numeroDocumentoCriacao" required style={INPUT_STYLE} value={formData.numeroDocumentoCriacao} onChange={handleInputChange} placeholder="Ex: RESOLUÇÃO CONSU Nº 01/2026" />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Data da Aprovação</label>
                                    <input name="dataDocumentoCriacao" type="date" required style={INPUT_STYLE} value={formData.dataDocumentoCriacao} onChange={handleInputChange} />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Início Previsto</label>
                                    <input name="dataInicioOferta" type="date" required style={INPUT_STYLE} value={formData.dataInicioOferta} onChange={handleInputChange} />
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Seção 3: Coordenação e Corpo Docente */}
                    <div style={SECTION_STYLE}>
                        <div
                            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
                            onClick={() => toggleSection('docente')}
                        >
                            <h3 style={{ margin: 0, color: 'var(--secondary-color)', fontSize: '1.1rem', fontWeight: 700 }}>
                                👨‍🏫 III. Coordenação e Corpo Docente
                            </h3>
                            <span style={{ transform: openSections.docente ? 'rotate(180deg)' : 'rotate(0)' }}>▼</span>
                        </div>

                        {openSections.docente && (
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem', marginTop: '1.5rem' }}>
                                <div>
                                    <label style={LABEL_STYLE}>CPF do Coordenador</label>
                                    <input name="cpfCoordenador" required style={INPUT_STYLE} value={formData.cpfCoordenador} onChange={handleInputChange} placeholder="000.000.000-00" />
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>Titulação Máxima</label>
                                    <select name="titulacaoCoordenador" style={SELECT_STYLE} value={formData.titulacaoCoordenador} onChange={handleInputChange}>
                                        <option value="DOUTOR">DOUTOR / PhD</option>
                                        <option value="MESTRE">MESTRE</option>
                                        <option value="ESPECIALISTA">ESPECIALISTA</option>
                                    </select>
                                </div>
                                <div>
                                    <label style={LABEL_STYLE}>% Docentes Stricto Sensu</label>
                                    <input name="percentualDocentesStrictoSensu" type="number" step="0.1" style={INPUT_STYLE} value={formData.percentualDocentesStrictoSensu} onChange={handleInputChange} />
                                </div>
                            </div>
                        )}
                    </div>

                    {/* SEÇÃO DE AUDITORIA VISUAL (EMISSOR) - RASTREABILIDADE TOTAL */}
                    <div style={{
                        ...SECTION_STYLE,
                        border: '1px solid var(--secondary-color)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '1.5rem',
                        backgroundColor: 'rgba(212, 175, 55, 0.05)',
                        marginTop: '2rem'
                    }}>
                        <div style={{
                            width: '45px',
                            height: '60px',
                            border: '2px solid var(--secondary-color)',
                            borderRadius: '4px',
                            overflow: 'hidden',
                            backgroundColor: '#000',
                            flexShrink: 0,
                            boxShadow: '0 0 10px var(--secondary-color)'
                        }}>
                            {user?.fotoUrl ? (
                                <img src={user.fotoUrl} alt="Emissor" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                            ) : (
                                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.6rem', textAlign: 'center', opacity: 0.5 }}>3x4</div>
                            )}
                        </div>
                        <div style={{ flex: 1 }}>
                            <h4 style={{ margin: '0 0 0.2rem 0', color: 'var(--secondary-color)', fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '1px', fontWeight: 800 }}>
                                Auditoria MEC (Responsável pelo Registro)
                            </h4>
                            <p style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700 }}>{user?.fullName || user?.username || 'Sessão Administrativa'}</p>
                            <p style={{ margin: '0.1rem 0 0 0', opacity: 0.8, fontSize: '0.8rem', color: 'var(--secondary-color)', fontWeight: 600 }}>{user?.position || 'COORDENAÇÃO ACADÊMICA'}</p>
                        </div>
                    </div>

                    <div style={{ textAlign: 'right', marginBottom: '3rem' }}>
                        <button type="submit" disabled={submitting} style={{ padding: '1.1rem 3.5rem', borderRadius: '12px', backgroundColor: 'var(--secondary-color)', color: '#000', border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 800, fontSize: '1rem', boxShadow: '0 8px 15px rgba(0,0,0,0.3)' }}>
                            {submitting ? '⏳ Processando Auditoria MEC...' : '💾 Finalizar e Auditar Curso'}
                        </button>
                    </div>
                </form>
            )}

            {/* List */}
            <div style={{ backgroundColor: 'rgba(255,255,255,0.02)', borderRadius: '15px', border: '1px solid rgba(255,255,255,0.05)', padding: '1.5rem' }}>
                <h2 style={{ fontSize: '1.2rem', marginBottom: '1.5rem', opacity: 0.8 }}>Cursos Registrados ({courses.length})</h2>

                {isLoading ? (
                    <div style={{ padding: '4rem', textAlign: 'center', opacity: 0.5 }}>Carregando dados...</div>
                ) : courses.length === 0 ? (
                    <div style={{ padding: '4rem', textAlign: 'center', opacity: 0.4 }}>Nenhum curso cadastrado no sistema.</div>
                ) : (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', textAlign: 'left' }}>
                                    <th style={{ padding: '1rem', opacity: 0.6, fontSize: '0.8rem' }}>DENOMINAÇÃO / CÓD. IES</th>
                                    <th style={{ padding: '1rem', opacity: 0.6, fontSize: '0.8rem' }}>NÍVEL / MODALIDADE</th>
                                    <th style={{ padding: '1rem', opacity: 0.6, fontSize: '0.8rem' }}>CARGA H.</th>
                                    <th style={{ padding: '1rem', opacity: 0.6, fontSize: '0.8rem' }}>AUDITORIA MEC</th>
                                    <th style={{ padding: '1rem', opacity: 0.6, fontSize: '0.8rem' }}>AÇÕES</th>
                                </tr>
                            </thead>
                            <tbody>
                                {courses.map(c => (
                                    <tr key={c.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', transition: 'background 0.2s' }}>
                                        <td style={{ padding: '1rem' }}>
                                            <div style={{ fontWeight: 600 }}>{c.denominacaoCurso}</div>
                                            <div style={{ fontSize: '0.75rem', opacity: 0.5 }}>IES: {c.codigoIes}</div>
                                        </td>
                                        <td style={{ padding: '1rem' }}>
                                            <span style={{ fontSize: '0.75rem', padding: '3px 8px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.1)' }}>{c.nivelPosGraduacao}</span>
                                            <div style={{ fontSize: '0.75rem', marginTop: '0.3rem', opacity: 0.7 }}>{c.modalidade}</div>
                                        </td>
                                        <td style={{ padding: '1rem' }}>{c.cargaHorariaTotal}h</td>
                                        <td style={{ padding: '1rem' }}>
                                            {c.creatorName ? (
                                                <div style={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '8px',
                                                    padding: '5px 10px',
                                                    backgroundColor: 'rgba(212, 175, 55, 0.05)',
                                                    borderRadius: '6px',
                                                    border: '1px solid rgba(212, 175, 55, 0.1)',
                                                    width: 'fit-content'
                                                }}>
                                                    <div style={{
                                                        width: '24px',
                                                        height: '32px',
                                                        border: '1.5px solid var(--secondary-color)',
                                                        borderRadius: '2px',
                                                        overflow: 'hidden'
                                                    }}>
                                                        <img
                                                            src={c.creatorPhotoUrl ? c.creatorPhotoUrl : '/placeholder-user.png'}
                                                            alt={c.creatorName}
                                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                        />
                                                    </div>
                                                    <div>
                                                        <p style={{ fontSize: '0.6rem', fontWeight: 800, margin: 0, color: 'var(--secondary-color)', textTransform: 'uppercase' }}>{c.creatorPosition}</p>
                                                        <p style={{ fontSize: '0.65rem', fontWeight: 600, margin: 0, opacity: 0.9 }}>{c.creatorName.split(' ')[0]}</p>
                                                    </div>
                                                </div>
                                            ) : (
                                                <span style={{ fontSize: '0.7rem', opacity: 0.3 }}>Automático</span>
                                            )}
                                        </td>
                                        <td style={{ padding: '1rem' }}>
                                            <button onClick={() => handleDelete(c.id)} style={{ background: 'none', border: '1px solid #e74c3c', color: '#e74c3c', padding: '4px 8px', borderRadius: '5px', cursor: 'pointer', fontSize: '0.75rem' }}>
                                                Remover
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
