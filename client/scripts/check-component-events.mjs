/**
 * 组件事件命名门禁 —— 保证「组件对外事件不与小程序原生事件同名」，即「点一次只跳一次」。
 *
 * 校验项（任一不通过即退出码 1）：
 *  1. 组件 `emit('x')` 的 `x` 不得落在原生事件名集合内；
 *  2. 组件 `defineEmits` 声明的事件名同样不得落在该集合内。
 *
 * 真实事故（本门禁的由来）：`SearchBar`（首页搜索胶囊）曾 `emit('tap')`，页面写
 * `@tap="goToSearch"` 绑在组件标签上。小程序 `tap` 是**会跨自定义组件边界冒泡**的原生事件，
 * 于是「组件主动 emit」与「原生 tap 冒泡到组件节点」各触发一次 ⇒ 点一下搜索框连跳两页搜索页，
 * 用户要连按两次返回。全仓现统一用业务语义名（`enter` / `press` / `back` / `select` / …）。
 *
 * 口径真源：docs/ui/client/公共组件与形态基线.md（组件对外事件命名约定）
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const COMPONENTS_DIR = join(ROOT, 'src/components')

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

const files = vueFiles(COMPONENTS_DIR)
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

console.log(`扫描 ${files.length} 个组件：原生事件名 ${NATIVE_EVENTS.size} 个`)

if (offenders.length > 0) {
  console.error('\n✗ 组件自定义事件与小程序原生事件同名（会导致一次点击触发两次）：')
  for (const line of offenders) console.error(`  · ${line}`)
  console.error('\n  改法：把事件改成业务语义名（如 tap → enter / press），页面同步改绑定名。')
  process.exit(1)
}

console.log('✓ 组件事件命名一致：对外事件均非原生事件名（一次点击 = 一次跳转）')