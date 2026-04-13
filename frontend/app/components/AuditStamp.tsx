import api, { BASE_URL } from '@/app/services/api';

interface AuditStampProps {
    name?: string;
    position?: string;
    photoUrl?: string;
    date?: string;
}

export const AuditStamp: React.FC<AuditStampProps> = ({ name, position, photoUrl, date }) => {
    if (!name) return null;

    // Resolução robusta de URL
    const getResolvedPhotoUrl = (url?: string) => {
        if (!url) return undefined;
        if (url.startsWith('http')) return url;
        if (url.startsWith('/')) return `${BASE_URL}${url}`;
        return `${BASE_URL}/uploads/${url}`;
    };

    const resolvedUrl = getResolvedPhotoUrl(photoUrl);

    return (
        <div className="flex items-center gap-2 p-1 px-2 border rounded-full bg-slate-50/50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-800 w-fit">
            <div className="relative w-6 h-6 overflow-hidden rounded-full ring-1 ring-white dark:ring-slate-700">
                {resolvedUrl ? (
                    <img
                        src={resolvedUrl} 
                        alt={name} 
                        className="w-full h-full object-cover"
                    />
                ) : (
                    <div className="flex items-center justify-center w-full h-full bg-indigo-500 text-[10px] text-white font-bold">
                        {name.charAt(0)}
                    </div>
                )}
            </div>
            <div className="flex flex-col">
                <span className="text-[10px] font-semibold leading-tight text-slate-700 dark:text-slate-200">
                    {name}
                </span>
                <span className="text-[8px] leading-tight text-slate-500 dark:text-slate-400 uppercase tracking-tighter">
                    {position || 'Operador'} • {date ? new Date(date).toLocaleDateString() : 'Agora'}
                </span>
            </div>
        </div>
    );
};
