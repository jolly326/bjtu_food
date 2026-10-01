/**
 * client 请求层运行时探针（零依赖：`node scripts/probe-request-layer.mjs`）。
 *
 * <p><b>为何要它</b>：静态度量只能回答「代码里有几处调用」，回答不了
 * 「一次进入页面究竟发出多少个网络请求」。本探针把真实的 {@code src/api/http.ts}
 * 在 Node 里跑起来，用桩传输层**数真实传输次数**——这是「重复请求 / 并发未合并」
 * 这类问题唯一可信的口径，也让优化前后（响应缓存 + in-flight 去重）能直接对数。
 *
 * <p><b>怎么把 uni 代码跑在 Node 上</b>（刻意不引入 vitest/vite-node，保持零依赖）：
 * <ol>
 *   <li><b>条件编译预处理</b>：按目标平台 {@code MP-WEIXIN}（生产端）执行
 *       {@code // #ifdef / #ifndef / #endif} 的取舍。esbuild 不认识这类注释，
 *       直译会让两条传输分支同时留在产物里——那时「一次请求」会被数成两次，基线直接失真。</li>
 *   <li><b>路径改写</b>：{@code @/} 别名与无扩展名相对导入改写为 Node 可解析的
 *       {@code .ts} 相对路径（类型导入是 {@code import type}，会被类型擦除，不需要真实存在）。</li>
 *   <li><b>类型擦除</b>：交给 Node 原生 type stripping（无需 babel/esbuild）。</li>
 *   <li><b>平台桩</b>：{@code globalThis.wx.cloud.callContainer} 与 {@code globalThis.uni.*}
 *       用计数器替代，返回固定 {@code {code:200,data}} 外壳。</li>
 * </ol>
 *
 * <p><b>输出</b>：与服务端口径一致的行格式 {@code METRIC|key|value|unit|note}，
 * 末尾附「当前实现」与「优化后应有值」的对照，避免只看数字不知其意。
 */
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync, statSync } from 'node:fs'
import { dirname, join, resolve, relative } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const TMP = join(ROOT, '.probe-tmp')
/** 目标平台：生产形态是微信小程序（走 wx.cloud.callContainer），度量必须按它裁剪 */
const PLATFORM = 'MP-WEIXIN'

/* ==================== 1. 条件编译 + 路径改写 ==================== */

const DIRECTIVE = /^\s*\/\/\s*#\s*(ifdef|ifndef|else|elif|endif)\b\s*(.*)$/

/** 按目标平台执行 uni 条件编译；未命中的块整段丢弃，指令行留空行以保住原始行号 */
function preprocessConditional(source) {
  const kept = []
  // 栈元素 = 「该层是否处于启用状态」；父层 false 时子层一律 false（与 uni 预处理同语义）
  const stack = []
  const active = () => stack.every(Boolean)
  // 必须按 \r?\n 切：本仓是 CRLF，若只切 \n，行尾的 \r 既不被 `.` 匹配也不让 `$` 成立，
  // 指令行会一条都匹配不上——于是 #ifdef / #ifndef 两条分支同时留下，
  // 一次逻辑请求被数成两次传输（首跑就是这样把基线抬高了一倍）。
  for (const line of source.split(/\r?\n/)) {
    const directive = DIRECTIVE.exec(line)
    if (directive) {
      const [, kind, rest] = directive
      const platforms = rest.trim().split(/\s+/).filter(Boolean)
      if (kind === 'ifdef') stack.push(active() && platforms.includes(PLATFORM))
      else if (kind === 'ifndef') stack.push(active() && !platforms.includes(PLATFORM))
      else if (kind === 'endif') stack.pop()
      else stack.push(active()) // else：本仓未使用带 else 的写法，按父层延续处理
      kept.push('')
      continue
    }
    kept.push(active() ? line : '')
  }
  return kept.join('\n')
}

const posix = (p) => p.split(/[\\/]/).join('/')

/** 把 `@/x` 与 `./x` 解析为临时目录内可被 Node 解析的相对路径（补 .ts / .mjs 扩展名） */
function rewriteSpecifiers(source, fileRelDir) {
  return source.replace(/(\bfrom\s*|\bimport\s*\(\s*)(['"])([^'"]+)\2/g, (match, prefix, quote, spec) => {
    let target = null
    if (spec.startsWith('@/')) target = posix(join(TMP, 'src', spec.slice(2)))
    else if (spec.startsWith('./') || spec.startsWith('../')) target = posix(resolve(TMP, 'src', fileRelDir, spec))
    if (!target) return match
    // 401 静默重登的动态导入指向桩实现（真实 store 会拉起 pinia + vue，度量不需要）
    const withExt = target.endsWith('.mjs') ? target + '.mjs' : target + '.ts'
    const relPath = posix(relative(resolve(TMP, 'src', fileRelDir), withExt))
    return `${prefix}${quote}${relPath.startsWith('.') ? relPath : './' + relPath}${quote}`
  })
}

/** 探针入口模块；其余依赖由下面的闭包遍历自动纳入（手写清单必然漏，dish.ts 就间接依赖 utils/money） */
const ENTRIES = ['api/http.ts', 'api/dish.ts', 'api/banner.ts']
/** 只做类型用途的目录：`import type` 会被类型擦除，不需要在运行时真实存在，也不必读进临时目录 */
const SKIP_PREFIXES = ['types/']

/** 抽取一个文件里的所有导入说明符（含动态 import） */
function specifiersOf(source) {
  const list = []
  const re = /\b(?:from|import)\s*\(?\s*(['"])([^'"]+)\1/g
  let match
  while ((match = re.exec(source))) list.push(match[2])
  return list
}

/** 把导入说明符解析为 src/ 下的相对路径；解析不到真实文件时返回 null（包名、类型模块等） */
function toSrcRelative(fromRelPath, spec) {
  const abs = spec.startsWith('@/')
    ? join(SRC, spec.slice(2))
    : spec.startsWith('.')
      ? resolve(SRC, dirname(fromRelPath), spec)
      : null
  if (!abs) return null
  const rel = posix(relative(SRC, abs))
  for (const candidate of [rel, rel + '.ts', rel + '/index.ts']) {
    if (existsSync(join(SRC, candidate)) && statSync(join(SRC, candidate)).isFile()) return candidate
  }
  return null
}

if (existsSync(TMP)) rmSync(TMP, { recursive: true, force: true })
mkdirSync(TMP, { recursive: true })
// 声明 ESM：否则 Node 会先按 CommonJS 试解析再回退（MODULE_TYPELESS_PACKAGE_JSON 警告，
// 且在 PowerShell 下 stderr 会被当作 NativeCommandError，掩盖真实输出）
writeFileSync(join(TMP, 'package.json'), '{ "type": "module" }\n', 'utf8')
const pending = [...ENTRIES]
const queued = new Set()
while (pending.length) {
  const relPath = pending.shift()
  if (queued.has(relPath) || SKIP_PREFIXES.some((p) => relPath.startsWith(p))) continue
  queued.add(relPath)
  const source = readFileSync(join(SRC, relPath), 'utf8')
  for (const spec of specifiersOf(source)) {
    const dep = toSrcRelative(relPath, spec)
    if (dep && !SKIP_PREFIXES.some((p) => dep.startsWith(p))) pending.push(dep)
  }
  const dir = dirname(relPath) === '.' ? '' : dirname(relPath)
  const out = join(TMP, 'src', relPath)
  mkdirSync(dirname(out), { recursive: true })
  // import.meta.env 是 Vite 注入的构建期常量，Node 下不存在 → 统一指向探针注入的环境桩
  const nodeReady = preprocessConditional(source).replace(/import\.meta\.env\b/g, '(globalThis.__PROBE_ENV__ || {})')
  writeFileSync(out, rewriteSpecifiers(nodeReady, posix(dir)), 'utf8')
}
if (process.env.PROBE_DEBUG) console.log(`[probe] 已生成 ${queued.size} 个模块：${[...queued].join(', ')}`)

// 401 分支的静默重登桩（覆盖闭包遍历拷进来的真实 store）：抛错即代表「重登不可用」，
// 探针据此度量 401 的放大倍数；真实 store 会拉起 pinia + vue，度量不需要它们。
mkdirSync(join(TMP, 'src', 'stores'), { recursive: true })
writeFileSync(
  join(TMP, 'src', 'stores', 'user.ts'),
  'export function useUserStore() { return { silentLogin() { return Promise.reject(new Error("probe: no silent login")) } } }\n',
  'utf8',
)

/* ==================== 2. 平台桩：计数发生在真正的传输出口 ==================== */

/** 传输出口计数：key = `METHOD path`，value = 实际发出次数 */
let transports = new Map()
/** 可替换的响应工厂：默认返回 code=200 外壳，个别场景改投 401 / 网络失败 */
let responseFactory = null
const noOp = () => {}
const record = (key) => transports.set(key, (transports.get(key) || 0) + 1)
const totalTransports = () => [...transports.values()].reduce((a, b) => a + b, 0)

/**
 * 让桩与真实平台保持同一调用契约。
 * <p>
 * 这点极易踩错：{@code transportWxCloud} 消费的是 **success/fail 回调**而不是返回值，
 * 桩若只 `return Promise.resolve(...)`，回调永不触发 → 每个请求都干等 12s 超时 →
 * 基线数出来的全是「超时次数」而非「请求次数」（本探针首跑正是这样翻车的）。
 */
function respond(opts, failure = null) {
  record(`${opts.method} ${opts.path || opts.url}`)
  const body = responseFactory
    ? responseFactory(opts.path || opts.url)
    // 默认负载按方法给形态：GET 返回数组（api 层普遍对返回值做 .map / recordsOf），
    // 写操作返回对象——形态错配会让 api 层在传输之后抛错，那样数出来的是「异常前的请求数」。
    : { code: 200, message: 'ok', data: opts.method === 'GET' ? [] : { ok: true } }
  setTimeout(() => {
    if (failure) {
      if (typeof opts.fail === 'function') opts.fail({ errMsg: failure })
      return
    }
    if (typeof opts.success === 'function') opts.success({ statusCode: 200, header: {}, data: body })
  }, 0)
}

globalThis.uni = {
  getStorageSync: () => 'probe-token',
  setStorageSync: noOp,
  removeStorageSync: noOp,
  showToast: noOp,
  showLoading: noOp,
  hideLoading: noOp,
  showModal: ({ success }) => success && success({ confirm: true }),
  reLaunch: noOp,
  navigateTo: noOp,
  // 非微信端传输出口（条件编译后本探针不走这里，保留兜底以防口径漂移）
  request: (opts) => {
    respond(opts)
    return { abort: noOp }
  },
}
globalThis.wx = {
  cloud: {
    callContainer: (opts) => respond(opts, globalThis.__PROBE_FAIL__ || null),
  },
}
globalThis.__PROBE_ENV__ = { VITE_API_BASE_URL: 'http://127.0.0.1:8080/api/v1' }


// 平台桩必须先于 import 就位：http.ts 与 api 模块在 import 阶段就会求值模块级常量
const http = await import(pathToFileURL(join(TMP, 'src', 'api', 'http.ts')).href)
const dishApi = await import(pathToFileURL(join(TMP, 'src', 'api', 'dish.ts')).href)
const bannerApi = await import(pathToFileURL(join(TMP, 'src', 'api', 'banner.ts')).href)
if (process.env.PROBE_DEBUG) console.log('[probe] http.ts 与 api 模块已加载')

/* ==================== 3. 场景 ==================== */

const results = []
async function scenario(key, unit, note, run) {
  transports = new Map()
  responseFactory = null
  try {
    await run()
  } catch {
    // 场景允许上抛（401 / 网络失败）：要度量的是「上抛前真实发出了多少个请求」，异常本身不是结论
  }
  const value = totalTransports()
  results.push({ key, value, unit, note })
  console.log(`METRIC|client.request.${key}|${value}|${unit}|${note}`)
  if (process.env.PROBE_DEBUG) {
    for (const [k, v] of transports) console.log(`    · ${k} × ${v}`)
  }
}

const ids = (...list) => list

try {
  // —— 口径 1：并发同源请求（同一时刻多处组件各自要同一份数据）——
  await scenario('concurrent_same_get', '次', '5 个并发 listDishViews()（同 URL 同参数）；优化后期望=1', async () => {
    await Promise.all(ids(...Array.from({ length: 5 }, () => dishApi.listDishViews())))
  })

  // —— 口径 2：顺序重复请求（页面二次进入 / 返回再进）——
  await scenario('repeated_same_get', '次', '顺序 3 次 listDishViews()（模拟退出再进首页）；优化后期望=1', async () => {
    for (let i = 0; i < 3; i++) await dishApi.listDishViews()
  })

  // —— 口径 3：写操作必须逐次落地（防过度缓存的反向断言）——
  await scenario('concurrent_same_post', '次', '2 个并发同名 POST；写操作不得被合并，优化后仍应为 2', async () => {
    await Promise.all([http.post('/feedback', { content: 'x' }), http.post('/feedback', { content: 'x' })])
  })

  // —— 口径 4：不同参数的 GET 不得被误合并 ——
  await scenario('concurrent_distinct_get', '次', '2 个不同查询参数的 GET；优化后仍应为 2（防误合并）', async () => {
    await Promise.all([dishApi.searchDishes({ meal: 'staple' }), dishApi.searchDishes({ meal: 'snack' })])
  })

  // —— 口径 5：首屏真实序列（进入首页的完整请求面）——
  await scenario('first_screen_home', '次', '首页进入序列 listBanners + listDishViews + searchDishes + listGuessLike', async () => {
    await Promise.all([
      bannerApi.listBanners(),
      dishApi.listDishViews(),
      dishApi.searchDishes({ page: 1, size: 10 }),
      dishApi.listGuessLike('probe-seed'),
    ])
  })

  // —— 口径 6：首页二次进入（keep-alive 之外的真实回访形态）——
  await scenario('first_screen_home_revisit', '次', '同序列再来一遍（返回首页）；两次总量即用户看到的重复成本', async () => {
    const once = async () =>
      Promise.all([
        bannerApi.listBanners(),
        dishApi.listDishViews(),
        dishApi.searchDishes({ page: 1, size: 10 }),
        dishApi.listGuessLike('probe-seed'),
      ])
    await once()
    await once()
  })

  // —— 口径 7：详情页编辑弹层候选值（服务端聚合成本高 + 前端重复拉）——
  await scenario('detail_edit_attributes', '次', '同菜品连续 2 次 listDishEditAttributes(1)；候选值是可缓存字典', async () => {
    await dishApi.listDishEditAttributes(1)
    await dishApi.listDishEditAttributes(1)
  })

  // —— 口径 8：401 引发的重试放大 ——
  await scenario('unauthorized_retry_amplification', '次',
    '响应恒为 401；探针令静默重登不可用 → 只发 1 次。真实环境若静默重登可用则为 2（原始 + 1 次重试）——该放大是登录态竞态的代价，非缓存问题', async () => {
    responseFactory = () => ({ code: 401, message: 'token 失效', data: null })
    await http.get('/dishes/views')
  })

  // —— 口径 9：网络失败的传输成本（是否存在自动重试放大）——
  await scenario('network_failure_attempts', '次', '传输层恒失败（callContainer 走 fail 回调）；期望=1（请求层无隐藏重试放大）', async () => {
    globalThis.__PROBE_FAIL__ = 'request:fail timeout'
    await http.get('/dishes/views')
    globalThis.__PROBE_FAIL__ = null
  })
} finally {
  // PROBE_KEEP=1 时保留 .probe-tmp，便于核对条件编译裁剪结果（生成的 http.ts 应只剩一条传输分支）
  if (!process.env.PROBE_KEEP) rmSync(TMP, { recursive: true, force: true })
}

// 落盘（dist 已被 git 忽略）：与 measure-perf.mjs 同格式，便于优化前后逐行 diff
writeFileSync(
  join(ROOT, 'dist', 'perf-metrics-client-request.tsv'),
  results.map((r) => [`client.request.${r.key}`, r.value, r.unit, r.note].join('\t')).join('\n') + '\n',
  'utf8',
)

const total = results.reduce((sum, r) => sum + r.value, 0)
console.log(`\n合计传输 ${total} 次 / ${results.length} 个场景`)
console.log('说明：concurrent_same_get 与 repeated_same_get 的下降幅度 = 请求层缓存 + in-flight 去重的净收益；')
console.log('     concurrent_same_post / concurrent_distinct_get 必须保持不变，否则说明缓存做过头了。')
// 显式退出：http.ts 的超时/复位定时器（setTimeout 300ms / 12s）会让事件循环继续存活，
// 度量已经打印完毕，不需要等定时器自然到期——否则本探针会「跑完不退出」。
process.exit(0)

