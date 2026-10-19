#!/usr/bin/env node
// Development helper for the Docker Compose stack: readiness checks and
// demo data. Usage: node scripts/liferay.mjs wait [--integration] | health | seed
import { setTimeout as delay } from 'node:timers/promises';

const BASE = (process.env.LIFERAY_URL ?? `http://localhost:${process.env.LIFERAY_PORT ?? 8080}`).replace(/\/+$/, '');
const ADMIN_EMAIL = process.env.LIFERAY_ADMIN_EMAIL ?? 'test@liferay.com';
const ADMIN_PASSWORD = process.env.LIFERAY_ADMIN_PASSWORD ?? '';
const WAIT_TIMEOUT_MS = Number(process.env.LIFERAY_WAIT_TIMEOUT_MS ?? 15 * 60_000);

export function basic(email, password) {
    return `Basic ${Buffer.from(`${email}:${password}`).toString('base64')}`;
}

const ADMIN = basic(ADMIN_EMAIL, ADMIN_PASSWORD);

async function request(path, { auth = ADMIN, ...init } = {}) {
    const headers = new Headers(init.headers);
    if (auth) {
        headers.set('Authorization', auth);
    }
    return fetch(BASE + path, { ...init, headers, redirect: 'manual' });
}

async function status(path, init) {
    try {
        return (await request(path, init)).status;
    } catch {
        return 0;
    }
}

const checks = [
    ['portal', '/c/portal/layout', s => s >= 200 && s < 400, { auth: null }],
    ['frontend bundle', '/o/exelearning-web/js/exelearning.js', s => s === 200, { auth: null }],
    ['REST API', '/o/exelearning/v1/resources/0', s => s === 404, {}],
    ['editor', '/o/exelearning-editor/index.html', s => s === 200, { auth: null }],
];

async function wait(integration) {
    const deadline = Date.now() + WAIT_TIMEOUT_MS;
    const pending = integration ? checks : checks.slice(0, 1);
    process.stdout.write(`Waiting for ${BASE} (${pending.map(c => c[0]).join(', ')})`);
    // Two passes: a bundle that is being replaced can answer once and then
    // restart, so every check must pass again after a short pause.
    for (const pass of [1, 2]) {
        if (pass === 2) {
            await delay(3000);
        }
        for (const [name, path, ok, init] of pending) {
            while (!ok(await status(path, init))) {
                if (Date.now() > deadline) {
                    console.error(`\nTimed out waiting for ${name} at ${BASE}${path}.`);
                    console.error('Check "make logs" or "docker compose logs liferay" for the cause.');
                    process.exit(1);
                }
                process.stdout.write('.');
                await delay(5000);
            }
        }
    }
    console.log(' ready.');
}

async function health() {
    let failed = false;
    for (const [name, path, ok, init] of checks) {
        const code = await status(path, init);
        const good = ok(code);
        failed ||= !good;
        console.log(`${good ? 'OK  ' : 'FAIL'} ${name.padEnd(16)} ${path} -> ${code || 'unreachable'}`);
    }
    const response = await request('/o/exelearning-editor/index.html', { auth: null }).catch(() => null);
    const html = response?.ok ? await response.text() : '';
    const configured = html.includes('__EXE_EMBEDDING_CONFIG__');
    failed ||= !configured;
    console.log(`${configured ? 'OK  ' : 'FAIL'} editor config    embedding configuration injected`);
    if (failed) {
        console.error('\nThe integration is not healthy. Run "make deploy" and check "make logs".');
        process.exit(1);
    }
}

async function main() {
    const [command, ...args] = process.argv.slice(2);
    switch (command) {
        case 'wait':
            await wait(args.includes('--integration'));
            break;
        case 'health':
            await health();
            break;
        case 'seed': {
            const { seed } = await import('./seed.mjs');
            await seed({ request });
            break;
        }
        default:
            console.error('Usage: node scripts/liferay.mjs wait [--integration] | health | seed');
            process.exit(2);
    }
}

await main();
