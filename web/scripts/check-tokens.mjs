#!/usr/bin/env node
/**
 * 悬空 design token 引用检测（管理端）。
 *
 * <p><b>为什么需要</b>：CSS 里引用一个**未声明**的 token（如 `var(--radius-circle)`）时，
 * 浏览器会**静默丢弃整条声明**，页面照常渲染但样式失效 —— 静态类型检查与 ESLint 都拦不住。
 * 线上实测已因此产生两例缺陷：「用户头像渲染成直角方块」（`--radius-circle` 未声明）、
 * 「Tab 激活态失去主色底」（`--color-primary-soft` 未声明，静默回落灰底）。
 *
 * <p><b>判据</b>：`web/src/**` 内所有 `var(--x)` 引用的 `--x`，必须在 `web/src/**` 内
 * **有声明**（`--x:` 形式；含组件内局部声明与 Element Plus 覆写，如 `.el-switch { --el-switch-* }`）。
 * 声明集合与引用集合同源扫描 ⇒ 无「白名单」维护成本。
 *
 * <p>附带信息（不判定失败）：`variables.css` 中**零引用**的 token 会被列出，
 * 供「零消费即删」人工复核。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC_DIR = fileURLToPath(new URL('../src/', import.meta.url))
const ROOT = fileURLToPath(new URL('..', import.meta.url))
const EXTS = new Set(['.vue', '.ts', '.js', '.css', '.scss'])
/** 生成物 / 类型产物不参与扫描 */
const SKIP_DIRS = new Set(['types'])

/** `--name:`（声明）；`['"]?` 兼收 `:style` 内联注入的带引号写法（如 `'--clamp-lines': '2'`）*/
const DECL_RE = /--([a-z0-9-]+)['"]?\s*:/gi
/** `var(--name)`（引用）*/
const USE_RE = /var\(\s*--([a-z0-9-]+)/gi

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    const st = statSync(path)
    if (st.isDirectory()) {
      if (!SKIP_DIRS.has(name)) walk(path, out)
    } else if (EXTS.has(extname(path))) {
      out.push(path)
    }
  }
  return out
}

const files = walk(SRC_DIR)
const declared = new Map() // name -> 首个声明位置
const used = new Map() // name -> 首个引用位置

for (const file of files) {
  const text = readFileSync(file, 'utf8')
  const lines = text.split(/\r?\n/)
  const rel = relative(ROOT, file).replace(/\\/g, '/')

  lines.forEach((line, i) => {
    for (const m of line.matchAll(DECL_RE)) {
      const name = m[1].toLowerCase()
      if (!declared.has(name)) declared.set(name, `${rel}:${i + 1}`)
    }
    for (const m of line.matchAll(USE_RE)) {
      const name = m[1].toLowerCase()
      if (!used.has(name)) used.set(name, `${rel}:${i + 1}`)
    }
  })
}

const dangling = [...used.entries()].filter(([name]) => !declared.has(name))
/** 仅核对声明真源（variables.css）的零消费情况，避免组件局部变量噪声 */
const variablesCss = join(SRC_DIR, 'styles', 'variables.css')
const variablesText = readFileSync(variablesCss, 'utf8')
const orphans = [...variablesText.matchAll(DECL_RE)]
  .map((m) => m[1].toLowerCase())
  .filter((name, i, arr) => arr.indexOf(name) === i)
  .filter((name) => !used.has(name))

console.log(
  `扫描 ${files.length} 个文件：声明 ${declared.size} 个 token，引用 ${used.size} 个 token`,
)

if (orphans.length > 0) {
  console.log(
    `\nℹ️ variables.css 中零引用（建议按「零消费即删」复核，非阻断）：\n  ${orphans.join('\n  ')}`,
  )
}

if (dangling.length > 0) {
  console.error(
    `\n✗ 悬空 token 引用 ${dangling.length} 处（声明会被静默丢弃，样式失效）：\n` +
      dangling.map(([name, where]) => `  ${where}  var(--${name})`).join('\n'),
  )
  process.exit(1)
}

console.log('\n✓ 无悬空 token 引用')
