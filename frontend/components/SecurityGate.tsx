"use client";

import { useState, useEffect, type CSSProperties } from "react";

// O código de acesso é validado exclusivamente no servidor (Route Handler /api/portal-auth).
// Nenhum segredo é enviado ao browser — o cliente nunca vê o valor correto.
//
// Estilos inline de propósito: este frontend não tem Tailwind instalado (o PostCSS só roda o
// autoprefixer), então classes utilitárias como "h-8 w-8" não geram CSS e a tela ficava sem estilo
// (ícone do cadeado ocupando a largura toda). Ver E-03 no teste.md.
const styles: Record<string, CSSProperties> = {
  overlay: {
    position: "fixed",
    inset: 0,
    zIndex: 9999,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: 16,
    background: "#020617",
    fontFamily: "inherit",
    overflowY: "auto",
  },
  card: {
    width: "100%",
    maxWidth: 448,
    boxSizing: "border-box",
    padding: 32,
    background: "#0f172a",
    border: "1px solid #1e293b",
    borderRadius: 16,
    boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5)",
  },
  header: { textAlign: "center", marginBottom: 32 },
  iconWrap: {
    display: "inline-flex",
    padding: 12,
    marginBottom: 16,
    background: "rgba(59, 130, 246, 0.1)",
    borderRadius: "50%",
    color: "#3b82f6",
  },
  icon: { width: 32, height: 32, display: "block" },
  title: { margin: "0 0 8px", fontSize: 24, fontWeight: 700, color: "#ffffff" },
  subtitle: { margin: 0, fontSize: 14, color: "#94a3b8" },
  label: {
    display: "block",
    marginBottom: 8,
    fontSize: 12,
    fontWeight: 500,
    letterSpacing: "0.05em",
    textTransform: "uppercase",
    color: "#94a3b8",
  },
  input: {
    width: "100%",
    boxSizing: "border-box",
    padding: "12px 16px",
    background: "#1e293b",
    borderStyle: "solid",
    borderWidth: 1,
    borderRadius: 8,
    color: "#ffffff",
    fontSize: 18,
    textAlign: "center",
    outlineColor: "#3b82f6",
  },
  error: { margin: "8px 0 0", fontSize: 12, fontWeight: 500, textAlign: "center", color: "#f87171" },
  button: {
    width: "100%",
    marginTop: 16,
    padding: "12px 16px",
    background: "#2563eb",
    border: "none",
    borderRadius: 8,
    color: "#ffffff",
    fontSize: 16,
    fontWeight: 600,
    cursor: "pointer",
    boxShadow: "0 10px 15px -3px rgba(30, 58, 138, 0.2)",
  },
  footer: { marginTop: 32, paddingTop: 24, borderTop: "1px solid #1e293b", textAlign: "center" },
  footerText: { margin: 0, fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", color: "#64748b" },
};

export default function SecurityGate({ children }: { children: React.ReactNode }) {
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const authorized = sessionStorage.getItem("portal_authorized");
    if (authorized === "true") {
      // Lê o estado de sessão só no navegador, depois da montagem (evita divergência de hidratação).
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setIsAuthorized(true);
    }
  }, []);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    // Campo vazio nem chega ao servidor.
    if (!password.trim()) {
      setErrorMessage("Digite o código de acesso.");
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
      const res = await fetch(`${basePath}/api/portal-auth`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code: password }),
      });

      if (res.ok) {
        const data = await res.json();
        if (data.ok) {
          sessionStorage.setItem("portal_authorized", "true");
          setIsAuthorized(true);
          return;
        }
      }

      setErrorMessage("Senha incorreta. Tente novamente.");
      setPassword("");
    } catch {
      setErrorMessage("Senha incorreta. Tente novamente.");
      setPassword("");
    } finally {
      setLoading(false);
    }
  };

  if (isAuthorized) {
    return <>{children}</>;
  }

  return (
    <div style={styles.overlay}>
      <div style={styles.card}>
        <div style={styles.header}>
          <div style={styles.iconWrap}>
            <svg xmlns="http://www.w3.org/2000/svg" style={styles.icon} width={32} height={32} fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 style={styles.title}>Acesso Restrito</h1>
          <p style={styles.subtitle}>xavierbr-VPS Security Layer</p>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div>
            <label htmlFor="portal-access-code" style={styles.label}>
              Código de Acesso
            </label>
            <input
              id="portal-access-code"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Digite a senha global"
              disabled={loading}
              aria-invalid={errorMessage !== null}
              aria-describedby={errorMessage ? "portal-access-error" : undefined}
              style={{
                ...styles.input,
                borderColor: errorMessage ? "#ef4444" : "#334155",
                // Espaçamento largo só para o código digitado; o texto de exemplo não deve ser cortado.
                letterSpacing: password ? "0.3em" : "normal",
              }}
              autoFocus
            />
            {errorMessage && (
              <p id="portal-access-error" role="alert" style={styles.error}>
                {errorMessage}
              </p>
            )}
          </div>
          <button
            type="submit"
            disabled={loading}
            style={{ ...styles.button, opacity: loading ? 0.5 : 1, cursor: loading ? "not-allowed" : "pointer" }}
          >
            {loading ? "Validando..." : "Validar Acesso"}
          </button>
        </form>

        <div style={styles.footer}>
          <p style={styles.footerText}>PortalCursos OMEGA-SUPREME</p>
        </div>
      </div>
    </div>
  );
}
