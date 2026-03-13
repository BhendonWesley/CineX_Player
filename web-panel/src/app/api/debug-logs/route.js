import { NextResponse } from 'next/server';
import { getDebugLogs, addDebugLog } from '@/lib/debug-logger';

export async function GET() {
    return NextResponse.json(getDebugLogs());
}

export async function POST(request) {
    try {
        const { message } = await request.json();
        addDebugLog(message);
        return NextResponse.json({ success: true });
    } catch (e) {
        return NextResponse.json({ success: false }, { status: 400 });
    }
}
