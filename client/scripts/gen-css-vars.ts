/**
 * gen-css-vars —— UI-03 色值唯一真源生成器（spec §4.2）。
 *
 * 遍历 src/theme/tokens.ts 的 CSS_VARS（键 = CSS 变量名，值引用 COLOR_MAP 同语义键），
 * 生成 src/theme/generated-colors.css 的 `page{…}` 颜色块（生成物受版本控制）。
 *
 * 规则：
 * - 生成物**禁止手工编辑**；改色值只改 tokens.ts，然后运行 `npm run gen:tokens`；
 * - App.vue 仅 `@import './theme/generated-colors.css'`（构建期由 vite/postcss 内联进 app.wxss）；
 * - CI 校验建议：`npm run gen:tokens && git diff --exit-code -- src/theme/generated-colors.css`，
 *   diff 非空 = tokens.ts 与生成物漂移，判定失败。
 *
 * 运行方式：`node scripts/gen-css-vars.ts`（Node 24 原生 type-stripping，无需额外依赖；
 * 文件含 import/export，Node 模块语法检测按 ESM 解析）。
 */
import { writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { CSS_VARS } from '../src/theme/tokens.ts'

const here = dirname(fileURLToPath(import.meta.url))
const outPath = join(here, '..', 'src', 'theme', 'generated-colors.css')

const entries = Object.entries(CSS_VARS)
if (!entries.length) {
  console.error('[gen:tokens] CSS_VARS 为空，拒绝生成（疑似真源被误删）')
  process.exit(1)
}

const lines: string[] = [
  '/* =========================================================================',
  ' * generated-colors.css —— 自动生成，禁止手工编辑（UI-03 色值唯一真源，spec §4.2）',
  ' * 真源：src/theme/tokens.ts 的 CSS_VARS（值引用 COLOR_MAP 同键同值）',
  ' * 再生成：npm run gen:tokens；CI 校验：生成后 git diff --exit-code 本文件',
  ' * ======================================================================== */',
  '',
  'page {',
]
for (const [name, value] of entries) {
  if (!name.startsWith('--')) {
    console.error(`[gen:tokens] 非法键「${name}」：必须以 -- 开头（CSS 变量名）`)
    process.exit(1)
  }
  lines.push(`  ${name}: ${value};`)
}
lines.push('}')
lines.push('')

writeFileSync(outPath, lines.join('\n'), 'utf8')
console.log(`[gen:tokens] 已生成 ${outPath}（${entries.length} 个颜色变量）`)
