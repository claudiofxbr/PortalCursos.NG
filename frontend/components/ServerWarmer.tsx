'use client';

import { useEffect } from 'react';
import { checkServerHealth } from '../app/services/api';

/**
 * ServerWarmer Component - SIGMA V18.1
 * Realiza um ping silencioso e unificado para o backend.
 * utiliza a URL centralizada do motor de API para evitar discrepâncias.
 */
export default function ServerWarmer() {
    useEffect(() => {
        const warmServer = async () => {
            try {
                console.log(`[WARMER-V18.1] Iniciando aquecimento unificado...`);
                await checkServerHealth();
                console.info('[WARMER-V18.1] Canal estabelecido.');
            } catch (error) {
                console.warn('[WARMER-V18.1] Backend em Cold Start. Despertar assistido em curso.');
            }
        };

        // Delay de 1.5s para priorizar o carregamento dos assets da landing page
        const timer = setTimeout(warmServer, 1500);
        return () => clearTimeout(timer);
    }, []);

    return null; // Componente de infraestrutura (invisível)
}
