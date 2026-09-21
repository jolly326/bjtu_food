const fs = require('fs');
const path = require('path');
const dir = 'docs/feature';
const marker = '\n## 答疑';
const done = [];
for (const f of fs.readdirSync(dir)) {
  if (!f.endsWith('.md')) continue;
  const fp = path.join(dir, f);
  let t = fs.readFileSync(fp, 'utf8');
  const idx = t.indexOf(marker);
  if (idx === -1) continue;
  t = t.slice(0, idx).replace(/\n{3,}/g, '\n\n').trimEnd() + '\n';
  fs.writeFileSync(fp, t);
  done.push(f);
}
console.log('cleaned: ' + JSON.stringify(done));
let residual = 0;
for (const f of fs.readdirSync(dir)) {
  if (!f.endsWith('.md')) continue;
  if (fs.readFileSync(path.join(dir, f), 'utf8').includes('## 答疑')) residual++;
}
console.log('residual = ' + residual);
