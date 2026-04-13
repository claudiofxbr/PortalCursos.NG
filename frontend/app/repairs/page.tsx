'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '../services/api';
import { useAuth } from '../context/AuthContext';
import PhotoUpload3x4 from '@/components/PhotoUpload3x4';

// Auxiliar para tradução de status vindo do Backend (EN) para o Usuário (PT-BR)
const statusMap: Record<string, { label: string, color: string }> = {
  'OPEN': { label: 'Pendente', color: '#ff9800' },
  'IN_PROGRESS': { label: 'Em Andamento', color: '#1e88e5' },
  'RESOLVED': { label: 'Resolvido', color: '#4caf50' },
  'CANCELLED': { label: 'Cancelado', color: '#d32f2f' },
};

export default function RepairsPage() {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const [tickets, setTickets] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Estados para o novo chamado
  const [newTitle, setNewTitle] = useState('');
  const [newLoc, setNewLoc] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [mainPhotoFile, setMainPhotoFile] = useState<File | null>(null);

  useEffect(() => {
    if (!authLoading) {
      fetchTickets();
    }
  }, [authLoading]);

  const fetchTickets = async () => {
    if (authLoading || !isAuthenticated) return;
    
    try {
      setLoading(true);
      setError(null);
      const response = await api.get('/repairs'); 
      setTickets(response.data);
    } catch (err: any) {
      if (err.silent) return;
      
      console.error("[CAMPUS-CARE] Erro na infraestrutura:", err);
      
      if (err.response?.status === 403) {
        setError("ACESSO NEGADO: Sua categoria de acesso não permite gerenciar chamados de infraestrutura.");
      } else if (!err.response) {
        setError("SERVIDOR OFFLINE: Não foi possível conectar ao núcleo de serviços de reparo.");
      } else {
        setError("FALHA TÉCNICA: Ocorreu um erro inesperado ao carregar os chamados.");
      }
    } finally {
      setLoading(false);
    }
  };

  // Barreira de Segurança Visual V37.7
  const isAuthorized = user?.roles?.some((r: string) => 
    ['ROLE_ROOT_MASTER', 'ROLE_ADMIN', 'ROLE_SECRETARIA', 'ROLE_FINANCEIRO', 'ROLE_ACADEMICO', 'ROLE_COORDENADOR', 'ROLE_PROFESSOR'].includes(r)
  );

  if (!authLoading && isAuthenticated && !isAuthorized && !loading) {
    return (
      <div style={{ padding: '5rem', textAlign: 'center' }}>
        <div className="glass-panel" style={{ padding: '3rem', maxWidth: '600px', margin: '0 auto', border: '1px solid rgba(255, 82, 82, 0.2)' }}>
          <h2 style={{ color: '#d32f2f' }}>🚫 Acesso Restrito</h2>
          <p style={{ color: '#666', marginTop: '1rem' }}>
            O módulo <b>Campus Care</b> é uma prerrogativa prioritária apenas para funcionários e corpo docente autorizado.
          </p>
          <button 
            onClick={() => window.location.href = '/'} 
            className="btn-primary" 
            style={{ marginTop: '2rem', padding: '12px 24px', backgroundColor: '#1a237e' }}
          >
            Voltar ao Início
          </button>
        </div>
      </div>
    );
  }

  const handleReport = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!newTitle || !newLoc || !newDesc) {
      alert("⚠️ Por favor, preencha todos os campos obrigatórios (Título, Local e Descrição).");
      return;
    }

    try {
      const formData = new FormData();
      formData.append('title', newTitle);
      formData.append('location', newLoc);
      formData.append('description', newDesc);
      formData.append('status', 'OPEN');
      if (mainPhotoFile) {
        formData.append('mainPhotoFile', mainPhotoFile);
      }

      await api.post('repairs', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      alert("✅ Chamado registrado com sucesso!");
      setNewTitle('');
      setNewLoc('');
      setNewDesc('');
      setMainPhotoFile(null);
      fetchTickets();
    } catch (err: any) {
      const msg = err.response?.data?.message || "Falha técnica ao enviar chamado. Verifique sua conexão.";
      alert(`❌ Erro: ${msg}`);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm("⚠️ Tem certeza que deseja remover este chamado? Ele será arquivado para auditoria.")) return;
    
    try {
      await api.delete(`/repairs/${id}`);
      alert("✅ Chamado removido com sucesso!");
      fetchTickets();
    } catch (err: any) {
      alert("❌ Erro ao remover chamado.");
    }
  };

  const handleUploadPhoto = async (id: number, file: File) => {
    const formData = new FormData();
    formData.append('file', file);

    try {
      await api.post(`/repairs/${id}/photo`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      alert("✅ Foto adicionada!");
      fetchTickets();
    } catch (err: any) {
      const msg = err.response?.data || "Erro ao fazer upload da foto.";
      alert(`❌ ${msg}`);
    }
  };

  return (
    <div className="fade-in" style={{ padding: '1rem' }}>
      {/* Header Premium */}
      <header style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        marginBottom: '2.5rem',
        padding: '2rem',
        background: 'linear-gradient(135deg, #1a237e, #3949ab)',
        borderRadius: '20px',
        color: 'white',
        boxShadow: '0 10px 30px rgba(26, 35, 126, 0.2)'
      }}>
        <div>
          <h2 style={{ fontSize: '2.2rem', margin: 0, fontWeight: 800 }}>Campus Care 🛠️</h2>
          <p style={{ opacity: 0.8, marginTop: '0.5rem', fontSize: '1rem' }}>Portal de Manutenção e Infraestrutura do Campus.</p>
        </div>
        
        <button 
          onClick={() => alert("🚨 Equipe de resposta rápida notificada!")} 
          style={{ 
            backgroundColor: '#ff5252', 
            color: 'white', 
            fontWeight: 800,
            padding: '14px 28px',
            borderRadius: '12px',
            border: 'none',
            cursor: 'pointer',
            boxShadow: '0 4px 15px rgba(255, 82, 82, 0.4)',
            transition: 'transform 0.2s'
          }}
          onMouseOver={(e) => e.currentTarget.style.transform = 'scale(1.05)'}
          onMouseOut={(e) => e.currentTarget.style.transform = 'scale(1)'}
        >
          🚨 EMERGÊNCIA
        </button>
      </header>

      {error && (
        <div className="glass-panel" style={{ 
          backgroundColor: 'rgba(255, 82, 82, 0.05)', 
          color: '#d32f2f', 
          padding: '1.2rem', 
          marginBottom: '2rem', 
          border: '1px solid rgba(255, 82, 82, 0.2)',
          borderRadius: '12px',
          display: 'flex',
          alignItems: 'center',
          gap: '12px'
        }}>
          <span style={{ fontSize: '1.2rem' }}>⚠️</span> {error}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 400px', gap: '3rem' }}>
        {/* Lado Esquerdo: Lista de Chamados */}
        <section>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
            <h3 style={{ fontSize: '1.6rem', color: '#1a237e', margin: 0 }}>Histórico de Chamados</h3>
            <button onClick={fetchTickets} style={{ background: '#f5f5f5', border: '1px solid #ddd', padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontSize: '0.85rem' }}>🔄 Sincronizar</button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', minHeight: '400px' }}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '5rem', color: '#666' }}>
                <p style={{ fontSize: '1.1rem' }}>Carregando dados do campus...</p>
              </div>
            ) : tickets.length > 0 ? tickets.map(ticket => {
              const statusInfo = statusMap[ticket.status] || { label: ticket.status, color: '#757575' };
              return (
                <div key={ticket.id} className="glass-panel" style={{ 
                  padding: '1.8rem', 
                  display: 'flex', 
                  gap: '1.5rem',
                  borderLeft: `8px solid ${statusInfo.color}`,
                  background: 'white',
                  boxShadow: '0 4px 20px rgba(0,0,0,0.03)',
                  borderRadius: '16px',
                  transition: 'transform 0.2s ease'
                }}>
                  {/* Foto Principal 3x4 */}
                  <div style={{ 
                    width: '90px', 
                    height: '120px', 
                    borderRadius: '8px', 
                    backgroundColor: '#f8f9fa',
                    overflow: 'hidden',
                    border: '1px solid #eee',
                    flexShrink: 0
                  }}>
                    {ticket.mainPhotoUrl ? (
                      <img 
                        src={`${BASE_URL}/v1/storage/${ticket.mainPhotoUrl}`} 
                        alt="Foto principal" 
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2rem', opacity: 0.1 }}>🛠️</div>
                    )}
                  </div>

                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '0.8rem' }}>
                      <h4 style={{ margin: 0, color: '#1a237e', fontSize: '1.2rem' }}>{ticket.title}</h4>
                      <span style={{ 
                        fontSize: '0.7rem', 
                        padding: '4px 10px', 
                        borderRadius: '30px', 
                        backgroundColor: `${statusInfo.color}15`,
                        color: statusInfo.color,
                        fontWeight: 800,
                        border: `1px solid ${statusInfo.color}30`
                      }}>
                        {statusInfo.label.toUpperCase()}
                      </span>
                    </div>
                    <p style={{ fontSize: '0.95rem', color: '#444', lineHeight: '1.5', marginBottom: '1.2rem' }}>{ticket.description}</p>
                    <div style={{ display: 'flex', gap: '20px', fontSize: '0.85rem', color: '#777', marginBottom: '1.2rem' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>📍 {ticket.location}</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>👤 {ticket.reportedByFullName || 'Usuário'}</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>📅 {ticket.createdAt ? new Date(ticket.createdAt).toLocaleDateString('pt-BR') : 'Recente'}</span>
                    </div>

                    {/* Galeria de Fotos */}
                    {ticket.photoUrls && ticket.photoUrls.length > 0 && (
                      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '1.2rem' }}>
                        {ticket.photoUrls.map((url: string, idx: number) => (
                          <div key={idx} style={{ 
                            width: '80px', 
                            height: '80px', 
                            borderRadius: '8px', 
                            overflow: 'hidden', 
                            border: '1px solid #eee',
                            backgroundColor: '#f8f9fa',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                          }}>
                            <img 
                              src={`${BASE_URL}${url}`} 
                              alt={`Evidência ${idx + 1}`} 
                              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                              onError={(e) => {
                                (e.target as any).src = 'https://via.placeholder.com/80?text=Foto';
                              }}
                            />
                          </div>
                        ))}
                      </div>
                    )}
                    
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                       <span style={{ fontSize: '0.85rem', color: ticket.photoUrls?.length >= 4 ? '#d32f2f' : '#666' }}>
                        📸 {ticket.photoUrls?.length || 0}/4 fotos
                       </span>
                       {ticket.photoUrls?.length < 4 && (
                         <label style={{ 
                           fontSize: '0.8rem', 
                           color: '#1a237e', 
                           cursor: 'pointer', 
                           textDecoration: 'underline',
                           fontWeight: 600
                         }}>
                           Adicionar Foto
                           <input 
                             type="file" 
                             accept="image/*" 
                             style={{ display: 'none' }} 
                             onChange={(e) => {
                               const file = e.target.files?.[0];
                               if (file) handleUploadPhoto(ticket.id, file);
                             }}
                           />
                         </label>
                       )}
                    </div>
                  </div>
                  <div style={{ marginLeft: '20px', display: 'flex', flexDirection: 'column', gap: '10px', alignItems: 'center' }}>
                    {/* Carimbo de Auditoria Premium */}
                    {ticket.creatorName && (
                      <div style={{ 
                        display: 'flex', 
                        flexDirection: 'column', 
                        alignItems: 'center', 
                        padding: '10px',
                        backgroundColor: 'rgba(26, 35, 126, 0.03)',
                        borderRadius: '12px',
                        border: '1px dashed rgba(26, 35, 126, 0.2)',
                        minWidth: '100px'
                      }}>
                        <div style={{ 
                          width: '45px', 
                          height: '60px', 
                          borderRadius: '4px', 
                          overflow: 'hidden',
                          border: '1px solid #fff',
                          boxShadow: '0 2px 5px rgba(0,0,0,0.1)',
                          marginBottom: '6px'
                        }}>
                          {ticket.creatorPhotoUrl ? (
                            <img src={ticket.creatorPhotoUrl} alt="Auditor" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                            <div style={{ width: '100%', height: '100%', background: '#eee', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.8rem' }}>👤</div>
                          )}
                        </div>
                        <span style={{ fontSize: '0.65rem', fontWeight: 800, color: '#1a237e', textAlign: 'center', lineHeight: 1 }}>{ticket.creatorName.split(' ')[0]}</span>
                        <span style={{ fontSize: '0.55rem', color: '#666', marginTop: '2px' }}>{ticket.creatorPosition}</span>
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: '8px', marginTop: 'auto' }}>
                      <button 
                        onClick={() => handleDelete(ticket.id)}
                        style={{ background: 'rgba(211, 47, 47, 0.05)', color: '#d32f2f', border: '1px solid rgba(211, 47, 47, 0.2)', padding: '10px', borderRadius: '10px', cursor: 'pointer' }}
                        title="Remover Chamado"
                      >
                        🗑️
                      </button>
                      <button style={{ background: '#f8f9fa', border: '1px solid #eee', padding: '10px', borderRadius: '10px', cursor: 'pointer' }}>👁️</button>
                    </div>
                  </div>
                </div>
              );
            }) : (
              <div style={{ padding: '5rem', textAlign: 'center', color: '#bbb', background: '#fafafa', borderRadius: '20px', border: '2px dashed #eee' }}>
                <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>✅</div>
                <p style={{ fontSize: '1.1rem' }}>Tudo funcionando perfeitamente por aqui!</p>
              </div>
            )}
          </div>
        </section>

        {/* Lado Direito: Formulário */}
        <aside>
          <div className="glass-panel" style={{ 
            padding: '2.5rem', 
            background: 'white',
            borderRadius: '24px',
            boxShadow: '0 15px 40px rgba(0,0,0,0.06)',
            position: 'sticky',
            top: '2rem'
          }}>
            <h3 style={{ fontSize: '1.5rem', color: '#1a237e', marginBottom: '0.5rem' }}>Relatar Incidente</h3>
            <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '2rem' }}>Encontrou algo quebrado? Informe-nos para agilizarmos o reparo.</p>
            
            <form onSubmit={handleReport} style={{ display: 'flex', flexDirection: 'column', gap: '1.8rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#333', marginBottom: '10px' }}>ASSUNTO</label>
                <input 
                  type="text" 
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="Ex: Ar condicionado barulhento" 
                  style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '1.5px solid #eee', fontSize: '1rem' }} 
                />
              </div>
              
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#333', marginBottom: '10px' }}>LOCAL DO PROBLEMA</label>
                <input 
                  type="text" 
                  value={newLoc}
                  onChange={(e) => setNewLoc(e.target.value)}
                  placeholder="Ex: Sala 402 - Bloco D" 
                  style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '1.5px solid #eee', fontSize: '1rem' }} 
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#333', marginBottom: '10px' }}>EVIDÊNCIA VISUAL (3X4)</label>
                <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
                  <PhotoUpload3x4 
                    onPhotoSelected={(file) => setMainPhotoFile(file)}
                    label="Clique para anexar foto principal"
                  />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#333', marginBottom: '10px' }}>DESCRIÇÃO DETALHADA</label>
                <textarea 
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="Explique o que aconteceu..." 
                  rows={4}
                  style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '1.5px solid #eee', fontSize: '1rem', resize: 'none' }} 
                />
              </div>

              <button type="submit" className="btn-primary" style={{ 
                width: '100%', 
                padding: '20px', 
                borderRadius: '14px',
                fontSize: '1.1rem',
                fontWeight: 700,
                backgroundColor: '#1a237e',
                color: 'white',
                border: 'none',
                cursor: 'pointer',
                boxShadow: '0 8px 20px rgba(26, 35, 126, 0.25)'
              }}>
                ENVIAR RELATÓRIO
              </button>
            </form>
          </div>
        </aside>
      </div>
    </div>
  );
}
