// PROTOCOLO V30.0-SUPREME - RESILIÊNCIA TOTAL (11/04/2026)
// ============================================================
// Motor de conectividade de última geração para Render + Neon.
// Suporte a Request Deferring, Silent Auth e Telemetria Integrada.
// ============================================================

import axios from 'axios';

export const V_BUILD_ID = "V30.3-OMEGA";

const getBaseUrl = () => {
    if (typeof window !== 'undefined') {
        const host = window.location.hostname;
        if (host === 'localhost' || host === '127.0.0.1') {
            return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/';
        }
        
        // Em produção, se acessado por IP puro (ex: 69.62.87.38), deve usar HTTP na porta 8090
        // Se acessado por domínio (ex: portalcursos.ng), usa o protocolo atual do navegador (http/https) na porta do backend (8090)
        // Se a porta correspondente for diferente ou estiver por trás de um proxy reverso sem portas explícitas,
        // ajustamos dinamicamente. Para garantir compatibilidade total, usamos a porta 8090 padrão.
        const protocol = window.location.protocol; // http: ou https:
        // O Nginx (Proxy Reverso) no servidor lida com a rota /api transparentemente sob o mesmo domínio/IP.
        // Forçar a porta 8090 causa erros de protocolo SSL/handshake em produção.
        return `${protocol}//${host}/api/`;
    }
    
    // Fallback de build ou SSR (aponta para o proxy reverso do Nginx na porta padrão)
    return process.env.NEXT_PUBLIC_API_URL || '/api/';
};

export const API_BASE_URL = getBaseUrl();
export const BASE_URL = API_BASE_URL.replace(/\/api\/?$/, '');

const isLocalhost = typeof window !== 'undefined' && 
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

// Estado Global OMEGA V30.2
if (typeof window !== 'undefined') {
    (window as any).PC_OMEGA_STATUS = {
        lastCheck: Date.now(),
        isHealthy: false,
        isBooting: false,
        isSynchronizing: false,
        dbStatus: 'UNKNOWN',
        attempt: 0,
        version: V_BUILD_ID
    };
}

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: isLocalhost ? 60000 : 30000, // 30s para produção Hostinger (foi 300s)
    headers: {
        'X-Build-ID': V_BUILD_ID,
        'X-Protocol': 'ULTRA-RESILIENT-31.2'
    }
});

// Interceptor de Request: Injeção de JWT
api.interceptors.request.use((config) => {
    if (typeof window !== 'undefined') {
        const token = localStorage.getItem('accessToken');
        if (token && config.headers) {
            config.headers.Authorization = `Bearer ${token}`;
        }
    }
    return config;
}, (error) => Promise.reject(error));

// Interceptor de Response: Inteligência OMEGA
api.interceptors.response.use(
    (response) => {
        if (typeof window !== 'undefined') {
            const status = (window as any).PC_OMEGA_STATUS;
            status.isHealthy = true;
            status.isBooting = false;
            status.isSynchronizing = response.data?.status === 'SYNCHRONIZING';
            status.dbStatus = response.data?.diagnostics?.database || 'CONNECTED';
            window.dispatchEvent(new CustomEvent('OMEGA_HEALTH', { detail: status }));
        }
        return response;
    },
    async (error) => {
        const { response, config } = error;
        const status = response?.status;

        // Erro de Rede ou Cold Start (503 Service Unavailable / 502 Bad Gateway)
        if (!response || status === 502 || status === 503 || status === 504 || error.code === 'ECONNABORTED') {
            
            config._retryCount = config._retryCount || 0;
            const maxRetries = 15; 
            
            if (config._retryCount < maxRetries) {
                config._retryCount++;
                // Backoff Exponencial mais suave para não sobrecarregar o boot
                const delay = Math.min(2000 * Math.pow(1.4, config._retryCount), 30000);
                
                if (typeof window !== 'undefined') {
                    const omega = (window as any).PC_OMEGA_STATUS;
                    omega.isBooting = true;
                    omega.attempt = config._retryCount;
                    window.dispatchEvent(new CustomEvent('OMEGA_BOOTING', { detail: omega }));
                }

                await new Promise(resolve => setTimeout(resolve, delay));
                return api(config);
            }
        }

        // Silent Auth & Logout Fix (V30.0)
        if (status === 401) {
            if (config.url?.includes('auth/signout')) {
                return Promise.resolve({ data: { message: "Logout forçado via Protocolo SUPREME" } });
            }

            if (!config._retry) {
                config._retry = true;
                try {
                    const refreshToken = localStorage.getItem('refreshToken');
                    if (!refreshToken) return Promise.reject({ ...error, silent: true });

                    const refreshRes = await axios.post(`${API_BASE_URL}auth/refreshtoken`, { refreshToken });
                    const newToken = refreshRes.data.accessToken;
                    localStorage.setItem('accessToken', newToken);
                    
                    config.headers.Authorization = `Bearer ${newToken}`;
                    return api(config);
                } catch (e) {
                    if (localStorage.getItem('accessToken')) {
                        window.dispatchEvent(new CustomEvent('AUTH_REQUIRED'));
                    }
                    return Promise.reject(e);
                }
            }
        }

        // Tratamento de Conflito de Versão (Optimistic Locking)
        if (status === 409) {
            console.error("Conflito de Sincronismo Detectado (Version Mismatch). Recarregando dados...");
            window.dispatchEvent(new CustomEvent('SYNC_CONFLICT', { detail: { url: config.url } }));
        }

        return Promise.reject(error);
    }
);

export const checkServerHealth = async () => {
    try {
        const res = await api.get('health');
        return res.data;
    } catch (e) {
        throw e;
    }
};

export default api;
