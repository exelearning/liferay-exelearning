// Bundles the custom elements into one IIFE script served by the OSGi module.
import { copyFile, mkdir } from 'node:fs/promises';
import { build } from 'esbuild';

const outdir = new URL('./dist/', import.meta.url);
await mkdir(outdir, { recursive: true });
await build({
    entryPoints: [new URL('./src/index.ts', import.meta.url).pathname],
    outfile: new URL('exelearning.js', outdir).pathname,
    bundle: true,
    format: 'iife',
    target: 'es2022',
    minify: true,
    sourcemap: true,
    legalComments: 'none',
});
await copyFile(new URL('./src/exelearning.css', import.meta.url), new URL('exelearning.css', outdir));
