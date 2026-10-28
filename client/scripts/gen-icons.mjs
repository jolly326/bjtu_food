#!/usr/bin/env node
/**
 * 由**下载的 SVG 资源**生成着色变体与取址模块。
 *
 *   源：`client/src/assets/icons/*.svg`（Phosphor 官方资源，MIT，见 docs/ui/client/图标资源.md。
 *       取 **Bold** 字重（圆润粗描边，Q 版动森风）；点赞选中态另取 **Fill** 字重 `heart-fill`）
 *   产物：`client/src/assets/icon-variants/<key>--<tone>.svg`（浅色组）
 *         `client/src/assets/icon-variants/<key>--<tone>--dark.svg`（深色组）
 *         + 同名 TS 取址模块
 *
 * 为何按色调预生成：微信 `<image>` 支持 SVG **文件**，但**不支持运行时换色**（`currentColor` 落到黑色）。
 * 为何分主题两组：深色底上浅色组色调对比不足，深色组按深色色调另烘焙（口径见 docs/ui/client/图标资源.md §2）。
 * 为何走 `assets/` 而非 `static/`：`src/static/` 只在 uni-app **构建启动时拷贝一次**，
 * 偶发漏拷会让图标整批 404；`assets/` 由模块图引用，打包器在 dev / build 两种模式下都产出。
 */
import { mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const SRC_DIR = join(ROOT, 'src', 'assets', 'icons')
const OUT_DIR = join(ROOT, 'src', 'assets', 'icon-variants')
const TS_OUT = join(ROOT, 'src', 'theme', 'icons.generated.ts')

/** 浅色色调 -> 色值（与 docs/ui/client/图标资源.md 一致；取自 tokens 真源） */
const TONES = { default: '#8A5A2B', primary: '#FF8A4C', muted: '#C9B49C' }
/** 深色色调 -> 色值（暖棕夜色：正文奶油 / 主色晚霞橙 / 弱化暖灰） */
const DARK_TONES = { default: '#E8D9C4', primary: '#FFA76B', muted: '#A08D78' }

rmSync(OUT_DIR, { recursive: true, force: true })
mkdirSync(OUT_DIR, { recursive: true })

const files = readdirSync(SRC_DIR)
  .filter((n) => n.endsWith('.svg'))
  .sort()
const keys = files.map((f) => f.slice(0, -4))

/**
 * 只打包**源码实际引用**的图标键（+ empty 兜底），避免把整批预置图标塞进首屏产物。
 * 动态图标名（如 TabBar 的 `:name="item.icon"`）必须在其数据源里以字符串字面量出现；
 * 这是 `scripts/check-icon-system.mjs` 已约定的口径。
 */
const SRC_ROOT = join(ROOT, 'src')
const GENERATED_TS = join(SRC_ROOT, 'theme', 'icons.generated.ts')

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (/\.(vue|ts)$/.test(name)) out.push(p)
  }
  return out
}

const used = new Set(['question'])
for (const file of walk(SRC_ROOT)) {
  if (file === GENERATED_TS) continue
  const text = readFileSync(file, 'utf8')
  for (const key of keys) {
    // 只认 AppIcon 静态名、数据源 icon 字段，以及「动态图标名」所在行的字符串字面量
    // （如 MemoryRecordCard 的图标引用），避免把 class="filter" 之类的字符串误当图标
    const re = new RegExp(
      `name="\\s*${key}"|icon\\s*[:=]\\s*['"]${key}['"]|^.*icon.*['"]${key}['"]`,
      'im',
    )
    if (re.test(text)) used.add(key)
  }
}
const generateFiles = files.filter((f) => used.has(f.slice(0, -4)))
const generateKeys = generateFiles.map((f) => f.slice(0, -4))

/**
 * 把单个源 SVG 烘焙成指定色调的变体文件，返回 import 语句与条目。
 *
 * Phosphor 是**填充式**图形（字重烘焙进路径），故变体以 `fill` 着色而非 `stroke`；
 * 微信 `<image>` 不支持运行时换色（`currentColor` 会落到黑色），必须按色调预生成。
 */
function bake(key, raw, tone, color, isDark) {
  const openTag = raw.match(/<svg[^>]*>/)?.[0] ?? ''
  const viewBox = openTag.match(/viewBox="([^"]+)"/)?.[1] ?? '0 0 256 256'
  const m = raw.match(/<svg[^>]*>([\s\S]*)<\/svg>/)
  const body = (m ? m[1] : '').replace(/\s+/g, ' ').trim()
  if (!body) {
    console.warn(`[gen:icons] 警告：${key}.svg 未解析出图元`)
    return null
  }
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="${viewBox}" ` +
    `fill="${color}">${body}</svg>`
  const variant = `${key}--${tone}${isDark ? '--dark' : ''}`
  writeFileSync(join(OUT_DIR, `${variant}.svg`), svg, 'utf8')
  const ident = variant.replace(/[^a-zA-Z0-9]/g, '_')
  return {
    importLine: `import ${ident} from '@/assets/icon-variants/${variant}.svg'`,
    entry: `  ${JSON.stringify(variant)}: ${ident},`,
  }
}

const imports = []
const entries = []
const darkEntries = []
let written = 0

for (const file of generateFiles) {
  const key = file.slice(0, -4)
  const raw = readFileSync(join(SRC_DIR, file), 'utf8')
  for (const [tone, color] of Object.entries(TONES)) {
    const baked = bake(key, raw, tone, color, false)
    if (baked) {
      imports.push(baked.importLine)
      entries.push(baked.entry)
      written++
    }
  }
  for (const [tone, color] of Object.entries(DARK_TONES)) {
    const baked = bake(key, raw, tone, color, true)
    if (baked) {
      imports.push(baked.importLine)
      darkEntries.push(baked.entry)
      written++
    }
  }
}

const ts = `/**
 * 由 \`scripts/gen-icons.mjs\` 生成 —— **请勿手改**。
 * 源资源与获取路径见 docs/ui/client/图标资源.md。
 */
${imports.join('\n')}

/** 浅色色调（默认主题） */
export const ICON_TONES = ${JSON.stringify(TONES, null, 2)} as const

/** 深色色调（data-theme="dark" 下生效） */
export const ICON_TONES_DARK = ${JSON.stringify(DARK_TONES, null, 2)} as const

export type IconTone = keyof typeof ICON_TONES

/** 浅色组：着色变体 -> 打包器资产地址（dev / build 均由模块图产出） */
export const ICON_SRC: Record<string, string> = {
${entries.join('\n')}
}

/** 深色组：着色变体 -> 打包器资产地址 */
export const ICON_SRC_DARK: Record<string, string> = {
${darkEntries.join('\n')}
}

/** 本次被源码引用并生成着色变体的图标键（+ empty 兜底） */
export const ICON_KEYS = [
${generateKeys.map((k) => `  ${JSON.stringify(k)},`).join('\n')}
] as const

export type IconKey = (typeof ICON_KEYS)[number]

/** 取图标资产地址；未知键回退 empty；dark = 取深色色调组 */
export function iconSrc(key: string, tone: IconTone, dark = false): string {
  const safe = (ICON_KEYS as readonly string[]).includes(key) ? key : 'question'
  const table = dark ? ICON_SRC_DARK : ICON_SRC
  return table[\`\${safe}--\${tone}\${dark ? '--dark' : ''}\`]
}
`

writeFileSync(TS_OUT, ts, 'utf8')
console.log(
  `[gen:icons] 使用 ${generateKeys.length}/${keys.length} 键 × ${Object.keys(TONES).length} 色调 × 2 主题 = ${written} 个 SVG -> src/assets/icon-variants/`,
)
