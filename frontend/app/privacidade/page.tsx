'use client';

import Link from 'next/link';
import React from 'react';

const SECTION_STYLE: React.CSSProperties = { marginBottom: '1.75rem' };
const H2_STYLE: React.CSSProperties = { fontSize: '1.1rem', color: 'var(--primary-color)', marginBottom: '0.5rem' };

export default function PrivacyPolicyPage() {
  return (
    <div style={{
      minHeight: '100vh',
      width: '100%',
      background: 'linear-gradient(135deg, var(--sidebar-bg) 0%, var(--primary-color) 100%)',
      padding: '3rem 1rem',
      display: 'flex',
      justifyContent: 'center',
    }}>
      <div className="glass-panel fade-in" style={{
        width: '100%',
        maxWidth: '760px',
        padding: '2.5rem',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        color: '#222',
      }}>
        <h1 style={{ color: 'var(--primary-color)', marginBottom: '0.25rem' }}>Política de Privacidade</h1>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem', fontSize: '0.85rem' }}>
          Versão 1.0 — em vigor desde 20/08/2026. Elaborada em conformidade com a Lei Geral de Proteção de Dados
          (Lei nº 13.709/2018 — LGPD) e, para titulares na União Europeia, com o Regulamento Geral sobre a Proteção
          de Dados (GDPR).
        </p>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>1. Dados que coletamos</h2>
          <p>Para operar a plataforma PortalCursos, coletamos os seguintes dados pessoais, conforme o seu perfil de uso:</p>
          <ul style={{ marginTop: '0.5rem', paddingLeft: '1.25rem' }}>
            <li>Identificação: nome completo, CPF, data de nascimento;</li>
            <li>Contato: e-mail, telefone, endereço;</li>
            <li>Imagem: foto de matrícula;</li>
            <li>Documentos: identidade, comprovante de residência e demais documentos acadêmicos enviados por você;</li>
            <li>Acadêmicos: matrícula, cursos, notas e histórico escolar;</li>
            <li>Financeiros: pagamentos, comprovantes e status de mensalidades;</li>
            <li>Acesso: nome de usuário, e-mail de login e registros de autenticação.</li>
          </ul>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>2. Finalidade e base legal</h2>
          <p>
            Utilizamos esses dados para viabilizar sua matrícula e permanência acadêmica, processar pagamentos,
            emitir documentos institucionais e cumprir obrigações legais e regulatórias (ex.: MEC). A base legal
            aplicada é a execução de contrato educacional (LGPD art. 7º, V / GDPR art. 6(1)(b)), o cumprimento de
            obrigação legal (LGPD art. 7º, II / GDPR art. 6(1)(c)) e, quando aplicável, o seu consentimento
            (LGPD art. 7º, I / GDPR art. 6(1)(a)), coletado no momento do cadastro.
          </p>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>3. Compartilhamento</h2>
          <p>
            Seus dados não são vendidos. Podem ser compartilhados apenas com prestadores de serviço estritamente
            necessários à operação da plataforma (hospedagem e banco de dados) e com órgãos públicos quando exigido
            por lei ou obrigação regulatória.
          </p>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>4. Retenção e eliminação</h2>
          <p>
            Mantemos seus dados pelo tempo necessário ao cumprimento das finalidades descritas acima e das
            obrigações legais de guarda de registros acadêmicos e financeiros. Você pode solicitar a eliminação dos
            seus dados a qualquer momento, observados os prazos de guarda exigidos por lei.
          </p>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>5. Segurança</h2>
          <p>
            Senhas são armazenadas com hash criptográfico (nunca em texto plano), o acesso à plataforma é feito por
            conexão criptografada (HTTPS) e o acesso aos seus documentos é restrito a usuários autenticados e
            autorizados.
          </p>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>6. Seus direitos</h2>
          <p>Nos termos da LGPD (art. 18) e do GDPR (arts. 15 a 21), você pode solicitar, a qualquer momento:</p>
          <ul style={{ marginTop: '0.5rem', paddingLeft: '1.25rem' }}>
            <li>Confirmação da existência de tratamento e acesso aos seus dados;</li>
            <li>Correção de dados incompletos, inexatos ou desatualizados;</li>
            <li>Eliminação dos dados tratados com base no seu consentimento;</li>
            <li>Portabilidade dos dados a outro fornecedor;</li>
            <li>Revogação do consentimento, quando esta for a base legal aplicável.</li>
          </ul>
        </section>

        <section style={SECTION_STYLE}>
          <h2 style={H2_STYLE}>7. Contato</h2>
          <p>
            Para exercer seus direitos ou esclarecer dúvidas sobre este tratamento de dados, entre em contato com a
            secretaria/administração da instituição responsável por esta plataforma.
          </p>
        </section>

        <div style={{ marginTop: '2rem' }}>
          <Link href="/auth/signup" style={{ color: 'var(--accent-color)', fontWeight: 600 }}>← Voltar ao cadastro</Link>
        </div>
      </div>
    </div>
  );
}
