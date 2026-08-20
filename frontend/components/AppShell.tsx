'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';
import ThemeSelector from './ThemeSelector';

// Rotas que NÃO precisam de autenticação
const PUBLIC_ROUTES = ['/auth/signin', '/auth/signup', '/privacidade'];

export default function AppShell({ children }: { children: React.ReactNode }) {
    const [isMounted, setIsMounted] = useState(false);
    const { isAuthenticated, user, logout, isLoading } = useAuth();
    const router = useRouter();
    const pathname = usePathname();

    const isPublicRoute = PUBLIC_ROUTES.includes(pathname || '');

    useEffect(() => {
        setIsMounted(true);

        // Redireciona para login se não autenticado em rota protegida
        if (!isLoading && !isAuthenticated && !isPublicRoute) {
            router.replace('/auth/signin');
        }
    }, [isAuthenticated, isPublicRoute, router, isLoading]);

    // 1. Se for rota pública, renderiza imediatamente (evita flash de loading no login)
    if (isPublicRoute) return <>{children}</>;

    // Hydration Guard SIGMA-V20.0
    // Garante que o render inicial no cliente coincida com o SSR (onde isMounted é sempre false)
    if (!isMounted) {
        return <div style={{ minHeight: '100vh', backgroundColor: 'var(--sidebar-bg)' }} />;
    }

    // 2. Estado de Carregamento (enquanto checkAuth está rodando)
    if (isLoading) {
        return (
            <div style={{
                display: 'flex', justifyContent: 'center', alignItems: 'center',
                minHeight: '100vh', backgroundColor: 'var(--sidebar-bg)',
                flexDirection: 'column', gap: '1rem'
            }}>
                <div className="omega-spinner" />
                <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: '0.9rem' }}>
                    Sincronização Adaptativa OMEGA...
                </p>
                <style>{`
                    .omega-spinner {
                        width: 40px; height: 40px; border: 3px solid rgba(255,255,255,0.1);
                        border-top: 3px solid var(--secondary-color); borderRadius: 50%;
                        animation: spin 0.8s linear infinite;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                `}</style>
            </div>
        );
    }

    // 3. Layout Principal (Sidebar + Main)
    return (
        <div style={{ display: 'flex', minHeight: '100vh', overflow: 'hidden' }}>
            {/* Sidebar Institucional */}
            <aside style={{
                width: '280px',
                backgroundColor: 'var(--sidebar-bg)',
                color: 'var(--white)',
                padding: '2rem',
                display: 'flex',
                flexDirection: 'column',
                zIndex: 10,
                flexShrink: 0,
            }}>
                <div style={{ marginBottom: '3rem' }}>
                    <h1 style={{ color: 'var(--secondary-color)', fontSize: '1.5rem', fontFamily: "'Inter Tight', sans-serif" }}>
                        PortalCursos<br />
                        <small style={{ fontSize: '0.8rem', opacity: 0.8, letterSpacing: '1px' }}>OMEGA-SUPREME V30.2</small>
                    </h1>
                </div>

                <nav style={{ flex: 1 }}>
                    <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                        {[
                            { href: '/', label: '📊 Dashboard' },
                            { href: '/academic', label: '🎓 Graduação' },
                            { href: '/academic/postgrad', label: '📋 Pós-Graduação' },
                            { href: '/courses', label: '📚 Gestão do MEC' },
                            { href: '/users', label: '🛡️ Controle Institucional', adminOnly: true },
                            { href: '/roadmap', label: '🚀 Implementação', adminOnly: true },
                            { href: '/finance', label: '💰 Financeiro' },
                            { href: '/repairs', label: '🛠️ Infraestrutura' },
                        ].filter(item => {
                            if (!item.adminOnly) return true;
                            const userRoles = user?.roles || [];
                            return userRoles.some((r: any) => {
                                const roleName = typeof r === 'string' ? r : r.name;
                                return roleName === 'ROLE_ROOT_MASTER' || roleName === 'ROLE_ADMIN';
                            });
                        }).map(({ href, label }) => (
                            <li key={href} style={{ padding: '0.8rem 0' }}>
                                <Link
                                    href={href}
                                    style={{
                                        color: 'inherit',
                                        textDecoration: 'none',
                                        opacity: pathname === href ? 1 : 0.7,
                                        fontWeight: pathname === href ? 700 : 400,
                                        borderLeft: pathname === href ? '3px solid var(--secondary-color)' : '3px solid transparent',
                                        paddingLeft: '0.75rem',
                                        display: 'block',
                                        transition: 'all 0.15s',
                                    }}
                                >
                                    {label}
                                </Link>
                            </li>
                        ))}
                    </ul>
                </nav>

                <div style={{ paddingTop: '1.5rem', borderTop: '1px solid rgba(255,255,255,0.1)' }}>
                    <p style={{ fontSize: '0.85rem', opacity: 0.6, marginBottom: '0.75rem' }}>
                        👤 {user?.username || 'Visitante'}
                    </p>
                    <button
                        onClick={logout}
                        style={{
                            padding: '8px 12px',
                            backgroundColor: 'rgba(255, 60, 60, 0.15)',
                            color: '#ff6b6b',
                            border: '1px solid rgba(255, 107, 107, 0.5)',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontSize: '0.85rem',
                            fontWeight: 600,
                            width: '100%',
                            transition: 'all 0.2s',
                            textAlign: 'left',
                        }}
                    >
                        🚪 Sair / Logout
                    </button>
                </div>
            </aside>

            {/* Área de Conteúdo Principal */}
            <main style={{ 
                flex: 1, 
                overflowY: 'auto', 
                padding: '2.5rem', 
                position: 'relative',
                background: 'radial-gradient(circle at top right, rgba(59, 130, 246, 0.03), transparent 40%), radial-gradient(circle at bottom left, rgba(197, 160, 89, 0.03), transparent 40%)'
            }}>
                <div style={{ position: 'absolute', top: '1rem', right: '2rem', zIndex: 100 }}>
                    <ThemeSelector />
                </div>
                {children}
            </main>
        </div>
    );
}
