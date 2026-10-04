/**
 * UI Token 护栏（2026-10-01 UI 规范统一）
 * ========================================================================
 * 把 docs/ui/client/设计变量.md §3 的红线从「文档自觉」变成「可执行」：
 *
 *   ① 裸色值        —— `#hex` / `rgb()` / `rgba()`（字体栈、currentColor、transparent 除外）
 *   ② 裸尺寸        —— 未走 `var(--…)` 的 `N rpx` / `N px`
 *   ③ `@click`      —— uni-app 小程序端应为 `@tap`（`@click` 在 MP 端不生效）
 *
 * 扫描范围：`src/**` 下的 `.vue`（`<style>` 块 + 模板内联 `style="…"`）、`.css`、`.scss`。
 *
 * 口径说明：
 *   · **注释先被剥离**（`/* *\/` 全语言；`//` 仅 .scss）—— 注释里的色值 / 尺寸是文档性说明，不判违规；
 *   · **允许清单**：token 真源文件本身必须写字面量（generated-colors.css / design-tokens.css）；
 *   · `1rpx` 为全站**发丝线**惯用值（分隔线 / 描边），非设计标度成员 ⇒ 允许（待办：如需收敛可加
 *     `--border-width-hairline`，见 design 文档 §6 缺口）；
 *   · `transform: scale()` 不判违规：图片按压反馈合法（禁止的是「整卡 scale 按压」，机器无法判意图）。
 *
 * 用法：
 *   node scripts/check-ui-tokens.mjs            # 严格模式：有违规 ⇒ 退出码 1（清零后可并入 verify）
 *   node scripts/check-ui-tokens.mjs --report   # 报告模式：始终退出 0（清零期间用）
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const CLIENT_ROOT = fileURLToPath(new URL('..', import.meta.url))
const SRC = join(CLIENT_ROOT, 'src')
const REPORT_ONLY = process.argv.includes('--report')

/** token 真源：这两份文件里的字面量是**定义**，不是违规 */
const EXEMPT = new Set(
  ['src/theme/generated-colors.css', 'src/theme/design-tokens.css'].map((p) => p.split('/').join(sep)),
)

/** 允许项：值级（物理/渲染量）+ 文件级（组件私有尺寸）；理由登记于 docs/ui/client/设计变量.md §6 */
const ALLOW = JSON.parse(readFileSync(join(CLIENT_ROOT, 'scripts', 'ui-token-allow.json'), 'utf8'))
const ALLOW_VALUES = new Set(ALLOW.values || [])
const ALLOW_FILES = new Map(
  Object.entries(ALLOW.files || {}).map(([p, vals]) => [p.split('/').join(sep), new Set(vals)]),
)

const RULES = [
  {
    id: 'bare-color',
    label: '裸色值',
    test: (line) => /#[0-9a-fA-F]{3,8}\b/.test(line) || /\brgba?\(/.test(line),
  },
  {
    id: 'bare-dimension',
    label: '裸尺寸',
    // 允许项（值级）与「1rpx 发丝线」在此放行；文件级允许项在扫描循环内按文件放行
    test: (line) => {
      const matches = line.match(/(?<![\w-])(\d+(?:\.\d+)?)(rpx|px)\b/g) || []
      return matches.some((m) => !ALLOW_VALUES.has(m))
    },
  },
  {
    id: 'at-click',
    label: '@click（应为 @tap）',
    test: (line) => /(^|[\s(<])@click\b/.test(line),
    onlyExt: ['.vue'],
  },
]

/** 把注释内容替换为等长空白（保留换行 ⇒ 行号不变） */
function stripComments(src, isScss) {
  let out = src.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
  if (isScss) {
    // 仅 .scss 有 // 行注释；避免误伤 url(//…) 与协议里的 //
    out = out.replace(/(^|[^:\/])\/\/[^\n]*/gm, (m, p1) => p1 + ' '.repeat(m.length - p1.length))
  }
  return out
}

/** 取出 .vue 需要扫描的片段：<style> 块 + 模板内联 style="…"（返回 [{startLine, text}]） */
function extractVueRegions(src) {
  const regions = []
  const lines = src.split('\n')
  const styleRe = /<style[^>]*>([\s\S]*?)<\/style>/g
  let m
  while ((m = styleRe.exec(src)) !== null) {
    const startLine = src.slice(0, m.index).split('\n').length
    regions.push({ startLine, text: m[1] })
  }
  // 内联样式（模板）：逐行取 style="…" 的内容
  lines.forEach((line, i) => {
    const inline = line.match(/style="([^"]*)"/g)
    if (inline) {
      inline.forEach((attr) => {
        const body = attr.slice(7, -1)
        regions.push({ startLine: i + 1, text: body })
      })
    }
  })
  return regions
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) walk(full, out)
    else if (/\.(vue|css|scss)$/.test(name)) out.push(full)
  }
  return out
}

const files = walk(SRC)
const findings = []

for (const file of files) {
  const rel = relative(CLIENT_ROOT, file)
  if (EXEMPT.has(rel)) continue
  const raw = readFileSync(file, 'utf8')
  const ext = file.slice(file.lastIndexOf('.'))
  const isScss = ext === '.scss'

  const regions = ext === '.vue' ? extractVueRegions(raw) : [{ startLine: 1, text: raw }]

  for (const region of regions) {
    const clean = stripComments(region.text, isScss)
    clean.split('\n').forEach((line, idx) => {
      if (!line.trim()) return
      const lineNo = region.startLine + idx
      for (const rule of RULES) {
        if (rule.onlyExt && !rule.onlyExt.includes(ext)) continue
        if (rule.id === 'bare-dimension') {
          // 值级允许项（全局）+ 文件级允许项（组件私有尺寸）+ 边框宽豁免（物理渲染量）
          const allowInFile = ALLOW_FILES.get(rel)
          const matches = line.match(/(?<![\w-])(\d+(?:\.\d+)?)(rpx|px)\b/g) || []
          const borderWidths = new Set(
            (line.match(/(\d+(?:\.\d+)?(?:rpx|px))\s+(?:solid|dashed|dotted|double|none)\b/g) || []).map(
              (s) => s.split(/\s+/)[0],
            ),
          )
          const bad = matches.filter((m) => !ALLOW_VALUES.has(m) && !allowInFile?.has(m) && !borderWidths.has(m))
          if (bad.length) {
            findings.push({ file: rel, line: lineNo, rule: rule.id, label: rule.label, text: line.trim() })
          }
          continue
        }
        if (rule.test(line)) {
          findings.push({ file: rel, line: lineNo, rule: rule.id, label: rule.label, text: line.trim() })
        }
      }
    })
  }
}

const byFile = new Map()
for (const f of findings) {
  if (!byFile.has(f.file)) byFile.set(f.file, [])
  byFile.get(f.file).push(f)
}

const labelOf = (id) => RULES.find((r) => r.id === id).label
const countOf = (list, id) => list.filter((f) => f.rule === id).length

console.log('\n=== UI Token 护栏报告（docs/ui/client/设计变量.md §3） ===\n')
if (!findings.length) {
  console.log('✓ 无违规：裸色值 0 ｜ 裸尺寸 0 ｜ @click 0\n')
} else {
  for (const [file, list] of [...byFile.entries()].sort()) {
    console.log(
      `${file}  →  裸色值 ${countOf(list, 'bare-color')} ｜ 裸尺寸 ${countOf(list, 'bare-dimension')} ｜ @click ${countOf(list, 'at-click')}`,
    )
    for (const f of list.slice(0, 6)) {
      console.log(`    L${f.line}  [${labelOf(f.rule)}]  ${f.text.slice(0, 110)}`)
    }
    if (list.length > 6) console.log(`    … 其余 ${list.length - 6} 处`)
  }
  console.log(
    `\n合计：${findings.length} 处 —— 裸色值 ${countOf(findings, 'bare-color')} ｜ 裸尺寸 ${countOf(findings, 'bare-dimension')} ｜ @click ${countOf(findings, 'at-click')}（涉及 ${byFile.size} 个文件）`,
  )
}

if (!REPORT_ONLY && findings.length) {
  console.log('\n✗ 存在违规（严格模式）。清零前可先跑 `npm run check:ui -- --report` 仅出报告。\n')
  process.exit(1)
}
console.log(REPORT_ONLY ? '\n（报告模式：不影响退出码）\n' : '\n✓ 通过\n')
