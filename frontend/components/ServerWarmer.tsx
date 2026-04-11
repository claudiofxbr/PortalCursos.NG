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
                console.log(`[V30.0-SUPREME] Iniciando sincronização de infraestrutura...`);
                const health = await checkServerHealth();
                console.info('[V30.0-SUPREME] Canal de dados estabelecido com sucesso.');
                
                // Dispara evento de sucesso para componentes interessados
                window.dispatchEvent(new CustomEvent('SUPREME_HEALTH', { detail: { isHealthy: true, isBooting: false, ...health } }));
            } catch (error) {
                console.warn('[V30.0-SUPREME] Infraestrutura em Cold Start. Motor de resiliência ativo.');
            }
        };

        // Delay estratégico para não competir com a hidratação inicial do Next.js
        const timer = setTimeout(warmInfrastructure, 2000);
        return () => clearTimeout(timer);
    }, []);

    return null; // Componente de infraestrutura silencioso
}
