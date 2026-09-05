import { writeFile } from 'node:fs/promises';

const port = process.argv[2] || '9222';
const screenshotPath = process.argv[3];
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const target = targets.find(candidate => candidate.type === 'page');
if (!target) throw new Error('No hay WebView debug disponible');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
    socket.onopen = resolve;
    socket.onerror = reject;
});
let sequence = 0;
const pending = new Map();
const contexts = new Map();
const requests = new Map();
socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id) {
        const handler = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) handler?.reject(new Error(JSON.stringify(message.error)));
        else handler?.resolve(message.result);
    }
    if (message.method === 'Runtime.executionContextCreated') {
        const context = message.params.context;
        if (context.auxData?.isDefault) contexts.set(context.id, context.origin);
    }
    if (message.method === 'Runtime.executionContextDestroyed') contexts.delete(message.params.executionContextId);
    if (message.method === 'Network.requestWillBeSent') {
        const request = message.params;
        try {
            const url = new URL(request.request.url);
            requests.set(request.requestId, url.origin + url.pathname);
        } catch {}
    }
    if (message.method === 'Network.responseReceived' && message.params.response.status >= 400) {
        console.log('HTTP', message.params.response.status, requests.get(message.params.requestId));
    }
    if (message.method === 'Network.loadingFailed') {
        console.log('NETWORK', message.params.errorText, requests.get(message.params.requestId));
    }
};
function send(method, params = {}) {
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error(`Timeout: ${method}`)), 10000);
        const identifier = ++sequence;
        pending.set(identifier, {
            resolve: value => { clearTimeout(timeout); resolve(value); },
            reject: error => { clearTimeout(timeout); reject(error); }
        });
        socket.send(JSON.stringify({ id: identifier, method, params }));
    });
}
try {
    await send('Runtime.enable');
    await send('Page.enable');
    if (process.argv.includes('--reload')) {
        await send('Network.enable');
        await send('Page.reload');
        await new Promise(resolve => setTimeout(resolve, 12000));
    }
    const expression = `JSON.stringify({
        page: location.origin + location.pathname,
        playerScript: !!window.__pelotaPlayerInstalled,
        text: (window === window.top || document.querySelector('video')) ? document.body?.innerText.slice(0, 450) : '',
        videos: Array.from(document.querySelectorAll('video')).map(video => ({
            paused: video.paused, time: video.currentTime, ready: video.readyState,
            width: video.videoWidth, height: video.videoHeight, muted: video.muted,
            error: video.error?.code
        })),
        frames: Array.from(document.querySelectorAll('iframe')).map(frame => ({
            src: frame.src.split('?')[0], width: frame.offsetWidth, height: frame.offsetHeight,
            display: getComputedStyle(frame).display
        }))
    })`;
    for (const [contextId, origin] of contexts) {
        try {
            const result = await send('Runtime.evaluate', { expression, contextId, returnByValue: true });
            console.log(origin, result.result?.value || result.exceptionDetails?.text);
        } catch (error) { console.log(origin, error.message); }
    }
    if (screenshotPath) {
        const screenshot = await send('Page.captureScreenshot', { format: 'png' });
        await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'));
    }
} finally {
    socket.close();
}
