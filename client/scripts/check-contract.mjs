/**
 * client 契约质量门禁（2026-09-29，方案 E）——零依赖，`node scripts/check-contract.mjs`。
 *
 * <p><b>为何要它</b>：client 的类型保障完全建立在「`src/types/generated/api.d.ts`
 * 是当前契约的产物」这一前提上。前提一旦破坏（有人手改生成文件、忘了跑
 * `gen:api:fresh`、re-export 名字打错），`type-check` 仍然全绿，
 * 契约保障静默失效——这正是方案 A 要消灭的那类无感知漂移。
 *
 * <p><b>校验四件事（任一失败即退出码 1）</b>：
 * <ol>
 *   <li><b>生成产物存在且非空</b>：缺失 ⇒ 新成员 clone 后无任何类型保障；</li>
 *   <li><b>re-export 与生成文件一致</b>：`shared.ts` 逐个 re-export 的 VO 名
 *       必须真实存在于 `api.d.ts`（防契约改名后残留悬空导出）；</li>
 *   <li><b>RawRow 未回潮</b>：允许清单外的 `RawRow` 用法即失败
 *       （它是弱类型逃逸通道，每新增一处都会让契约保障出现缺口）；</li>
 *   <li><b>契约 VO 不得在本仓重复手写</b>：`client/` 内若手写了与契约
 *       **字段名 + 可空性全同**的 interface，即为重复声明。</li>
 * </ol>
 *
 * <p><b>范围：仅 client/ 与 server/</b>。`web/` 处于待重构状态（用户已明确
 * 后期自行处理），本脚本<b>刻意不扫描 web</b>——对一个即将重写的目录施加护栏，
 * 只会制造需要二次清理的噪音。
 *
 * <p>本脚本只做**结构性**校验，类型正确性交由 `npm run type-check`。
 * 两者在 CI/提交前都应执行（见 README「质量门禁」）。
 */
import { readFileSync, existsSync, statSync, readdirSync } from 'node:fs'
import { dirname, join, resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const GENERATED = join(ROOT, 'src', 'types', 'generated', 'api.d.ts')
const SHARED = join(ROOT, 'src', 'api', 'shared.ts')
const API_DIR = join(ROOT, 'src', 'api')

const failures = []
const fail = (msg) => failures.push(msg)

/* ---------- 1. 生成产物存在且非空 ---------- */
if (!existsSync(GENERATED)) {
  fail(`生成类型缺失：${relative(ROOT, GENERATED)}\n  → 启动后端后执行 \`npm run gen:api:fresh\``)
} else if (statSync(GENERATED).size === 0) {
  fail(`生成类型为空：${relative(ROOT, GENERATED)}\n  → 执行 \`npm run gen:api:fresh\` 重新生成`)
}

/* ---------- 2. re-export 与生成文件一致 ---------- */
if (existsSync(GENERATED) && existsSync(SHARED)) {
  const generated = readFileSync(GENERATED, 'utf8')
  const shared = readFileSync(SHARED, 'utf8')
  // 形如：export type DishListItemVO = components['schemas']['DishListItemVO']
  const reExports = [...shared.matchAll(/^export type (\w+) = components\['schemas'\]\['(\w+)'\]$/gm)]

  if (reExports.length === 0) {
    fail(`shared.ts 未找到任何契约 re-export（形如 \`export type XxxVO = components['schemas']['XxxVO']\`）。
  → shared.ts 已被改动或契约类型被清空，类型保障失效。`)
  }

  for (const [, local, schema] of reExports) {
    if (local !== schema) {
      fail(`re-export 改名会掩盖契约漂移：本地 ${local} 指向契约 ${schema}（要求同名）`)
    }
    // 生成文件里该 schema 必须有真实定义
    if (!new RegExp(`\\b${schema}:\\s*\\{`).test(generated)) {
      fail(`契约中已不存在 schema「${schema}」→ 重新执行 \`npm run gen:api:fresh\` 后修正 shared.ts 的 re-export`)
    }
  }
}

/* ---------- 3. RawRow 未回潮 ---------- */
// `RawRow`（弱类型兜底载体）已按「零消费即删」自 api/shared.ts 移除，清单现为空：
// 新增弱类型逃逸前先问「为什么不能用 generated 的 XxxVO」——答案通常是「没写」而非「不能」。
const RAWROW_ALLOWLIST = new Set([])

/**
 * 扫描范围：**src/ 全树**（api / stores / composables / pages / types）。
 *
 * <p>2026-09-29 修正：此前只扫 `src/api/*.ts`，而 `RawRow` 同样可以
 * 从 `stores/`（如 review store 直接持有接口行）、`composables/`、`pages/`
 * 流入——那些位置引入弱类型，编译期同样无感知。
 * 数据一旦离开 `api/` 层的归一化边界，契约保障即失效，故必须全树覆盖。
 *
 * <p>**排除**：`.d.ts`（含生成产物 `types/generated/api.d.ts`）与 `.vue`
 * （模板内 TS 表达式由 `vue-tsc` 单独检查，此处只管静态 .ts 模块）。
 */
const SCAN_ROOTS = ['api', 'stores', 'composables', 'pages', 'types', 'utils']

if (existsSync(API_DIR)) {
  for (const sub of SCAN_ROOTS) {
    const dir = join(API_DIR, '..', sub)
    if (!existsSync(dir)) continue

    for (const file of walkTs(dir)) {
      const relPath = relative(ROOT, file).replace(/\\/g, '/')
      if (relPath.endsWith('.d.ts')) continue
      if (RAWROW_ALLOWLIST.has(relPath)) continue

      const src = readFileSync(file, 'utf8')
      // 去掉注释行后再匹配，避免文档提及被误判
      const code = src
        .split('\n')
        .filter((line) => !line.trim().startsWith('*') && !line.trim().startsWith('//') && !line.trim().startsWith('/*'))
        .join('\n')

      if (/\bRawRow\b/.test(code)) {
        const hits = code
          .split('\n')
          .map((l, i) => (/\bRawRow\b/.test(l) ? `${i + 1}: ${l.trim()}` : null))
          .filter(Boolean)
        fail(`${relPath} 出现 RawRow 用法（弱类型逃逸通道，会造成契约保障缺口）：
  ${hits.join('\n  ')}
  → 改用 api/shared.ts re-export 的强类型（见 types/generated/api.d.ts）。`)
      }
    }
  }
}

/** 递归收集目录下的 .ts 文件 */
function walkTs(dir) {
  const out = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'generated' || entry.name === 'node_modules') continue
      out.push(...walkTs(full))
    } else if (entry.name.endsWith('.ts')) {
      out.push(full)
    }
  }
  return out
}

/* ---------- 4. 契约 VO 不得在本仓重复手写 ---------- */
// 契约已有单一真源（generated/api.d.ts），端上再手写一份等价 interface
// 等于凭空造出第二真源——后端改字段时，副本不会跟着变，且**无任何编译期提示**。
// client 的 api 层只应做「有业务语义的归一」（分→元、别名、零值兜底），
// 而非复制一份字段清单。
//
// ⚠️ **只拦「纯镜像」**：判据是「字段名与可空性都与契约 VO 完全一致」。
// client 存在合法的**归一化展示模型**（如 `DishListItem`：把 `canteenName`→`canteen`、
// `avgRating`→`rating`、分→元、零值兜底），那是有业务语义的适配、不属重复声明，
// 拦了反而逼人写 `as any`。判据同时比较**可空性**，正是为了把这类适配放行。

/** 提取 interface 体内的字段名 → 是否可选（仅顶层、忽略嵌套与注释） */
function interfaceFields(src, name) {
  const start = src.search(new RegExp(`^export interface ${name}\\s*\\{`, 'm'))
  if (start < 0) return null
  const body = src.slice(start)
  let depth = 0
  let end = 0
  for (let i = body.indexOf('{'); i < body.length; i++) {
    if (body[i] === '{') depth++
    else if (body[i] === '}') {
      depth--
      if (depth === 0) { end = i; break }
    }
  }
  const inner = body.slice(0, end)
  // 去掉块注释与行注释后再取字段名
  const cleaned = inner.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')
  const fields = new Map()
  for (const line of cleaned.split('\n')) {
    const m = line.match(/^\s{2}([A-Za-z_]\w*)(\??)\s*[:?]/)
    if (m) fields.set(m[1], m[2] === '?')   // value = 是否可选
  }
  return fields
}

/** 契约 VO 的字段名 → 是否可选（读 generated 的 properties） */
function contractVoFields(generated, name) {
  // ⚠️ generated 是 **TS 语法**：块尾是 `    };`（分号结尾），字段是 `f?: T;`（分号结尾），
  //    既不是 JSON 也不是无分号的 TS 早期写法。
  //    这两处都曾写错，导致本判据**静默永不匹配**（负向测试才暴露出来）——
  //    护栏自身失效比没有护栏更危险，故此处把形态假设显式注释出来。
  const re = new RegExp(`^    ${name}: \\{([\\s\\S]*?)^    \\};`, 'm')
  const m = generated.match(re)
  if (!m) return null
  const cleaned = m[1].replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')
  const fields = new Map()
  for (const m2 of cleaned.matchAll(/^\s{6}([A-Za-z_]\w*)(\??)\s*:/gm)) {
    fields.set(m2[1], m2[2] === '?')
  }
  return fields
}

if (existsSync(API_DIR) && existsSync(GENERATED)) {
  const generated = readFileSync(GENERATED, 'utf8')

  // 扫描范围与第 3 条（RawRow）**保持一致**：
  // 2026-10-01 修正 —— 此前只扫 `src/api/`，而 `src/types/`（`feedback.ts` / `dish.ts` 等）
  // 同样会手写契约副本，那里的重复声明可完全逃逸。现改为全树，两条护栏口径统一。
  const files = []
  for (const sub of SCAN_ROOTS) {
    const dir = join(API_DIR, '..', sub)
    if (!existsSync(dir)) continue
    for (const f of walkTs(dir)) {
      if (f.endsWith('.d.ts')) continue
      files.push(f)
    }
  }

  for (const file of files) {
    const relPath = relative(ROOT, file).replace(/\\/g, '/')
    if (relPath === 'src/api/shared.ts') continue   // re-export 出口本身，非重复声明
    const src = readFileSync(file, 'utf8')

    for (const m of src.matchAll(/^export interface (\w+)\s*\{/gm)) {
      const name = m[1]
      // 合法：已改为 type X = YxxVO 别名
      if (new RegExp(`^export type ${name} = `, 'm').test(src)) continue

      const local = interfaceFields(src, name)
      const vo = contractVoFields(generated, name)
      if (!local || !vo) continue

      if (local.size !== vo.size) continue
      // 字段名全同？可空性也须全同 —— 否则就是**归一化展示模型**（合法）：
      // 典型如 client 的 `ReportReason`：契约字段全 `?`（后端 record 分量可空），
      // 端上在 api 层一次性兜底成必填，这正是归一函数的职责。
      const namesMatch = [...local.keys()].every((f) => vo.has(f))
      if (!namesMatch) continue

      const nullabilityDiff = [...local.entries()].filter(([f, opt]) => vo.get(f) !== opt)
      if (nullabilityDiff.length > 0) {
        // 可空性有别 = 归一化展示模型，放行
        continue
      }

      // 字段名与可空性**都**一致 ⇒ 纯镜像，属重复声明
      fail(`${relPath} 手写了与契约完全等价的 interface「${name}」——契约有单一真源，重复声明必然漂移。
  → 改为复用 shared.ts 的契约类型（\`import type { ${name} } from './shared'\`），
    字段有差异的部分保留在本模块的归一函数里。
  · 注：字段名或可空性有差异的展示模型是合法归一化适配，不受本条约束。`)
    }
  }
}

/* ---------- 汇总 ---------- */
if (failures.length > 0) {
  console.error('✗ 契约质量门禁未通过：\n')
  failures.forEach((f, i) => console.error(`${i + 1}. ${f}\n`))
  process.exit(1)
}

console.log('✓ 契约质量门禁通过：生成产物存在、re-export 与契约一致、RawRow 未回潮。')
