// PROTOCOLO V20.0-ULTRA - CONECTIVIDADE STATEFUL (09/04/2026)
// ============================================================
// Motor de resiliência ultra-robusto para Render + Neon.
// Garante feedback visual imediato e retentativas exponenciais.
// ============================================================

import axios from 'axios';

export const V_BUILD_ID = "V20.0-ULTRA";

const getBaseUrl = () => {
    if (typeof window !== 'undefined') {
        const host = window.location.hostname;
        // Detecção dinâmica de ambiente
        if (host === 'localhost' || host === '127.0.0.1') {
            return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/';
        }
    }
    // URL de produção consolidada após análise profunda
    return 'https://portalcursos-backend.onrender.com/api/';
};

export const API_BASE_URL = getBaseUrl();
export const BASE_URL = API_BASE_URL.replace(/\/api\/?$/, '');

const isLocalhost = API_BASE_URL.includes('localhost') || API_BASE_URL.includes('127.0.0.1');

// Estado Global de Infraestrutura (Acessível via window para componentes de UI)
if (typeof window !== 'undefined') {
    (window as any).PC_ULTRA_STATUS = {
        lastCheck: Date.now(),
        isHealthy: false,
        isBooting: false,
        attempt: 0,
        version: V_BUILD_ID
    };
}

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: isLocalhost ? 10000 : 120000, // 10s local, 120s cloud (Neon Cold Start)
    headers: {
        'X-Build-ID': V_BUILD_ID,
        'X-Protocol': 'ULTRA-RESILIENT-20'
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

// Interceptor de Response: Inteligência de Cold Start e Recuperação
api.interceptors.response.use(
    (response) => {
        if (typeof window !== 'undefined') {
            const status = (window as any).PC_ULTRA_STATUS;
            status.isHealthy = true;
            status.isBooting = false;
        }
        return response;
    },
    async (error) => {
        const { response, config } = error;
        const status = response?.status;

        // Erro de Rede ou Cold Start (Render Free Tier)
        if (!response || status === 502 || status === 503 || status === 504 || error.code === 'ECONNABORTED') {
            
            if (isLocalhost) {
                return Promise.reject({ ...error, message: "[LOCAL] Backend não detectado na porta 8080." });
            }

            config._retryCount = config._retryCount || 0;
            const maxRetries = 10; // Aumentado para ULTRA
            
            if (config._retryCount < maxRetries) {
                config._retryCount++;
                const delay = Math.min(1000 * Math.pow(1.5, config._retryCount), 15000);
                
                if (typeof window !== 'undefined') {
                    const ultra = (window as any).PC_ULTRA_STATUS;
                    ultra.isBooting = true;
                    ultra.attempt = config._retryCount;
                    window.dispatchEvent(new CustomEvent('ULTRA_BOOTING', { detail: ultra }));
                }

                await new Promise(resolve => setTimeout(resolve, delay));
                return api(config);
            }
        }

        // Silent Refresh para Expiração de Token (401)
        if (status === 401 && !config._retry) {
            config._retry = true;
            try {
                const refreshToken = localStorage.getItem('refreshToken');
                if (!refreshToken) {
                    // Se não há refresh token, estamos em logout ou deslogados. Silenciar.
                    return Promise.reject({ ...error, silent: true });
                }

                const refreshRes = await axios.post(`${API_BASE_URL}auth/refreshtoken`, { refreshToken });
                const newToken = refreshRes.data.accessToken;
                localStorage.setItem('accessToken', newToken);
                
                config.headers.Authorization = `Bearer ${newToken}`;
                return api(config);
            } catch (e) {
                // Se falhar o refresh, dispara login apenas se ainda pretendíamos estar logados
                if (localStorage.getItem('accessToken')) {
                    window.dispatchEvent(new CustomEvent('AUTH_REQUIRED'));
                }
                return Promise.reject(e);
            }
        }

        return Promise.reject(error);
    }
);

// Health Check Singleton
export const checkServerHealth = async () => {
    try {
        const res = await api.get('health');
        return res.data;
    } catch (e) {
        throw e;
    }
};

export default api;
