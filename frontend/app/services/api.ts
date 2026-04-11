// PROTOCOLO V30.0-SUPREME - RESILIÊNCIA TOTAL (11/04/2026)
// ============================================================
// Motor de conectividade de última geração para Render + Neon.
// Suporte a Request Deferring, Silent Auth e Telemetria Integrada.
// ============================================================

import axios from 'axios';

export const V_BUILD_ID = "V30.0-SUPREME";

const getBaseUrl = () => {
    if (typeof window !== 'undefined') {
        const host = window.location.hostname;
        if (host === 'localhost' || host === '127.0.0.1') {
            return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/';
        }
    }
    return 'https://portalcursos-backend.onrender.com/api/';
};

export const API_BASE_URL = getBaseUrl();
export const BASE_URL = API_BASE_URL.replace(/\/api\/?$/, '');

const isLocalhost = API_BASE_URL.includes('localhost') || API_BASE_URL.includes('127.0.0.1');

// Estado Global V30.0
if (typeof window !== 'undefined') {
    (window as any).PC_SUPREME_STATUS = {
        lastCheck: Date.now(),
        isHealthy: false,
        isBooting: false,
        dbStatus: 'UNKNOWN',
        attempt: 0,
        version: V_BUILD_ID
    };
}

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: isLocalhost ? 15000 : 120000, 
    headers: {
        'X-Build-ID': V_BUILD_ID,
        'X-Protocol': 'SUPREME-RESILIENCE-30'
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

// Interceptor de Response: Inteligência SUPREME
api.interceptors.response.use(
    (response) => {
        if (typeof window !== 'undefined') {
            const status = (window as any).PC_SUPREME_STATUS;
            status.isHealthy = true;
            status.isBooting = false;
            status.dbStatus = response.data?.diagnostics?.database || 'CONNECTED';
            window.dispatchEvent(new CustomEvent('SUPREME_HEALTH', { detail: status }));
        }
        return response;
    },
    async (error) => {
        const { response, config } = error;
        const status = response?.status;

        // Erro de Rede ou Cold Start (Render Free Tier)
        if (!response || status === 502 || status === 503 || status === 504 || error.code === 'ECONNABORTED') {
            
            config._retryCount = config._retryCount || 0;
            const maxRetries = 12; 
            
            if (config._retryCount < maxRetries) {
                config._retryCount++;
                const delay = Math.min(1000 * Math.pow(1.6, config._retryCount), 20000);
                
                if (typeof window !== 'undefined') {
                    const supreme = (window as any).PC_SUPREME_STATUS;
                    supreme.isBooting = true;
                    supreme.attempt = config._retryCount;
                    window.dispatchEvent(new CustomEvent('SUPREME_BOOTING', { detail: supreme }));
                }

                await new Promise(resolve => setTimeout(resolve, delay));
                return api(config);
            }
        }

        // Silent Auth & Logout Fix (V30.0)
        if (status === 401) {
            // Se for chamada de logout, ignoramos o 401 e resolvemos como sucesso para o app
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
