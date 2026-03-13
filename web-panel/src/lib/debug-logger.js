
// Memória global para logs (apenas em desenvolvimento)
if (!global._debugLogs) {
    global._debugLogs = [];
}

export function addDebugLog(msg) {
    const logItem = {
        timestamp: new Date().toISOString(),
        message: msg
    };
    global._debugLogs.push(logItem);
    // Manter apenas os últimos 100 logs
    if (global._debugLogs.length > 100) {
        global._debugLogs.shift();
    }
    console.log(`[DEBUG_LOG] ${msg}`);
}

export function getDebugLogs() {
    return global._debugLogs || [];
}
