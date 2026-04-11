import React from 'react';
import Image from 'next/image';

interface AuditStampProps {
    name?: string;
    position?: string;
    photoUrl?: string;
    date?: string;
}

export const AuditStamp: React.FC<AuditStampProps> = ({ name, position, photoUrl, date }) => {
    if (!name) return null;

    return (
        <div className="flex items-center gap-2 p-1 px-2 border rounded-full bg-slate-50/50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-800 w-fit">
            <div className="relative w-6 h-6 overflow-hidden rounded-full ring-1 ring-white dark:ring-slate-700">
                {photoUrl ? (
                    <Image 
                        src={photoUrl} 
                        alt={name} 
                        fill 
                        className="object-cover"
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
