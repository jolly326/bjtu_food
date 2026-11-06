/**
 * 组件事件命名门禁 —— 保证「组件对外事件不与小程序原生事件同名」，即「点一次只跳一次」。
 *
 * 校验项（任一不通过即退出码 1）：
 *  1. 组件 `emit('x')` 的 `x` 不得落在原生事件名集合内；
 *  2. 组件 `defineEmits` 声明的事件名同样不得落在该集合内。
 *
 * 扫描范围：`src/components/` 公共组件 + 各页面下的 `components/` 私有组件。
 *
 * 为什么要它：组件若 `emit('tap')`，页面把 `@tap` 绑在组件标签上，小程序 `tap` 是**会跨自定义
 * 组件边界冒泡**的原生事件，于是「组件主动 emit」与「原生 tap 冒泡到组件节点」各触发一次
 * ⇒ 一次点击触发两次跳转。全仓统一用业务语义名（`enter` / `press` / `back` / `select` / …）。
 *
 * 口径真源：docs/ui/client/菜品首页.md（组件对外事件命名约定）
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const PAGES_DIR = join(ROOT, 'src/pages')

/** 小程序原生事件名（跨自定义组件边界冒泡）——组件自定义事件与之同名即双触发 */
const NATIVE_EVENTS = new Set([
  'tap',
  'longpress',
  'longtap',
  'touchstart',
  'touchmove',
  'touchend',
  'touchcancel',
  'confirm',
  'input',
  'change',
  'submit',
  'reset',
  'scroll',
  'scrolltolower',
  'scrolltoupper',
  'blur',
  'focus',
  'columnchange',
  'linechange',
  'keyboardheightchange',
])

/** 递归收集 `.vue` 文件 */
function vueFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return vueFiles(full)
    return name.endsWith('.vue') ? [full] : []
  })
}

/** 公共组件目录 + 各页面私有组件目录 */
function componentDirs() {
  const dirs = [join(ROOT, 'src/components')]
  if (!existsSync(PAGES_DIR)) return dirs
  for (const name of readdirSync(PAGES_DIR)) {
    const pageDir = join(PAGES_DIR, name)
    if (!statSync(pageDir).isDirectory()) continue
    const candidate = join(pageDir, 'components')
    if (existsSync(candidate)) dirs.push(candidate)
  }
  return dirs
}

const dirs = componentDirs()
const files = dirs.flatMap(vueFiles)
const offenders = []

for (const file of files) {
  const src = readFileSync(file, 'utf8')
  for (const m of src.matchAll(/emit\(\s*'([A-Za-z][\w:-]*)'/g)) {
    if (NATIVE_EVENTS.has(m[1])) {
      offenders.push(`${relative(ROOT, file)} → emit('${m[1]}')`)
    }
  }
  for (const m of src.matchAll(/\(\s*e:\s*'([A-Za-z][\w:-]*)'\s*\)/g)) {
    if (NATIVE_EVENTS.has(m[1])) {
      offenders.push(`${relative(ROOT, file)} → defineEmits '${m[1]}'`)
    }
  }
}

console.log(`扫描 ${files.length} 个组件（${dirs.length} 个目录）：原生事件名 ${NATIVE_EVENTS.size} 个`)

if (offenders.length > 0) {
  console.error('\n✗ 组件自定义事件与小程序原生事件同名（会导致一次点击触发两次）：')
  for (const line of offenders) console.error(`  · ${line}`)
  console.error('\n  改法：把事件改成业务语义名（如 tap → enter / press），页面同步改绑定名。')
  process.exit(1)
}

console.log('✓ 组件事件命名一致：对外事件均非原生事件名（一次点击 = 一次跳转）')
