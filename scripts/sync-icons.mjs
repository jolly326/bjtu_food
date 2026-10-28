/**
 * 图标同步：把学生端 `client/src/assets/icons/*.svg` 同步到运营端 `web/src/assets/icons/`。
 *
 * 两端图标同源（Phosphor 精选子集），避免手工双改导致漂移。
 * 用法：node scripts/sync-icons.mjs
 */
import { copyFileSync, mkdirSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(root, 'client/src/assets/icons')
const dest = resolve(root, 'web/src/assets/icons')

mkdirSync(dest, { recursive: true })
const files = readdirSync(src).filter((name) => name.endsWith('.svg'))
for (const name of files) {
  copyFileSync(resolve(src, name), resolve(dest, name))
}
console.log(`✓ 图标已同步 ${files.length} 个：client/src/assets/icons → web/src/assets/icons`)
