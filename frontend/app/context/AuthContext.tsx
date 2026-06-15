'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';

interface User {
    id: number;
    username: string;
    email: string;
    roles: string[];
    fullName?: string;
    position?: string;
    fotoUrl?: string;
}

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isAuthModalOpen: boolean;
    setIsAuthModalOpen: (isOpen: boolean) => void;
    lastUser: string | null;
    loginSuccess: (userData: User) => void;
    logout: () => Promise<void>;
    checkAuth: () => Promise<void>;
    isLoading: boolean;
    isColdStart: boolean;
    bootProgress: number;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
    const [lastUser, setLastUser] = useState<string | null>(null);
    const [isColdStart, setIsColdStart] = useState(false);
    const [bootProgress, setBootProgress] = useState(0);
    const [isChecking, setIsChecking] = useState(false);

    // Verifica autenticação consultando /me — o cookie é enviado automaticamente
    const checkAuth = async () => {
        if (isChecking) return;

        try {
            setIsChecking(true);
            const { default: api } = await import('@/app/services/api');
            const response = await api.get('auth/me');
            setUser(response.data);
            setIsAuthenticated(true);
            setLastUser(response.data.username);
            if (typeof window !== 'undefined') {
                window.dispatchEvent(new CustomEvent('HEALTH_SUCCESS'));
            }
        } catch (error: unknown) {
            const status = (error as any)?.response?.status;
            // 401/403 = sem sessão válida; silencioso (sem erro no console)
            if (status === 401 || status === 403 || status === undefined) {
                setUser(null);
                setIsAuthenticated(false);
            }
        } finally {
            setIsChecking(false);
            setIsLoading(false);
        }
    };

    useEffect(() => {
        // Safety timer: garante que isLoading sai de true mesmo se a rede travar
        const safetyTimer = setTimeout(() => setIsLoading(false), 8000);

        checkAuth();

        const handleBooting = (e: Event) => {
            setIsColdStart(true);
            setBootProgress((e as CustomEvent).detail?.attempt ?? 0);
        };
        const handleHealthOk = () => setIsColdStart(false);

        window.addEventListener('OMEGA_BOOTING', handleBooting);
        window.addEventListener('HEALTH_SUCCESS', handleHealthOk);

        return () => {
            clearTimeout(safetyTimer);
            window.removeEventListener('OMEGA_BOOTING', handleBooting);
            window.removeEventListener('HEALTH_SUCCESS', handleHealthOk);
        };
    }, []);

    const loginSuccess = (userData: User) => {
        setUser(userData);
        setIsAuthenticated(true);
        setLastUser(userData.username);
        setIsAuthModalOpen(false);
        if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('HEALTH_SUCCESS'));
        }
    };

    const logout = async () => {
        try {
            const { default: api } = await import('@/app/services/api');
            // O cookie refreshToken é enviado automaticamente — backend invalida a sessão e limpa os cookies
            await api.post('auth/signout', {});
        } catch {
            // Logout silencioso: mesmo com erro de rede, limpa o estado local
        } finally {
            setUser(null);
            setIsAuthenticated(false);
            const basePath = process.env.NEXT_PUBLIC_BASE_PATH || '';
            window.location.href = `${basePath}/auth/signin`;
        }
    };

    useEffect(() => {
        const handleAuthRequired = () => setIsAuthModalOpen(true);
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
