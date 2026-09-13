'use client';

import { useEffect } from 'react';
import { checkServerHealth } from '../app/services/api';

/**
 * Faz um health-check inicial do backend ao carregar o app. Se falhar, o
 * interceptor de retry do axios (api.ts) assume e dispara OMEGA_BOOTING,
 * que o ConnectivityGuard usa para mostrar feedback visual de reconexão.
 */
export default function ServerWarmer() {
    useEffect(() => {
        const checkBackend = async () => {
            try {
                const health = await checkServerHealth();
                window.dispatchEvent(new CustomEvent('OMEGA_HEALTH', {
                    detail: {
                        isHealthy: true,
                        isBooting: false,
                        isSynchronizing: health.status === 'SYNCHRONIZING',
                        ...health
                    }
                }));
            } catch {
                // O interceptor do axios em api.ts já assume o retry e dispara OMEGA_BOOTING
            }
        };

        // Delay estratégico para estabilidade de hidratação
        const timer = setTimeout(checkBackend, 1500);
        return () => clearTimeout(timer);
    }, []);

    return null;
}
