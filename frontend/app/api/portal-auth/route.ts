import { NextRequest, NextResponse } from 'next/server';

// PORTAL_ACCESS_CODE não tem prefixo NEXT_PUBLIC_ — nunca vai para o bundle do cliente
const PORTAL_ACCESS_CODE = process.env.PORTAL_ACCESS_CODE;

export async function POST(request: NextRequest) {
    if (!PORTAL_ACCESS_CODE) {
        // Se a variável não estiver configurada, bloqueia acesso (fail-closed)
        console.error('[SecurityGate] PORTAL_ACCESS_CODE não configurado no servidor.');
        return NextResponse.json({ ok: false }, { status: 500 });
    }

    let body: { code?: string };
    try {
        body = await request.json();
    } catch {
        return NextResponse.json({ ok: false }, { status: 400 });
    }

    const { code } = body;

    if (!code || typeof code !== 'string') {
        return NextResponse.json({ ok: false }, { status: 400 });
    }

    // Comparação em tempo constante para evitar timing attacks
    const isValid = timingSafeEqual(code, PORTAL_ACCESS_CODE);

    return NextResponse.json({ ok: isValid }, { status: isValid ? 200 : 401 });
}

/** Comparação em tempo constante (evita ataques de timing side-channel). */
function timingSafeEqual(a: string, b: string): boolean {
    if (a.length !== b.length) {
        // Compara uma string dummy do mesmo comprimento para não vazar o tamanho
        let diff = 0;
        for (let i = 0; i < b.length; i++) {
            diff |= (a.charCodeAt(i % a.length) ^ b.charCodeAt(i));
        }
        return false;
    }
    let diff = 0;
    for (let i = 0; i < a.length; i++) {
        diff |= (a.charCodeAt(i) ^ b.charCodeAt(i));
    }
    return diff === 0;
}
