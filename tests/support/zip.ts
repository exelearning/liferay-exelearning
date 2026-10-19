import { deflateRawSync } from 'node:zlib';

/** Minimal ZIP writer for test fixtures: lets tests craft archives that normal tools refuse to produce. */
export interface ZipEntry {
    name: string;
    data: string | Uint8Array;
    /** Store uncompressed instead of deflating. */
    store?: boolean;
    /** Size written to the headers; defaults to the real size (lets tests lie). */
    declaredSize?: number;
}

const table = new Uint32Array(256).map((_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    return c >>> 0;
});

function crc32(bytes: Uint8Array): number {
    let crc = 0xffffffff;
    for (const byte of bytes) {
        crc = (table[(crc ^ byte) & 0xff] ?? 0) ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
}

export function zip(entries: ZipEntry[]): Buffer {
    const locals: Buffer[] = [];
    const centrals: Buffer[] = [];
    let offset = 0;
    for (const entry of entries) {
        const name = Buffer.from(entry.name, 'utf8');
        const raw = typeof entry.data === 'string' ? Buffer.from(entry.data, 'utf8') : Buffer.from(entry.data);
        const body = entry.store ? raw : deflateRawSync(raw);
        const method = entry.store ? 0 : 8;
        const crc = crc32(raw);
        const size = entry.declaredSize ?? raw.length;

        const local = Buffer.alloc(30);
        local.writeUInt32LE(0x04034b50, 0);
        local.writeUInt16LE(20, 4);
        local.writeUInt16LE(0x0800, 6);
        local.writeUInt16LE(method, 8);
        local.writeUInt32LE(crc, 14);
        local.writeUInt32LE(body.length, 18);
        local.writeUInt32LE(size, 22);
        local.writeUInt16LE(name.length, 26);
        locals.push(local, name, body);

        const central = Buffer.alloc(46);
        central.writeUInt32LE(0x02014b50, 0);
        central.writeUInt16LE(20, 4);
        central.writeUInt16LE(20, 6);
        central.writeUInt16LE(0x0800, 8);
        central.writeUInt16LE(method, 10);
        central.writeUInt32LE(crc, 16);
        central.writeUInt32LE(body.length, 20);
        central.writeUInt32LE(size, 24);
        central.writeUInt16LE(name.length, 28);
        central.writeUInt32LE(offset, 42);
        centrals.push(central, name);

        offset += local.length + name.length + body.length;
    }
    const directory = Buffer.concat(centrals);
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(entries.length, 8);
    end.writeUInt16LE(entries.length, 10);
    end.writeUInt32LE(directory.length, 12);
    end.writeUInt32LE(offset, 16);
    return Buffer.concat([...locals, directory, end]);
}

export const CONTENT_XML = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ode SYSTEM "content.dtd">
<ode xmlns="http://www.intef.es/xsd/ode" version="2.0"><odeProperties>
<odeProperty><key>pp_title</key><value>Adversarial fixture</value></odeProperty>
</odeProperties><odeNavStructures/></ode>`;

/** A minimal but valid package plus extra entries. */
export function elpx(extra: ZipEntry[] = []): Buffer {
    return zip([
        { name: 'content.xml', data: CONTENT_XML },
        { name: 'index.html', data: '<!DOCTYPE html><html><body><h1>Fixture</h1></body></html>' },
        ...extra,
    ]);
}
