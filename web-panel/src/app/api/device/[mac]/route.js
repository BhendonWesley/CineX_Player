import { NextResponse } from 'next/server'

export async function GET(request, { params }) {
    const mac = params.mac

    // This would query Supabase for the playlist associated with the MAC
    // For the demo/build check, we return a mock response

    const mockPlaylist = {
        mac: mac,
        sync_status: 'success',
        playlist: {
            type: 'xtream',
            host: 'http://cinex.example.com',
            user: 'user_demo',
            pass: 'pass_demo'
        }
    }

    return NextResponse.json(mockPlaylist)
}
