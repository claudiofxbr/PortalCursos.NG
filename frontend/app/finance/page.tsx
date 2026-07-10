'use client';

import React, { useState, useEffect } from 'react';
import api, { BASE_URL } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useAuditCRUD } from '../hooks/useAuditCRUD';
import { AuditStamp } from '../components/AuditStamp';
import { toast } from 'sonner';

type AcademicLevel = 'GRADUATION' | 'POSTGRADUATE';

export default function FinancePage() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<AcademicLevel>('GRADUATION');
  const [invoices, setInvoices] = useState<any[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Hooks SUPREME
  const financeService = useAuditCRUD('/finance');
  const invoicesService = useAuditCRUD('/finance/invoices');
  
  // Estados para Modais
  const [showChargeModal, setShowChargeModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState<any | null>(null);
  
  // Estado para formulário de nova cobrança
  const [newCharge, setNewCharge] = useState({
    studentId: '',
    amount: '',
    dueDate: '',
    category: 'MENSALIDADE',
    secretaryProcessType: 'REGULAR',
    description: ''
  });
  
  const [selectedStudent, setSelectedStudent] = useState<any | null>(null);
  const [isSearchingStudent, setIsSearchingStudent] = useState(false);
  
  // Estado para Edição
  const [editingInvoice, setEditingInvoice] = useState<any | null>(null);

  useEffect(() => {
    fetchFinanceData();
  }, [activeTab]);

  useEffect(() => {
    const searchStudent = async () => {
        if (!newCharge.studentId || newCharge.studentId.length < 1) {
            setSelectedStudent(null);
            return;
        }
        
        setIsSearchingStudent(true);
        try {
            const prefix = activeTab === 'GRADUATION' ? 'v1/grad-students' : 'v1/postgrad-students';
            const res = await api.get(`${prefix}/${newCharge.studentId}`);
            setSelectedStudent(res.data);
        } catch (e) {
            setSelectedStudent(null);
        } finally {
            setIsSearchingStudent(false);
        }
    };

    const timer = setTimeout(searchStudent, 800);
    return () => clearTimeout(timer);
  }, [newCharge.studentId, activeTab]);

  const fetchFinanceData = async () => {
    try {
      setLoading(true);
      const [invoicesData, historyData] = await Promise.all([
        api.get(`/finance/invoices/${activeTab}`).then(r => r.data).catch(() => []),
        api.get(`/finance/history/${activeTab}`).then(r => r.data).catch(() => [])
      ]);
      
      setInvoices(invoicesData);
      setHistory(historyData);
      setError(null);
    } catch (err) {
      console.error("Erro financeiro:", err);
      toast.error("Não foi possível carregar os dados financeiros.");
    } finally {
      setLoading(false);
    }
  };

  const handleCreateCharge = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await financeService.create({
        ...newCharge,
        amount: parseFloat(newCharge.amount),
        studentId: parseInt(newCharge.studentId),
        academicLevel: activeTab
      }, '/charge'); 
      fetchFinanceData();
      setShowChargeModal(false);
    } catch (err) {
      // toast already handled by hook
    }
  };

  const handleGeneratePayment = async (id: number, method: 'PIX' | 'BOLETO') => {
      try {
          const endpoint = method === 'PIX' ? `/finance/generate-pix/${id}` : `/finance/generate-boleto/${id}`;
          const res = await api.post(endpoint);
          setShowPaymentModal({ ...res.data, method });
      } catch (err: any) {
          const msg = err?.response?.data?.message || `Não foi possível gerar o ${method === 'PIX' ? 'código Pix' : 'boleto'}. Tente novamente.`;
          alert(msg);
      }
  };

  const handleDeleteInvoice = async (id: number) => {
    if (!confirm("⚠️ ATENÇÃO: Deseja realmente excluir esta fatura? Esta ação é irreversível e ficará registrada como inativa para auditoria.")) return;
    try {
      await invoicesService.remove(id);
      fetchFinanceData();
    } catch (err) {
      // toast handled by hook
    }
  };
 
  const handleUpdateInvoice = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await invoicesService.update(editingInvoice.id, editingInvoice);
      setEditingInvoice(null);
      fetchFinanceData();
    } catch (err) {
      // toast handled by hook
    }
  };

  return (
    <div className="fade-in" style={{ paddingBottom: '4rem' }}>
      <header style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        marginBottom: '2rem',
        animation: 'slideIn 0.5s ease-out'
      }}>
        <div>
          <h2 style={{ fontSize: '2.4rem', fontWeight: 800, background: 'linear-gradient(135deg, var(--primary-color), var(--secondary-color))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>Gestão Financeira</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '1.1rem' }}>Central de faturamento, processos e arrecadação.</p>
        </div>
        
        <div style={{ display: 'flex', gap: '1rem' }}>
          <button onClick={() => setShowChargeModal(true)} className="btn-primary hover-lift" style={{ 
            padding: '12px 24px',
            borderRadius: '12px',
            background: 'var(--primary-color)',
            boxShadow: '0 4px 15px rgba(26, 35, 126, 0.3)',
            display: 'flex', 
            alignItems: 'center', 
            gap: '10px',
            fontWeight: 700
          }}>
            <span>⚡</span> Gerar Nova Cobrança
          </button>
        </div>
      </header>

      {/* Tabs Customizadas */}
      <div style={{ 
          display: 'flex', 
          gap: '10px', 
          marginBottom: '2rem',
          borderBottom: '1px solid #eee',
          paddingBottom: '10px'
      }}>
          <button 
            onClick={() => setActiveTab('GRADUATION')}
            style={{
                padding: '10px 25px',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: activeTab === 'GRADUATION' ? 'var(--primary-color)' : 'transparent',
                color: activeTab === 'GRADUATION' ? 'white' : 'var(--text-secondary)',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.3s ease'
            }}
          >🎓 Graduação</button>
          <button 
            onClick={() => setActiveTab('POSTGRADUATE')}
            style={{
                padding: '10px 25px',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: activeTab === 'POSTGRADUATE' ? 'var(--primary-color)' : 'transparent',
                color: activeTab === 'POSTGRADUATE' ? 'white' : 'var(--text-secondary)',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.3s ease'
            }}
          >📘 Pós-Graduação</button>
      </div>

      {error && (
        <div className="glass-panel" style={{ backgroundColor: '#fff5f5', color: '#c53030', padding: '1rem', marginBottom: '1.5rem', border: '1px solid #feb2b2' }}>
          <strong>Erro:</strong> {error}
        </div>
      )}

      {/* Grid de Mensalidades */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h3 style={{ fontSize: '1.2rem' }}>Pendentes em {activeTab === 'GRADUATION' ? 'Graduação' : 'Pós'}</h3>
          <span style={{ fontSize: '0.9rem', color: '#666' }}>{invoices.length} faturas encontradas</span>
      </div>

      <section style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', 
        gap: '1.5rem',
        marginBottom: '3rem'
      }}>
        {loading ? (
          <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center', gridColumn: '1/-1' }}>Carregando dados financeiros...</div>
        ) : invoices.length > 0 ? invoices.map(invoice => (
          <div key={invoice.id} className="glass-panel hover-lift" style={{ 
            padding: '1.5rem', 
            borderTop: '5px solid ' + (invoice.status === 'OVERDUE' ? '#e53935' : 'var(--secondary-color)') 
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <span style={{ fontSize: '0.7rem', color: '#888', fontWeight: 600 }}>{invoice.category}</span>
              <span style={{ 
                fontSize: '0.65rem', 
                padding: '2px 8px', 
                borderRadius: '20px', 
                backgroundColor: invoice.status === 'OVERDUE' ? '#ffebee' : '#e8f5e9',
                color: invoice.status === 'OVERDUE' ? '#c62828' : '#2e7d32',
                fontWeight: 700
              }}>{invoice.status}</span>
            </div>
            
            <h4 style={{ fontSize: '1.1rem', marginBottom: '0.3rem', color: 'var(--primary-color)' }}>
                {invoice.student?.fullName || invoice.postgradStudent?.fullName || 'Usuário'}
            </h4>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
               {invoice.category === 'INTERNAL_PROCESS' && (
                   <span style={{ fontSize: '0.7rem', padding: '2px 8px', borderRadius: '4px', background: 'var(--secondary-color)', color: '#000', fontWeight: 700 }}>
                       ⚡ {invoice.secretaryProcessType}
                   </span>
               )}
               <p style={{ fontSize: '0.85rem', color: '#666', margin: 0 }}>
                  {invoice.description || 'Taxa Administrativa'}
               </p>
            </div>

            <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: '1rem', marginTop: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                <div>
                   <p style={{ fontSize: '0.75rem', color: '#999', marginBottom: '4px' }}>Vencimento: {new Date(invoice.dueDate).toLocaleDateString('pt-BR')}</p>
                   <span style={{ fontSize: '1.5rem', fontWeight: 800 }}>R$ {invoice.totalAmount?.toFixed(2)}</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'flex-end' }}>
                    <div style={{ display: 'flex', gap: '4px' }}>
                        <button onClick={() => setEditingInvoice(invoice)} style={{ padding: '4px 8px', fontSize: '0.7rem', background: '#e3f2fd', color: '#1976d2', border: 'none', borderRadius: '4px' }}>Editar</button>
                        <button onClick={() => handleDeleteInvoice(invoice.id)} style={{ padding: '4px 8px', fontSize: '0.7rem', background: '#ffebee', color: '#c62828', border: 'none', borderRadius: '4px' }}>Excluir</button>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button onClick={() => handleGeneratePayment(invoice.id, 'PIX')} className="glass-panel hover-lift" style={{ padding: '8px 12px', border: 'none', cursor: 'pointer', background: '#f0faff', color: '#0288d1', fontSize: '0.8rem' }}>Pix</button>
                        <button onClick={() => handleGeneratePayment(invoice.id, 'BOLETO')} className="glass-panel hover-lift" style={{ padding: '8px 12px', border: 'none', cursor: 'pointer', background: '#f5f5f5', color: '#333', fontSize: '0.8rem' }}>Boleto</button>
                    </div>
                </div>
            </div>

            {/* Selo de Auditoria V30.9-SUPREME */}
            <div style={{ marginTop: '1rem' }}>
              <AuditStamp 
                  name={invoice.creatorName} 
                  position={invoice.creatorPosition} 
                  photoUrl={invoice.creatorPhotoUrl ? (invoice.creatorPhotoUrl.startsWith('http') ? invoice.creatorPhotoUrl : `${BASE_URL}/uploads/${invoice.creatorPhotoUrl}`) : undefined}
                  date={invoice.createdAt}
              />
            </div>
          </div>
        )) : (
          <div className="glass-panel" style={{ padding: '3rem', textAlign: 'center', gridColumn: '1/-1', color: '#999' }}>
            Nenhuma cobrança pendente para este nível.
          </div>
        )}
      </section>

      {/* Histórico Transacional */}
      <section className="glass-panel" style={{ padding: '2rem' }}>
        <h3 style={{ marginBottom: '1.5rem', fontSize: '1.2rem' }}>Histórico de Recebimentos</h3>
        <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                    <tr style={{ textAlign: 'left', color: '#888', fontSize: '0.85rem' }}>
                        <th style={{ padding: '12px' }}>Data</th>
                        <th style={{ padding: '12px' }}>Beneficiário</th>
                        <th style={{ padding: '12px' }}>Categoria</th>
                        <th style={{ padding: '12px' }}>Método</th>
                        <th style={{ padding: '12px', textAlign: 'right' }}>Valor</th>
                    </tr>
                </thead>
                <tbody>
                    {history.map((h, i) => (
                        <tr key={i} style={{ borderTop: '1px solid #f9f9f9', fontSize: '0.9rem' }}>
                            <td style={{ padding: '12px' }}>{new Date(h.dueDate).toLocaleDateString('pt-BR')}</td>
                            <td style={{ padding: '12px', fontWeight: 500 }}>{h.student?.fullName || h.postgradStudent?.fullName}</td>
                            <td style={{ padding: '12px' }}><span style={{ fontSize: '0.75rem', background: '#eee', padding: '2px 8px', borderRadius: '4px' }}>{h.category}</span></td>
                            <td style={{ padding: '12px' }}>{h.method}</td>
                            <td style={{ padding: '12px', textAlign: 'right', fontWeight: 700, color: '#2e7d32' }}>R$ {h.amount.toFixed(2)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
            {history.length === 0 && <p style={{ textAlign: 'center', padding: '2rem', color: '#ccc' }}>Nenhum histórico disponível.</p>}
        </div>
      </section>

      {/* MODAL: Nova Cobrança (Processo Interno) */}
      {showChargeModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
              <div className="glass-panel fade-in" style={{ padding: '2.5rem', width: '100%', maxWidth: '550px', backgroundColor: 'white', borderRadius: '24px', boxShadow: '0 20px 50px rgba(0,0,0,0.2)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                      <h3 style={{ fontSize: '1.5rem', fontWeight: 800 }}>⚡ Nova Cobrança de Curso</h3>
                      <button onClick={() => setShowChargeModal(false)} style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#999' }}>&times;</button>
                  </div>

                  {/* IDENTIFICAÇÃO DO EMISSOR (FOTO 3X4 E CARGO) */}
                  <div className="glass-panel" style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '1rem', 
                      padding: '1rem', 
                      marginBottom: '1rem',
                      background: 'rgba(212, 175, 55, 0.05)',
                      border: '1px solid rgba(212, 175, 55, 0.2)',
                      borderRadius: '16px'
                  }}>
                      <div style={{ 
                          width: '50px', 
                          height: '66px', 
                          borderRadius: '6px', 
                          overflow: 'hidden',
                          border: '2px solid var(--secondary-color)',
                          backgroundColor: '#000',
                          flexShrink: 0
                      }}>
                          {user?.fotoUrl ? (
                              <img src={user.fotoUrl.startsWith('http') ? user.fotoUrl : `${BASE_URL}/uploads/${user.fotoUrl}`} alt="Emissor" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                              <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1rem' }}>👤</div>
                          )}
                      </div>
                      <div style={{ flex: 1 }}>
                          <p style={{ margin: 0, fontSize: '0.65rem', fontWeight: 700, color: 'var(--secondary-color)', textTransform: 'uppercase' }}>Emissor Auditor (Imutável)</p>
                          <h4 style={{ margin: '0', fontSize: '1rem', fontWeight: 800, color: 'var(--primary-color)' }}>{user?.username || 'Funcionário Identificado'}</h4>
                          <p style={{ margin: 0, fontSize: '0.75rem', opacity: 0.8, fontWeight: 500 }}>{user?.position || 'Departamento Financeiro'}</p>
                      </div>
                  </div>

                  {/* IDENTIFICAÇÃO DO BENEFICIÁRIO (ALUNO) - AUDITORIA CRUZADA */}
                  <div className="glass-panel" style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '1rem', 
                      padding: '1rem', 
                      marginBottom: '2rem',
                      background: selectedStudent ? 'rgba(46, 204, 113, 0.05)' : 'rgba(0,0,0,0.02)',
                      border: selectedStudent ? '1px solid #2ecc71' : '1px solid #eee',
                      borderRadius: '16px',
                      transition: 'all 0.3s ease'
                  }}>
                      <div style={{ 
                          width: '50px', 
                          height: '66px', 
                          borderRadius: '6px', 
                          overflow: 'hidden',
                          border: selectedStudent ? '2px solid #2ecc71' : '1px solid #ddd',
                          backgroundColor: '#f5f5f5',
                          flexShrink: 0
                      }}>
                          {selectedStudent?.fotoUrl ? (
                              <img src={selectedStudent.fotoUrl.startsWith('http') ? selectedStudent.fotoUrl : `${BASE_URL}/uploads/${selectedStudent.fotoUrl}`} alt="Aluno" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                              <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.2rem' }}>🎓</div>
                          )}
                      </div>
                      <div style={{ flex: 1 }}>
                          <p style={{ margin: 0, fontSize: '0.65rem', fontWeight: 700, color: selectedStudent ? '#27ae60' : '#d35400', textTransform: 'uppercase' }}>Beneficiário Auditado (Receptor)</p>
                          {isSearchingStudent ? (
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <div className="spinner-mini" style={{ width: '12px', height: '12px', border: '2px solid rgba(0,0,0,0.1)', borderTopColor: 'var(--primary-color)', borderRadius: '50%', animation: 'spin 1s linear infinite' }}></div>
                                  <span style={{ fontSize: '0.9rem', opacity: 0.6 }}>Verificando integridade...</span>
                              </div>
                          ) : selectedStudent ? (
                              <>
                                <h4 style={{ margin: '0', fontSize: '1rem', fontWeight: 800 }}>{selectedStudent.fullName}</h4>
                                <p style={{ margin: 0, fontSize: '0.75rem', opacity: 0.8, color: '#27ae60' }}>CPF Validado: {selectedStudent.cpf}</p>
                              </>
                          ) : (
                              <span style={{ fontSize: '0.9rem', opacity: 0.5, fontStyle: 'italic' }}>Insira o ID para auditoria cruzada</span>
                          )}
                      </div>
                      {selectedStudent && <div style={{ color: '#2ecc71', fontSize: '1.2rem' }}>✓</div>}
                  </div>
                  
                  <form onSubmit={handleCreateCharge}>
                      <div style={{ marginBottom: '1.5rem' }}>
                          <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Identificação do Aluno ({activeTab})</label>
                          <input type="number" required value={newCharge.studentId} onChange={e => setNewCharge({...newCharge, studentId: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee', outline: 'none', transition: 'border-color 0.3s' }} placeholder="ID do Aluno no Sistema" />
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
                          <div>
                            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Valor (R$)</label>
                            <input type="number" step="0.01" required value={newCharge.amount} onChange={e => setNewCharge({...newCharge, amount: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee' }} placeholder="0.00" />
                          </div>
                          <div>
                            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Data de Vencimento</label>
                            <input type="date" required value={newCharge.dueDate} onChange={e => setNewCharge({...newCharge, dueDate: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee' }} />
                          </div>
                      </div>

                      <div style={{ marginBottom: '1.5rem' }}>
                          <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Categoria da Cobrança</label>
                          <select value={newCharge.category} onChange={e => setNewCharge({...newCharge, category: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee', background: 'white' }}>
                              <option value="TUITION">Mensalidade Regular</option>
                              <option value="INTERNAL_PROCESS">Processo de Secretaria (Taxa)</option>
                              <option value="ENROLLMENT_FEE">Taxa de Matrícula/Rematrícula</option>
                              <option value="LAB_FEE">Taxa de Uso de Laboratório</option>
                              <option value="OTHER">Outros Serviços</option>
                          </select>
                      </div>

                      {newCharge.category === 'INTERNAL_PROCESS' && (
                          <div style={{ marginBottom: '1.5rem', animation: 'fadeInDown 0.3s ease' }}>
                              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Tipo de Processo Secretaria</label>
                              <select value={newCharge.secretaryProcessType} onChange={e => setNewCharge({...newCharge, secretaryProcessType: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee', background: '#f8f9ff' }}>
                                  <option value="TRANSCRIPT">Emissão de Histórico Escolar</option>
                                  <option value="DIPLOMA_COPY">Segunda Via de Diploma</option>
                                  <option value="CERTIFICATE">Certificado de Conclusão/Cursos</option>
                                  <option value="ENROLLMENT_CANCELLATION">Taxa de Cancelamento de Matrícula</option>
                                  <option value="COURSE_CHANGE">Taxa de Mudança de Curso/Turno</option>
                                  <option value="OTHER">Outros Processos Internos</option>
                              </select>
                          </div>
                      )}

                      <div style={{ marginBottom: '2rem' }}>
                          <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#666', marginBottom: '8px' }}>Detalhamento / Descrição</label>
                          <textarea rows={3} value={newCharge.description} onChange={e => setNewCharge({...newCharge, description: e.target.value})} style={{ width: '100%', padding: '14px', borderRadius: '12px', border: '2px solid #eee' }} placeholder="Descreva o motivo da cobrança para o aluno..."></textarea>
                      </div>

                      <div style={{ display: 'flex', gap: '15px', justifyContent: 'flex-end' }}>
                          <button type="button" onClick={() => setShowChargeModal(false)} style={{ padding: '14px 25px', borderRadius: '12px', border: 'none', background: '#f5f5f5', color: '#666', fontWeight: 600, cursor: 'pointer' }}>Descartar</button>
                          <button type="submit" className="btn-primary" style={{ padding: '14px 35px', borderRadius: '12px', fontWeight: 700 }}>Confirmar e Lançar</button>
                      </div>
                  </form>
              </div>
          </div>
      )}

      {/* MODAL: Pagamento (Pix / Boleto) */}
      {showPaymentModal && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(6px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001 }}>
               <div className="glass-panel fade-in" style={{ padding: '2.5rem', width: '100%', maxWidth: '400px', backgroundColor: 'white', textAlign: 'center' }}>
                   <h3 style={{ marginBottom: '0.5rem' }}>Pagamento via {showPaymentModal.method}</h3>
                   <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1.5rem' }}>Valor Total: R$ {showPaymentModal.totalAmount?.toFixed(2)}</p>
                   
                   {showPaymentModal.method === 'PIX' ? (
                       <div>
                           <div style={{ background: '#f5f5f5', padding: '1rem', borderRadius: '12px', marginBottom: '1rem' }}>
                               {/* Simulação de QR Code */}
                               <img src={`https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(showPaymentModal.paymentCode)}`} alt="Pix QR Code" style={{ borderRadius: '8px' }} />
                           </div>
                           <p style={{ fontSize: '0.7rem', color: '#888', marginBottom: '1rem', wordBreak: 'break-all' }}>{showPaymentModal.paymentCode}</p>
                           <button onClick={() => { navigator.clipboard.writeText(showPaymentModal.paymentCode); alert("Chave Pix copiada!"); }} className="btn-primary" style={{ width: '100%', marginBottom: '10px' }}>Copiar Chave Pix</button>
                       </div>
                   ) : (
                       <div>
                           <div style={{ background: '#f5f5f5', padding: '2rem', borderRadius: '12px', marginBottom: '1rem' }}>
                               <span style={{ fontSize: '3rem' }}>📄</span>
                               <p style={{ fontWeight: 600, marginTop: '10px' }}>Boleto Bancário</p>
                           </div>
                           <button onClick={() => window.open(showPaymentModal.paymentCode, '_blank')} className="btn-primary" style={{ width: '100%', marginBottom: '10px' }}>Baixar PDF do Boleto</button>
                       </div>
                   )}
                   <button onClick={() => setShowPaymentModal(null)} style={{ border: 'none', background: 'none', color: '#666', cursor: 'pointer', textDecoration: 'underline' }}>Fechar</button>
               </div>
          </div>
      )}

      {/* MODAL: Edição de Fatura (CRUD Universal) */}
      {editingInvoice && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
              <div className="glass-panel fade-in" style={{ padding: '2.5rem', width: '100%', maxWidth: '500px', backgroundColor: 'white' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                      <h3 style={{ fontSize: '1.3rem', fontWeight: 800 }}>📂 Editar Fatura #{editingInvoice.id}</h3>
                      <button onClick={() => setEditingInvoice(null)} style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#999' }}>&times;</button>
                  </div>

                  <form onSubmit={handleUpdateInvoice}>
                      <div style={{ marginBottom: '1rem' }}>
                          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#666', marginBottom: '4px' }}>Valor (R$)</label>
                          <input type="number" step="0.01" value={editingInvoice.totalAmount} onChange={e => setEditingInvoice({...editingInvoice, totalAmount: parseFloat(e.target.value)})} style={{ width: '100%', padding: '12px', borderRadius: '8px', border: '1px solid #ddd' }} />
                      </div>

                      <div style={{ marginBottom: '1rem' }}>
                          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#666', marginBottom: '4px' }}>Data de Vencimento</label>
                          <input type="date" value={editingInvoice.dueDate.split('T')[0]} onChange={e => setEditingInvoice({...editingInvoice, dueDate: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '8px', border: '1px solid #ddd' }} />
                      </div>

                      <div style={{ marginBottom: '1rem' }}>
                          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#666', marginBottom: '4px' }}>Status</label>
                          <select value={editingInvoice.status} onChange={e => setEditingInvoice({...editingInvoice, status: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '8px', border: '1px solid #ddd', background: '#fff' }}>
                              <option value="PENDING">Pendente</option>
                              <option value="PAID">Pago</option>
                              <option value="OVERDUE">Atrasado</option>
                              <option value="CANCELLED">Cancelado</option>
                          </select>
                      </div>

                      <div style={{ marginBottom: '1.5rem' }}>
                          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#666', marginBottom: '4px' }}>Descrição</label>
                          <textarea rows={3} value={editingInvoice.description} onChange={e => setEditingInvoice({...editingInvoice, description: e.target.value})} style={{ width: '100%', padding: '12px', borderRadius: '8px', border: '1px solid #ddd' }} />
                      </div>

                      <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                          <button type="button" onClick={() => setEditingInvoice(null)} style={{ padding: '12px 20px', borderRadius: '8px', background: '#eee', border: 'none' }}>Cancelar</button>
                          <button type="submit" className="btn-primary" style={{ padding: '12px 25px', borderRadius: '8px' }}>Salvar Alterações</button>
                      </div>
                  </form>
              </div>
          </div>
      )}
    </div>
  );
}
