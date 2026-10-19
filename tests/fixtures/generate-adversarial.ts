// Regenerates tests/fixtures/adversarial/*. Run: npx tsx is not required;
// `node --experimental-strip-types tests/fixtures/generate-adversarial.ts`
import { writeFileSync } from 'node:fs';
import { CONTENT_XML, elpx, zip } from '../support/zip.ts';

const dir = new URL('./adversarial/', import.meta.url);
const write = (name: string, data: Buffer | string) => writeFileSync(new URL(name, dir), data);

write('zip-slip.elpx', elpx([{ name: '../../../../tmp/exelearning-escaped.html', data: 'escaped' }]));
write('absolute-path.elpx', elpx([{ name: '/tmp/exelearning-absolute.html', data: 'absolute' }]));
write('windows-path.elpx', elpx([{ name: '..\\..\\exelearning-windows.html', data: 'windows' }]));
write('server-script.elpx', elpx([{ name: 'libs/shell.php', data: '<?php system($_GET["c"]);' }]));
write('htaccess.elpx', elpx([{ name: '.htaccess', data: 'Options +ExecCGI' }]));
write('duplicate-entries.elpx', elpx([{ name: 'INDEX.HTML', data: 'shadow' }]));
write('zip-bomb.elpx', elpx([{ name: 'content/bomb.txt', data: new Uint8Array(64 * 1024 * 1024) }]));
write(
    'lying-sizes.elpx',
    elpx([{ name: 'content/big.bin', data: new Uint8Array(32 * 1024 * 1024), declaredSize: 10 }]),
);
write('no-content-xml.elpx', zip([{ name: 'index.html', data: '<html></html>' }]));
write('legacy-elp.elpx', zip([{ name: 'contentv3.xml', data: '<instance/>' }]));
write('not-a-zip.elpx', 'This is not a ZIP archive.');
write(
    'xxe.elpx',
    zip([
        {
            name: 'content.xml',
            data: CONTENT_XML.replace(
                '<!DOCTYPE ode SYSTEM "content.dtd">',
                '<!DOCTYPE ode [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>',
            ).replace('Adversarial fixture', '&xxe;'),
        },
        { name: 'index.html', data: '<html></html>' },
    ]),
);
