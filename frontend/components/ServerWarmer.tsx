'use client';

import { useEffect } from 'react';
import { checkServerHealth } from '../app/services/api';

/**
 * ServerWarmer Component - V30.0-SUPREME
 * Realiza o despertar inteligente da infraestrutura Cloud (Render + Neon).
 * Sincronizado com o Motor de Conectividade do sistema.
 */
export default function ServerWarmer() {
    useEffect(() => {
        const warmInfrastructure = async () => {
            try {
                console.log(`[OMEGA-SUPREME] Despertando Cluster de Dados...`);
                const health = await checkServerHealth();
                console.info('[OMEGA-SUPREME] Estação de trabalho sincronizada com Neon Cloud.');
                
                // Dispara evento de saúde unificado OMEGA
                window.dispatchEvent(new CustomEvent('OMEGA_HEALTH', { 
                    detail: { 
                        isHealthy: true, 
                        isBooting: false,
                        isSynchronizing: health.status === 'SYNCHRONIZING',
                        ...health 
                    } 
                }));
            } catch (error: any) {
                console.warn('[OMEGA-SUPREME] Cluster em estágio de Cold Start. Ativando escudos de resiliência.');
                // O Interceptor no api.ts já dispara o OMEGA_BOOTING, não precisamos duplicar aqui
            }
        };

        // Delay estratégico para estabilidade de hidratação
        const timer = setTimeout(warmInfrastructure, 1500);
        return () => clearTimeout(timer);
    }, []);

    return null; // Componente de infraestrutura silencioso
}
