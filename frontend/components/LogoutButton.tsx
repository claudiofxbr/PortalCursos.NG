'use client';

import React from 'react';
import { useAuth } from '@/app/context/AuthContext';

export default function LogoutButton() {
    const { logout, isAuthenticated } = useAuth();

    if (!isAuthenticated) return null;

    return (
        <button 
            onClick={logout}
            style={{
                marginTop: '10px',
                padding: '8px 12px',
                backgroundColor: 'rgba(255, 60, 60, 0.2)',
                color: '#ff6b6b',
                border: '1px solid #ff6b6b',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '0.85rem',
                fontWeight: 'bold',
                width: '100%',
                transition: 'background-color 0.2s',
            }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 60, 60, 0.4)'}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 60, 60, 0.2)'}
        >
            🚪 Sair / Logout
        </button>
    );
}
