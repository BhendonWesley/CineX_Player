import { NextResponse } from 'next/server'
import { addDebugLog as log } from './lib/debug-logger'

export function middleware(request) {
    const session = request.cookies.get('cinex_session')
    const { pathname } = request.nextUrl
    
    // Log detalhado para identificar o contexto do Simulador
    if (pathname === '/') {
        console.error(` >>> [MIDDLEWARE] Path: / | Session: ${session ? 'OK' : 'MISSING'}`);
        console.error(` >>> [MIDDLEWARE] Headers: Origin=${request.headers.get('origin')}, Referer=${request.headers.get('referer')}`);
    }

    // Se estiver no login e já tiver sessão, manda pro home
    if (session && pathname === '/login') {
        return NextResponse.redirect(new URL('/', request.url))
    }

    // Se NÃO tiver sessão e NÃO for login ou arquivos estáticos, manda pro login
    if (!session && pathname !== '/login' && !pathname.startsWith('/api') && !pathname.includes('.')) {
        return NextResponse.redirect(new URL('/login', request.url))
    }

    return NextResponse.next()
}

export const config = {
    matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)'],
}
