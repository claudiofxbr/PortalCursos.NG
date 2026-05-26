"use client";

import { useState, useEffect } from "react";

export default function SecurityGate({ children }: { children: React.ReactNode }) {
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState(false);

  useEffect(() => {
    const authorized = sessionStorage.getItem("portal_authorized");
    if (authorized === "true") {
      setIsAuthorized(true);
    }
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const accessCode = process.env.NEXT_PUBLIC_PORTAL_ACCESS_CODE ?? "7512";
    if (password === accessCode) {
      sessionStorage.setItem("portal_authorized", "true");
      setIsAuthorized(true);
      setError(false);
    } else {
      setError(true);
      setPassword("");
    }
  };

  if (isAuthorized) {
    return <>{children}</>;
  }

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950 font-sans">
      <div className="w-full max-w-md p-8 bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl">
        <div className="text-center mb-8">
          <div className="inline-block p-3 bg-blue-500/10 rounded-full mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-white mb-2">Acesso Restrito</h1>
          <p className="text-slate-400 text-sm">xavierbr-VPS Security Layer</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">Código de Acesso</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Digite a senha global"
              className={`w-full px-4 py-3 bg-slate-800 border ${error ? 'border-red-500' : 'border-slate-700'} rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all text-center text-lg tracking-[0.5em]`}
              autoFocus
            />
            {error && (
              <p className="mt-2 text-red-400 text-xs text-center font-medium">Senha incorreta. Tente novamente.</p>
            )}
          </div>
          <button
            type="submit"
            className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-blue-900/20"
          >
            Validar Acesso
          </button>
        </form>

        <div className="mt-8 pt-6 border-t border-slate-800 text-center">
          <p className="text-slate-500 text-[10px] uppercase tracking-widest">PortalCursos OMEGA-SUPREME</p>
        </div>
      </div>
    </div>
  );
}
