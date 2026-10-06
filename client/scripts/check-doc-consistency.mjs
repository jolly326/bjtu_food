#!/usr/bin/env node
/**
 * 文档-代码一致性检查（产品设计评审 P2-13 门禁）。
 *
 * 两项检查：
 *  ① 零旧方案残留 —— 扫描 `docs/` 与三端手写源码中的回溯性表述：
 *     默认档（低误报）：原为 / 此前 / 已作废 / 已下线 / 已于 / project_spec
 *     `--strict` 追加（高误报，需人工核实）：已删除 / 不再 / 改为
 *  ② 文档相对链接可解析 —— `docs/**\/*.md` 的内联链接指向的本地文件必须存在。
 *
 * 退出码：默认「提示档」恒 0；`--strict` 命中即 1（供 CI 阻断）。
 *
 * 白名单：`scripts/doc-consistency-allow.txt`（可选，每行一个正则；命中该正则的行跳过）。
 */
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { join, dirname, resolve, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const STRICT = process.argv.includes('--strict')
const MAX_PRINT = 200

// 「还原为」含「原为」子串，用负向后顾排除该误报
const RESIDUAL_BASE = [/(?<!还)原为/, /原先/, /此前/, /已作废/, /已下线/, /已于/, /project_spec/]
const PATTERNS = STRICT ? [...RESIDUAL_BASE, /已删除/, /不再/, /改为/] : RESIDUAL_BASE

const SCAN_DIRS = ['docs', 'client/src', 'server/src', 'web/src']
const SCAN_EXTS = new Set(['.md', '.ts', '.vue', '.java'])
const SKIP_DIRS = new Set(['node_modules', 'dist', 'target', '.git', 'generated', 'logs', 'unpackage'])

const allowPath = join(ROOT, 'client/scripts/doc-consistency-allow.txt')
const ALLOW = existsSync(allowPath)
  ? readFileSync(allowPath, 'utf8')
      .split('\n')
      .map((l) => l.trim())
      .filter((l) => l && !l.startsWith('#'))
      .map((l) => new RegExp(l))
  : []

function walk(dir, out = []) {
  let entries
  try {
    entries = readdirSync(dir, { withFileTypes: true })
  } catch {
    return out
  }
  for (const e of entries) {
    if (e.isDirectory()) {
      if (!SKIP_DIRS.has(e.name)) walk(join(dir, e.name), out)
    } else if (SCAN_EXTS.has(extname(e.name))) {
      out.push(join(dir, e.name))
    }
  }
  return out
}

const rel = (p) => p.slice(ROOT.length + 1).replace(/\\/g, '/')
const hits = { residual: [], links: [] }

// ① 零旧方案残留
for (const dir of SCAN_DIRS) {
  for (const file of walk(join(ROOT, dir))) {
    const lines = readFileSync(file, 'utf8').split('\n')
    lines.forEach((line, i) => {
      if (!PATTERNS.some((re) => re.test(line))) return
      if (ALLOW.some((re) => re.test(line))) return
      hits.residual.push(`${rel(file)}:${i + 1}: ${line.trim().slice(0, 120)}`)
    })
  }
}

// ② 文档相对链接可解析
for (const file of walk(join(ROOT, 'docs')).filter((f) => f.endsWith('.md'))) {
  const text = readFileSync(file, 'utf8')
  for (const m of text.matchAll(/\]\(([^)\s]+)\)/g)) {
    const target = m[1]
    if (/^(https?:|mailto:|#)/.test(target)) continue
    const bare = target.split('#')[0]
    if (!bare || bare.includes('*')) continue
    const abs = resolve(dirname(file), decodeURIComponent(bare))
    if (!existsSync(abs)) hits.links.push(`${rel(file)} → 链接不可解析：${target}`)
  }
}

function report(title, list) {
  console.log(`\n[${title}] 命中 ${list.length} 处`)
  for (const item of list.slice(0, MAX_PRINT)) console.log('  ' + item)
  if (list.length > MAX_PRINT) console.log(`  … 其余 ${list.length - MAX_PRINT} 处省略`)
}

report(
  `零旧方案残留（${STRICT ? 'strict' : '默认'}档：${PATTERNS.map((re) => re.source).join(' / ')}）`,
  hits.residual,
)
report('文档相对链接可解析', hits.links)

const total = hits.residual.length + hits.links.length
if (total === 0) {
  console.log('\n✅ 一致性检查通过：无残留、无断链')
} else if (STRICT) {
  console.log(`\n❌ 一致性检查失败：${total} 处待处理（--strict）`)
  process.exit(1)
} else {
  console.log(`\n⚠️  提示档：${total} 处待核实（不阻断；加 --strict 可阻断）`)
}
