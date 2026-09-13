const fs = require('fs')
const path = require('path')

const root = path.join(__dirname, 'client', 'src')
const targetDirs = ['api', 'stores', 'types']

function walk(p, out = []) {
  for (const e of fs.readdirSync(p, { withFileTypes: true })) {
    const fp = path.join(p, e.name)
    if (e.isDirectory()) walk(fp, out)
    else if (/\.(ts|vue)$/.test(e.name)) out.push(fp)
  }
  return out
}

const allFiles = walk(root)
const sources = allFiles.map((f) => [f, fs.readFileSync(f, 'utf8')])

function filesIn(dirRel) {
  const abs = path.join(root, dirRel)
  if (!fs.existsSync(abs)) return []
  return walk(abs)
}

const results = []
for (const dir of targetDirs) {
  for (const f of filesIn(dir)) {
    const src = fs.readFileSync(f, 'utf8')
    const re = /export\s+(?:async\s+)?(?:function|const|class|interface|type|enum)\s+([A-Za-z0-9_]+)/g
    let m
    while ((m = re.exec(src))) {
      const name = m[1]
      let uses = 0
      const where = []
      for (const [g, gs] of sources) {
        if (g === f) continue
        if (new RegExp('\\b' + name + '\\b').test(gs)) {
          uses++
          where.push(g)
        }
      }
      const line = src.slice(0, m.index).split('\n').length
      results.push({
        file: path.relative(path.join(__dirname), f).replace(/\\/g, '/'),
        line,
        name,
        uses,
      })
    }
  }
}

const dead = results.filter((r) => r.uses === 0)
console.log('TOTAL EXPORTS SCANNED:', results.length)
console.log('ZERO-CONSUMPTION EXPORTS:', dead.length)
console.log('')
for (const d of dead) {
  console.log(`${d.file}:${d.line} :: ${d.name}`)
}
