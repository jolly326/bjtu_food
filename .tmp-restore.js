const fs = require('fs');
const { execSync } = require('child_process');
const git = (args) => execSync('git -c core.quotepath=false ' + args, { encoding: 'utf8', cwd: process.cwd() });

const status = git('status --short -- docs');
const lost = [];
for (const line of status.split('\n')) {
  if (!line.trim()) continue;
  const codes = line.slice(0, 2);
  const rest = line.slice(2).trim().replace(/^"|"$/g, '');
  if (codes.includes('D') && rest.startsWith('docs/feature/')) lost.push(rest);
}
console.log('丢失文件数=' + lost.length);
let restored = 0;
for (const rel of lost) {
  try {
    const content = git('show HEAD:"' + rel + '"');
    fs.writeFileSync(rel, content);
    restored++;
    console.log('已恢复: ' + rel + ' (' + content.length + ' 字符)');
  } catch (e) { console.log('恢复失败: ' + rel + ' — ' + e.message.slice(0, 60)); }
}

const after = git('status --short -- docs');
const dLeft = after.split('\n').filter(l => l.slice(0, 2).includes('D') && l.includes('docs/feature')).length;
let abnormal = 0;
for (const rel of lost) {
  if (!fs.existsSync(rel)) { abnormal++; continue; }
  const t = fs.readFileSync(rel, 'utf8');
  if (t.length < 200 || !t.startsWith('# ')) console.log('内容异常: ' + rel + ' (' + t.length + ' 字符)');
}
console.log('=== 终验 === git D 残留=' + dLeft + ' | 内容异常=' + abnormal + ' | 已恢复=' + restored + '/' + lost.length);
