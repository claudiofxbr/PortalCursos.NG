// PROTOCOLO V31.0-SECURE - COOKIES HttpOnly (2026-06-13)
// ============================================================
// Tokens JWT migrados de localStorage para cookies HttpOnly.
// O browser envia automaticamente o cookie em cada request.
// JavaScript não tem acesso aos tokens — XSS não consegue roubá-los.
// ============================================================

import axios from 'axios';

export const V_BUILD_ID = "V31.0-SECURE";

const getBaseUrl = () => {
    // Em localhost sem NEXT_PUBLIC_API_URL configurado, usa o backend local
    if (typeof window !== 'undefined') {
        const host = window.location.hostname;
        if (host === 'localhost' || host === '127.0.0.1') {
            return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/';
        }
    }
    // Em produção (e SSR): usa NEXT_PUBLIC_API_URL injetado no build
    // Garante que termina com /api/ para que api.get('auth/me') resolva corretamente
    const envUrl = process.env.NEXT_PUBLIC_API_URL || '';
    if (envUrl) {
        return envUrl.endsWith('/') ? envUrl : `${envUrl}/`;
    }
    return '/api/';
};

export const API_BASE_URL = getBaseUrl();
export const BASE_URL = API_BASE_URL.replace(/\/api\/?$/, '');

const isLocalhost = typeof window !== 'undefined' &&
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

// Estado Global de conectividade (somente status de rede — nunca armazena tokens)
if (typeof window !== 'undefined') {
    (window as any).PC_STATUS = {
        lastCheck: Date.now(),
        isHealthy: false,
        isBooting: false,
        attempt: 0,
        version: V_BUILD_ID
    };
}

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: isLocalhost ? 60000 : 30000,
    // withCredentials envia os cookies HttpOnly automaticamente em cada request
    withCredentials: true,
    headers: {
        'X-Build-ID': V_BUILD_ID,
    }
});

// Interceptor de Response
api.interceptors.response.use(
    (response) => {
        if (typeof window !== 'undefined') {
            const status = (window as any).PC_STATUS;
            status.isHealthy = true;
            status.isBooting = false;
            window.dispatchEvent(new CustomEvent('OMEGA_HEALTH', { detail: status }));
        }
        return response;
    },
    async (error) => {
        const { response, config } = error;
        const status = response?.status;

        // Cold start / rede indisponível — retry com backoff exponencial
        if (!response || status === 502 || status === 503 || status === 504 || error.code === 'ECONNABORTED') {
            config._retryCount = config._retryCount || 0;
            const maxRetries = 15;

            if (config._retryCount < maxRetries) {
                config._retryCount++;
                const delay = Math.min(2000 * Math.pow(1.4, config._retryCount), 30000);

                if (typeof window !== 'undefined') {
                    const s = (window as any).PC_STATUS;
                    s.isBooting = true;
                    s.attempt = config._retryCount;
                    window.dispatchEvent(new CustomEvent('OMEGA_BOOTING', { detail: s }));
                }

                await new Promise(resolve => setTimeout(resolve, delay));
                return api(config);
            }
        }

        // 401 — tenta renovar a sessão via cookie de refresh (silent refresh)
        if (status === 401) {
            // Logout não precisa de refresh — aceita o erro
            if (config.url?.includes('auth/signout')) {
                return Promise.resolve({ data: { message: "Logout realizado." } });
            }

            if (!config._retry) {
                config._retry = true;
                try {
                    // O cookie refreshToken é enviado automaticamente (withCredentials)
                    await axios.post(`${API_BASE_URL}auth/refreshtoken`, {}, { withCredentials: true });
                    // Cookie accessToken foi renovado pelo backend — repete a request original
                    return api(config);
                } catch {
                    if (typeof window !== 'undefined') {
                        window.dispatchEvent(new CustomEvent('AUTH_REQUIRED'));
                    }
                    return Promise.reject(error);
                }
            }
        }

        if (status === 409 && typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('SYNC_CONFLICT', { detail: { url: config.url } }));
        }

        return Promise.reject(error);
    }
);

export const checkServerHealth = async () => {
    const res = await api.get('health');
    return res.data;
};

export default api;
