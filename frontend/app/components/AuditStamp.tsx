import api, { BASE_URL } from '@/app/services/api';

interface AuditStampProps {
    name?: string;
    position?: string;
    photoUrl?: string;
    date?: string;
}

export const AuditStamp: React.FC<AuditStampProps> = ({ name, position, photoUrl, date }) => {
    if (!name) return null;

    // Resolução robusta de URL com Fallback V37.6-ULTRA
    const getResolvedPhotoUrl = (url?: string) => {
        if (!url || url.trim() === "") return "/default-auditor.png"; // asset estático do Next.js, sem auth
        if (url.startsWith('http')) return url;
        if (url.startsWith('/')) return `${BASE_URL}${url}`;
        // Documento/foto do usuário: exige autenticação, servido via DocumentController
        return `${BASE_URL}/api/uploads/${url}`;
    };

    const resolvedUrl = getResolvedPhotoUrl(photoUrl);

    return (
        <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            padding: '4px 10px',
            border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '20px',
            backgroundColor: 'rgba(255,255,255,0.03)',
            width: 'fit-content'
        }}>
            <div style={{
                position: 'relative',
                width: '26px',
                height: '26px',
                overflow: 'hidden',
                borderRadius: '50%',
                border: '1px solid rgba(255,255,255,0.2)',
                flexShrink: 0
            }}>
                <img
                    src={resolvedUrl} 
                    alt={name} 
                    onError={(e) => {
                        // Se a imagem falhar (ex: arquivo deletado), mostra iniciais ou fallback visual
                        (e.target as HTMLImageElement).style.display = 'none';
                        const parent = (e.target as HTMLImageElement).parentElement;
                        if (parent) {
                            parent.style.backgroundColor = '#3498db';
                            parent.innerHTML = `<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;color:white;font-size:10px;font-weight:bold">${name.charAt(0)}</div>`;
                        }
                    }}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
                <span style={{ fontSize: '10px', fontWeight: 700, color: '#fff' }}>
                    {name}
                </span>
                <span style={{ fontSize: '8px', opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                    {position || 'Operador'} • {date ? new Date(date).toLocaleDateString('pt-BR') : 'Agora'}
                </span>
            </div>
        </div>
    );
};
