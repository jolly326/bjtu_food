#!/usr/bin/env node
/**
 * 由**下载的 SVG 资源**生成着色变体与取址模块。
 *
 *   源：`client/src/assets/icons/*.svg`（Lucide 官方资源，见 docs/ui/client/图标资源.md）
 *   产物：`client/src/assets/icon-variants/<key>--<tone>.svg`（按色调烘焙 stroke 色）+ 同名 TS 取址模块
 *
 * 为何按色调预生成：微信 `<image>` 支持 SVG **文件**，但**不支持运行时换色**（`currentColor` 落到黑色）。
 * 为何走 `assets/` 而非 `static/`：`src/static/` 只在 uni-app **构建启动时拷贝一次**，
 * 偶发漏拷会让图标整批 404；改为模块图引用的资产后，由打包器在 dev / build 两种模式下都产出。
 */
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const SRC_DIR = join(ROOT, 'src', 'assets', 'icons')
const OUT_DIR = join(ROOT, 'src', 'assets', 'icon-variants')
const TS_OUT = join(ROOT, 'src', 'theme', 'icons.generated.ts')

/** 色调 -> 色值（与 docs/ui/client/图标资源.md 一致；取自 tokens 真源） */
const TONES = { default: '#7F6A55', primary: '#B4531A', muted: '#B5A594' }

rmSync(OUT_DIR, { recursive: true, force: true })
mkdirSync(OUT_DIR, { recursive: true })

const files = readdirSync(SRC_DIR)
  .filter((n) => n.endsWith('.svg'))
  .sort()
const keys = files.map((f) => f.slice(0, -4))

const imports = []
const entries = []
let written = 0

for (const file of files) {
  const key = file.slice(0, -4)
  const raw = readFileSync(join(SRC_DIR, file), 'utf8')
  const m = raw.match(/<svg[^>]*>([\s\S]*)<\/svg>/)
  const body = (m ? m[1] : '').replace(/\s+/g, ' ').trim()
  if (!body) {
    console.warn(`[gen:icons] 警告：${file} 未解析出图元`)
    continue
  }
  for (const [tone, color] of Object.entries(TONES)) {
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" ` +
      `fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ` +
      `preserveAspectRatio="none">${body}</svg>`
    const variant = `${key}--${tone}`
    writeFileSync(join(OUT_DIR, `${variant}.svg`), svg, 'utf8')
    const ident = variant.replace(/[^a-zA-Z0-9]/g, '_')
    imports.push(`import ${ident} from '@/assets/icon-variants/${variant}.svg'`)
    entries.push(`  ${JSON.stringify(variant)}: ${ident},`)
    written++
  }
}

const ts = `/**
 * 由 \`scripts/gen-icons.mjs\` 生成 —— **请勿手改**。
 * 源资源与获取路径见 docs/ui/client/图标资源.md。
 */
${imports.join('\n')}

export const ICON_TONES = ${JSON.stringify(TONES, null, 2)} as const

export type IconTone = keyof typeof ICON_TONES

/** 着色变体 -> 打包器资产地址（dev / build 均由模块图产出） */
export const ICON_SRC: Record<string, string> = {
${entries.join('\n')}
}

/** 已下载并生成着色变体的图标键（= src/assets/icons/*.svg 文件名） */
export const ICON_KEYS = [
${keys.map((k) => `  ${JSON.stringify(k)},`).join('\n')}
] as const

export type IconKey = (typeof ICON_KEYS)[number]

/** 取图标资产地址；未知键回退 empty */
export function iconSrc(key: string, tone: IconTone): string {
  const safe = (ICON_KEYS as readonly string[]).includes(key) ? key : 'empty'
  return ICON_SRC[\`\${safe}--\${tone}\`]
}
`

writeFileSync(TS_OUT, ts, 'utf8')
console.log(`[gen:icons] ${keys.length} 键 × ${Object.keys(TONES).length} 色调 = ${written} 个 SVG -> src/assets/icon-variants/`)
