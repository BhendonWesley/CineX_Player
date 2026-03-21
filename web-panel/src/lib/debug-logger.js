
// Memória global para logs (apenas em desenvolvimento)
if (!globalThis._debugLogs) {
    globalThis._debugLogs = [];
}

export function addDebugLog(msg) {
    const logItem = {
        timestamp: new Date().toISOString(),
        message: msg
    };
    globalThis._debugLogs.push(logItem);
    // Manter apenas os últimos 100 logs
    if (globalThis._debugLogs.length > 100) {
        globalThis._debugLogs.shift();
    }
    console.log(`[DEBUG_LOG] ${msg}`);
}

export function getDebugLogs() {
    return globalThis._debugLogs || [];
}
