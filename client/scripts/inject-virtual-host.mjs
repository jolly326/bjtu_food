/**
 * 构建后注入：为全部自定义组件 JSON 补写 `"options": { "virtualHost": true }`。
 *
 * 背景：uni-app（3.0.0-5020420260813001）不会把 defineOptions / manifest 里的
 * virtualHost 编译进组件 JSON；宿主节点不虚拟化时，页面 / 组件的 flex 链路会被
 * 裸宿主节点打断（卡片无法撑满槽位）。本脚本在构建后统一注入，保证组件根直接参与布局。
 *
 * 触发时机：`postbuild:mp-weixin`（build 后自动执行）；dev 产物目录存在时一并处理。
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const DIST_ROOTS = [
  join(dirname(fileURLToPath(import.meta.url)), '../dist/build/mp-weixin'),
  join(dirname(fileURLToPath(import.meta.url)), '../dist/dev/mp-weixin'),
]

let injected = 0

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) {
      walk(path)
      continue
    }
    if (!name.endsWith('.json')) continue
    const raw = readFileSync(path, 'utf8')
    let json
    try {
      json = JSON.parse(raw)
    } catch {
      continue
    }
    if (json.component !== true) continue
    const options = { ...(json.options ?? {}), virtualHost: true }
    if (json.options?.virtualHost === true) continue
    writeFileSync(path, `${JSON.stringify({ ...json, options }, null, 2)}\n`)
    injected += 1
  }
}

for (const root of DIST_ROOTS) {
  try {
    if (statSync(root).isDirectory()) walk(root)
  } catch {
    // 产物目录不存在（如未跑过 dev）——跳过
  }
}

console.log(`[inject-virtual-host] virtualHost injected into ${injected} component json file(s).`)
