import { NextResponse } from 'next/server'

export function middleware(request) {
    const session = request.cookies.get('cinex_session')
    const { pathname } = request.nextUrl

    // Se não tem sessão e não está na página de login, redireciona
    if (!session && pathname !== '/login' && !pathname.startsWith('/api')) {
        return NextResponse.redirect(new URL('/login', request.url))
    }

    // Se tem sessão e tenta ir pro login, manda pro dashboard
    if (session && pathname === '/login') {
        return NextResponse.redirect(new URL('/', request.url))
    }

    return NextResponse.next()
}

export const config = {
    matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)'],
}
