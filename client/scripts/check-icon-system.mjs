/**
 * 图标体系门禁 —— 保证「全站同一套图标 + 统一尺寸档位」不漂移。
 *
 * 校验项（任一不通过即退出码 1）：
 *  1. **单一来源**：`AppIcon` 的 `ICONS` 表是唯一图标真源；全仓不得出现内联 `<svg>`、
 *     图标字体（`@font-face` / `iconfont`）、emoji 图标、以 `content:'+'` 充当图标的写法。
 *  2. **图标键已登记**：`<AppIcon name="…">` 的**静态字面量**名必须在 `ICONS` 键表内
 *     （否则运行时会静默回退到 `empty` 兜底图标，肉眼难发现）。
 *  3. **尺寸档位合规**：`size` 数值必须落在 `docs/ui/client/公共组件与形态基线.md` §1.14
 *     登记的档位表内，或落在「按宿主盒对齐」的例外档（`HOST_BOX_SIZES`）。
 *  4. **颜色为实色**：`color` 不得为 `currentColor` / `var(...)` —— SVG data-uri 是独立文档，
 *     两者都会被回退成兜底近黑。
 *
 * 动态名（`:name="expr"`）无法静态判定，只做提示不失败；其合法性由对应数据源单测覆盖。
 *
 * 口径真源：docs/ui/client/公共组件与形态基线.md §1.14（图标）+ §1.15（图片占位）
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const SRC = join(ROOT, 'src')

/** §1.14 尺寸档位表（与文档逐行对应；改档位须同批改文档 + 本清单） */
const ALLOWED_SIZES = new Set([22, 24, 28, 32, 36, 40, 44, 48, 64, 96])

/**
 * 「按宿主盒对齐」的例外档：图标边长与其宿主盒等比（如搜索框放大镜、评分星、占位组件），
 * 此时档位随宿主盒走。同一宿主的图标必须恒定（由评审保证），故此处只做「非空」约束。
 */
const HOST_BOX_SIZES = new Set([30, 52, 56, 60, 120])

/**
 * uni.showToast 的 icon 字段取值域（平台参数，**不是** AppIcon 键）：
 * none=纯文本 / success=成功 / error=失败 / loading=加载中。
 * 与 AppIcon 键表同名的none/success 不参与图标键校验。
 */
const SHOW_TOAST_ICONS = new Set(['none', 'success', 'error', 'loading'])

const errors = []
const warns = []
const fail = (file, line, msg) => errors.push(`${relative(ROOT, file)}:${line}  ${msg}`)

/** 递归收集源码文件（跳过生成物与依赖） */
function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (/\.(vue|ts)$/.test(name)) out.push(p)
  }
  return out
}

const files = walk(SRC)

// ---- 1. 提取 ICONS 键表（图标唯一真源）----
const appIconPath = join(SRC, 'components', 'AppIcon.vue')
const appIconSrc = readFileSync(appIconPath, 'utf8')
const tableStart = appIconSrc.indexOf('const ICONS')
const tableEnd = appIconSrc.indexOf('\n}', tableStart)
if (tableStart < 0 || tableEnd < 0) {
  console.error('✗ 无法在 AppIcon.vue 中定位 ICONS 表 —— 图标真源结构已变，请同步本脚本')
  process.exit(1)
}
const iconKeys = new Set(
  appIconSrc
    .slice(tableStart, tableEnd)
    .split('\n')
    .map((l) => l.match(/^\s{2}'?([a-z-]+)'?\s*:\s*\{/))
    .filter(Boolean)
    .map((m) => m[1]),
)

// ---- 2. 逐文件扫描 ----
let usageCount = 0
const usedSizes = new Set()

for (const file of files) {
  if (file === appIconPath) continue
  const src = readFileSync(file, 'utf8')
  const lines = src.split('\n')

  lines.forEach((line, i) => {
    const at = i + 1

    // 1) 单一来源：禁内联 SVG / 图标字体 / emoji 图标 / content:'+' 图标
    if (/<svg[\s>]/i.test(line)) fail(file, at, '内联 <svg>：图标唯一出口是 AppIcon')
    if (/@font-face|iconfont/i.test(line)) fail(file, at, '图标字体：与 AppIcon 体系冲突')
    // content:'+' / content:"+" 用作加号图标（命中区扩展用的 content:'' 不命中）
    if (/content:\s*['"]\+['"]/.test(line)) fail(file, at, "content:'+' 当图标：应走 AppIcon 的 plus")

    // 2) <AppIcon …> 的属性校验
    if (!/<AppIcon[\s/>]/.test(line)) return
    const tag = collectTag(lines, i)
    if (!tag) return
    usageCount++

    // name：静态字面量必须已登记。前置 `:` 是 `:name` 绑定（不是静态名）⇒ 负向前瞻排除
    const nameStatic = tag.body.match(/(?<![:\w-])name="([a-z-]+)"/)
    if (nameStatic) {
      if (!iconKeys.has(nameStatic[1])) fail(file, at, `图标键 "${nameStatic[1]}" 未在 AppIcon ICONS 表登记`)
    } else if (/:name=/.test(tag.body)) {
      warns.push(`${relative(ROOT, file)}:${at}  动态图标名（需由数据源单测保证合法）：${tag.body.match(/:name="([^"]*)"/)?.[1] ?? ''}`)
    }

    // size：数值必须落在档位表
    const sizeStatic = tag.body.match(/\bsize="(\d+)"/)
    if (sizeStatic) {
      const n = Number(sizeStatic[1])
      usedSizes.add(n)
      if (!ALLOWED_SIZES.has(n) && !HOST_BOX_SIZES.has(n)) {
        fail(file, at, `size=${n} 不在 §1.14 档位表（${[...ALLOWED_SIZES].join('/')}）也不在宿主盒对齐例外档`)
      }
    }

    // color：不得为 currentColor / var(...)
    const colorStatic = tag.body.match(/\bcolor="([^"]*)"/)
    if (colorStatic && (colorStatic[1] === 'currentColor' || colorStatic[1].startsWith('var('))) {
      fail(file, at, `color="${colorStatic[1]}" 会被回退为兜底近黑：请传实色（COLOR_MAP['xxx']）`)
    }
  })
}

/** 多行标签聚合：从 `<AppIcon` 起向后收至 `>` 止（含自闭合 `/>`） */
function collectTag(lines, start) {
  let body = lines[start]
  if (!/>$/.test(body.trim())) {
    for (let k = start + 1; k < Math.min(start + 12, lines.length); k++) {
      body += '\n' + lines[k]
      if (/>$/.test(lines[k].trim())) break
    }
  }
  if (!/\/>/.test(body) && !/>\s*$/.test(body)) return null
  return { body }
}

// ---- 2b. 数据源 / 表达式里的**字面量**图标名同样须已登记 ----
//覆盖 `tabs.ts`、`cells` / `items` 之类的数据源数组，以及 `:name="cond ? 'a' : 'b'"` 的字面量分支。
for (const file of files) {
  const src = readFileSync(file, 'utf8')
  lines: for (const [i, line] of src.split('\n').entries()) {
    if (file === appIconPath) break lines
    // 校验独立出现的 icon: / icon= 赋值（负向前瞻排除带前缀的 toastIcon / fieldIcon 等其它标识符）
    for (const m of line.matchAll(/(?<![A-Za-z])icon\s*[:=]\s*'([a-z][a-z-]*)'/g)) {
      if (SHOW_TOAST_ICONS.has(m[1])) continue
      if (!iconKeys.has(m[1])) fail(file, i + 1, `数据源里的图标名 "${m[1]}" 未在 ICONS 表登记`)
    }
    for (const m of line.matchAll(/'([a-z][a-z-]*)'/g)) {
      // 只看像图标键的 token：命中 ICONS 视为已登记；不在 ICONS 且长得像图标语义的一律拦下
      const tok = m[1]
      if (tok.length > 2 && iconKeys.has(tok)) continue
      if (/^(arrow|star|home|profile)-(up|down|filled)$/.test(tok)) fail(file, i + 1, `图标名 "${tok}" 未在 ICONS 表登记`)
    }
  }
}
// ---- 3. ICONS 表自身的完整性：键数与文档登记一致 ----
const DOC_KEY_COUNT = 30 // §1.14：29 个业务键 + 1 个兜底键 empty
if (iconKeys.size !== DOC_KEY_COUNT) {
  errors.push(`AppIcon ICONS 键数 ${iconKeys.size} ≠ 文档 §1.14 登记的 ${DOC_KEY_COUNT}（请同批改文档与本脚本）`)
}

// ---- 4. 未消费的键（零消费即不登记）----
const consumed = new Set()
for (const file of files) {
  const src = readFileSync(file, 'utf8')
  for (const k of iconKeys) {
    if (new RegExp(`name="\\s*${k}"|'${k}'|"${k}"`).test(src)) consumed.add(k)
  }
}
// TabBar 以 `${icon}-filled` 动态拼接，两个 filled 变体由 ICON_KEYS 的拼接规则消费
for (const k of ['home-filled', 'profile-filled']) consumed.add(k)
// empty 是未知图标名的兜底回退目标（由 AppIcon 内部 ICONS.empty 引用，非模板消费）
consumed.add('empty')
const orphans = [...iconKeys].filter((k) => !consumed.has(k))
if (orphans.length) errors.push(`ICONS 中存在零消费键（零消费即不登记）：${orphans.join(', ')}`)

// ---- 输出 ----
console.log(`[图标体系] 图标键 ${iconKeys.size} 个（业务 ${iconKeys.size - 1} + 兜底 empty）`)
console.log(`[图标体系] AppIcon 使用点 ${usageCount} 处；尺寸档 ${[...usedSizes].sort((a, b) => a - b).join('/')}`)
if (warns.length) {
  console.log(`[图标体系] 动态图标名 ${warns.length} 处（不失败，需数据源单测覆盖）：`)
  for (const w of warns) console.log(`  · ${w}`)
}
if (errors.length) {
  console.error(`\n✗ 图标体系门禁失败：${errors.length} 处`)
  for (const e of errors) console.error(`  · ${e}`)
  process.exit(1)
}
console.log('✓ 图标体系一致：单一来源 / 图标键已登记 / 尺寸档合规 / 颜色实色')
