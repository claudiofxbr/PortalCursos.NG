'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';

/**
 * AuthContext V20.0-ULTRA
 * Integrado com o Motor de Resiliência para feedback em tempo real.
 */
interface AuthContextType {
    user: any | null;
    isAuthenticated: boolean;
    isAuthModalOpen: boolean;
    setIsAuthModalOpen: (isOpen: boolean) => void;
    lastUser: string | null;
    loginSuccess: (userData: any) => void;
    logout: () => Promise<void>;
    checkAuth: () => Promise<void>;
    isLoading: boolean;
    isColdStart: boolean;
    bootProgress: number;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<any | null>(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    const [lastUser, setLastUser] = useState<string | null>(null);
    const [isColdStart, setIsColdStart] = useState(false);
    const [bootProgress, setBootProgress] = useState(0);

    const [isChecking, setIsChecking] = useState(false);

    // Função para verificar se o usuário está logado (chamada silenciosa)
    const checkAuth = async () => {
        if (isChecking) return;
        
        const hasToken = typeof window !== 'undefined' && !!localStorage.getItem('accessToken');
        if (!hasToken) {
            setUser(null);
            setIsAuthenticated(false);
            setIsLoading(false);
            return;
        }

        try {
            setIsChecking(true);
            const { default: api } = await import('@/app/services/api');
            const response = await api.get('auth/me');
            setUser(response.data);
            setIsAuthenticated(true);
            setLastUser(response.data.username);
            window.dispatchEvent(new CustomEvent('HEALTH_SUCCESS'));
        } catch (error: any) {
            const status = error.response?.status;
            if (status === 401 || status === 403) {
                if (typeof window !== 'undefined') {
                    localStorage.removeItem('accessToken');
                    localStorage.removeItem('refreshToken');
                }
                setUser(null);
                setIsAuthenticated(false);
            }
        } finally {
            setIsChecking(false);
            setIsLoading(false);
        }
    };

    useEffect(() => {
        const safetyTimer = setTimeout(() => {
            setIsLoading(false);
        }, 5000); 

        checkAuth();
        
        const handleUltraBooting = (e: any) => {
            setIsColdStart(true);
            setBootProgress(e.detail.attempt);
        };
        const handleHealthSuccess = () => {
            setIsColdStart(false);
        };

        window.addEventListener('OMEGA_BOOTING', handleUltraBooting);
        window.addEventListener('HEALTH_SUCCESS', handleHealthSuccess);

        return () => {
            clearTimeout(safetyTimer);
            window.removeEventListener('OMEGA_BOOTING', handleUltraBooting);
            window.removeEventListener('HEALTH_SUCCESS', handleHealthSuccess);
        };
    }, []);

    const loginSuccess = (userData: any) => {
        setUser(userData);
        setIsAuthenticated(true);
        setLastUser(userData.username);
        setIsAuthModalOpen(false);
        window.dispatchEvent(new CustomEvent('HEALTH_SUCCESS'));
    };

    const logout = async () => {
        try {
            const { default: api } = await import('@/app/services/api');
            const refreshToken = typeof window !== 'undefined' ? localStorage.getItem('refreshToken') : null;
            if (refreshToken) {
                await api.post('auth/signout', { refreshToken });
            }
        } catch (e) {
            // Logout silencioso em caso de erro
        } finally {
            if (typeof window !== 'undefined') {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
            }
            setUser(null);
            setIsAuthenticated(false);
            
            // Redirecionamento atômico para tela de login (Evita 401 em rotas privadas)
            const basePath = process.env.NEXT_PUBLIC_BASE_PATH || '';
            window.location.href = `${basePath}/auth/signin`;
        }
    };

    useEffect(() => {
        const handleAuthRequired = () => {
            setIsAuthModalOpen(true);
        };
        window.addEventListener('AUTH_REQUIRED', handleAuthRequired);
        return () => window.removeEventListener('AUTH_REQUIRED', handleAuthRequired);
    }, []);

    return (
        <AuthContext.Provider value={{ 
            user, 
            isAuthenticated, 
            isAuthModalOpen, 
            setIsAuthModalOpen, 
            lastUser, 
            loginSuccess,
            logout,
            checkAuth,
            isLoading,
            isColdStart,
            bootProgress
        }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth deve ser usado dentro de um AuthProvider');
    }
    return context;
};
