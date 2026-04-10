"use client";

import { V_BUILD_ID, API_BASE_URL } from "../services/api";
import React, { useEffect, useState } from "react";

/**
 * PÁGINA DE DIAGNÓSTICO V12
 * Permite ao usuário validar se o servidor da Hostinger entregou o código novo.
 */
export default function VersionPage() {
    const [clientTime, setClientTime] = useState("");
    
    useEffect(() => {
        setClientTime(new Date().toLocaleString());
    }, []);

    const nuclearReset = () => {
        if (confirm("Deseja realizar uma limpeza profunda? Isso limpará todo o cache do seu navegador para este site.")) {
            localStorage.clear();
            sessionStorage.clear();
            window.location.href = "/auth/signin?reset=true";
        }
    };

    return (
        <div style={{
            minHeight: '100vh',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: '#0d123d',
            color: '#fff',
            fontFamily: 'sans-serif',
            padding: '20px',
            textAlign: 'center'
        }}>
            <h1 style={{ color: '#c5a059' }}>Diagnóstico de Operação: V12</h1>
            
            <div style={{
                backgroundColor: 'rgba(255,255,255,0.05)',
                padding: '30px',
                borderRadius: '15px',
                border: '1px solid #c5a059',
                maxWidth: '500px',
                width: '100%'
            }}>
                <p><strong>Build ID:</strong> <span style={{ color: '#00ff00' }}>{V_BUILD_ID}</span></p>
                <p><strong>Endpoint Ativo:</strong> {API_BASE_URL}</p>
                <p><strong>Hora Local:</strong> {clientTime}</p>
                
                <hr style={{ borderColor: 'rgba(255,255,255,0.1)', margin: '20px 0' }} />
                
                <div style={{ marginBottom: '20px' }}>
                    <strong>Status de Blindagem:</strong><br/>
                    {API_BASE_URL.includes('localhost') ? (
                        <span style={{ color: '#ff4444' }}>⚠️ MODO LOCAL ATIVO (Incorreto para Produção)</span>
                    ) : (
                        <span style={{ color: '#00ff00' }}>✅ MODO CLOUD ATIVO (Correto para Produção)</span>
                    )}
                </div>

                <button 
                    onClick={() => window.location.href = "/auth/signin"}
                    style={{
                        backgroundColor: '#c5a059',
                        color: '#0d123d',
                        border: 'none',
                        padding: '12px 25px',
                        borderRadius: '5px',
                        fontWeight: 'bold',
                        cursor: 'pointer',
                        margin: '5px'
                    }}
                >
                    IR PARA LOGIN
                </button>

                <button 
                    onClick={nuclearReset}
                    style={{
                        backgroundColor: 'transparent',
                        color: '#ff4444',
                        border: '1px solid #ff4444',
                        padding: '12px 25px',
                        borderRadius: '5px',
                        fontWeight: 'bold',
                        cursor: 'pointer',
                        margin: '5px'
                    }}
                >
                    LIMPEZA NUCLEAR
                </button>
            </div>

            <p style={{ marginTop: '20px', fontSize: '0.8rem', opacity: 0.5 }}>
                Se você não vê "V12" acima, seu servidor Hostinger ainda não atualizou os arquivos.
            </p>
        </div>
    );
}
