/**
 * client 性能与整洁度度量（零依赖，`node scripts/measure-perf.mjs`）。
 *
 * <p><b>为何要它</b>：性能优化最容易滑进「感觉变快了」。本脚本把可静态确定的项
 * 全部量化成同一行格式（`METRIC|key|value|unit|note`）的数值，使优化前后两轮
 * 用同一条命令复现，数值可直接抄进性能对比文档。
 *
 * <p><b>三类口径</b>：
 * <ol>
 *   <li><b>产物体积</b>：读 `dist/build/mp-weixin`（须先 `npm run build:mp-weixin`），
 *       按 `src/pages.json` 的 `subPackages[].root` 切分主包 / 各分包。
 *       ⚠️ 是**未压缩源文件字节数**，与微信开发者工具显示的「包体积」（含其自身压缩与统计口径）
 *       不是一回事——本口径只用于**同口径自比**，不用于替代上传前的官方包体积核对。</li>
 *   <li><b>代码结构与规模</b>：源码行数、清单式深响应容器用量、`shallowRef` 用量、
 *       图片懒加载覆盖率、`console.*` 残留、未使用导出数。</li>
 *   <li><b>请求面</b>：静态清点页面上的 API 调用点，并单列「字典类端点」调用点
 *       （字典类 = 全站共享、变化极慢的那几个 GET，是缓存收益的主要来源）。</li>
 * </ol>
 *
 * <p><b>静态口径的边界</b>：请求「次数」的真相只能在运行时数（见
 * {@code scripts/probe-request-layer.mjs}）；本脚本的调用点计数只回答「代码里存在几处调用」，
 * 用来说明合并 / 缓存的**作用面有多大**，不能当作实际发出的请求数。
 */
import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join, resolve, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const DIST = join(ROOT, 'dist', 'build', 'mp-weixin')
const OUT = join(ROOT, 'dist', 'perf-metrics.tsv')

const metrics = []
/** 记录一个度量项：同时进 stdout 行与内存表（末尾统一落盘） */
const emit = (key, value, unit, note) => {
  const text = Number.isInteger(value) ? String(value) : value.toFixed(3)
  metrics.push({ key, value: text, unit, note })
  console.log(`METRIC|${key}|${text}|${unit}|${note}`)
}

/** 递归列出目录内文件（可按扩展名与排除目录过滤） */
function walk(dir, extensions = null, excludeDirs = []) {
  const out = []
  if (!existsSync(dir)) return out
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      if (excludeDirs.includes(entry)) continue
      out.push(...walk(full, extensions, excludeDirs))
    } else if (!extensions || extensions.includes(entry.slice(entry.lastIndexOf('.')))) {
      out.push(full)
    }
  }
  return out
}

const read = (file) => readFileSync(file, 'utf8')
const rel = (file) => relative(ROOT, file).split(sep).join('/')
/** 统计正则在文本中的命中次数（逐行匹配，避免一次性大正则回溯） */
function countMatches(text, pattern) {
  let total = 0
  for (const line of text.split('\n')) {
    const matches = line.match(new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : pattern.flags + 'g'))
    if (matches) total += matches.length
  }
  return total
}

/* ==================== 1. 产物体积 ==================== */

const pagesJson = read(join(SRC, 'pages.json'))
/** 分包根目录（形如 `pages/detail/`）：主包 = 产物总量减去这些目录下的文件 */
const subpackageRoots = [...pagesJson.matchAll(/"root"\s*:\s*"([^"]+)"/g)].map((m) => m[1])

if (existsSync(DIST)) {
  const distFiles = walk(DIST)
  const kb = (files) => files.reduce((sum, f) => sum + statSync(f).size, 0) / 1024
  emit('client.bundle.total_kb', kb(distFiles), 'KB', `${distFiles.length} 个文件（未压缩源文件口径）`)
  let subTotal = 0
  for (const root of subpackageRoots) {
    const dirName = root.replace(/\/+$/, '').split('/').pop()
    const files = distFiles.filter((f) => rel(f).includes(`/${dirName}/`))
    const size = kb(files)
    subTotal += size
    emit(`client.bundle.subpackage.${dirName}_kb`, size, 'KB', `root=${root}；${files.length} 个文件`)
  }
  emit('client.bundle.main_kb', kb(distFiles) - subTotal, 'KB', '主包 = 产物总量 − 全部分包')
  const largest = distFiles
    .map((f) => ({ f, size: statSync(f).size }))
    .sort((a, b) => b.size - a.size)[0]
  emit('client.bundle.largest_file_kb', largest.size / 1024, 'KB', rel(largest.f))
} else {
  emit('client.bundle.total_kb', 0, 'KB', 'dist 不存在——先跑 npm run build:mp-weixin 再度量')
}

/* ==================== 2. 代码结构与规模 ==================== */

/** 源码统计范围：排除类型生成物与静态资源（二者不由本仓库的手工代码决定） */
const SOURCE_EXTENSIONS = ['.ts', '.vue', '.scss', '.json']
const sourceFiles = walk(SRC, SOURCE_EXTENSIONS, ['generated', 'static'])
const sourceTexts = new Map(sourceFiles.map((f) => [f, read(f)]))

let totalLines = 0
for (const text of sourceTexts.values()) {
  totalLines += text.split('\n').length
}
emit('client.src.loc', totalLines, '行', `${sourceFiles.length} 个源码文件（排除 generated/static）`)

/* —— 深响应容器 vs 浅响应：列表态是深响应开销最集中的地方 —— */
let deepListRefs = 0
let shallowRefs = 0
let computedCount = 0
let watchCount = 0
for (const [file, text] of sourceTexts) {
  if (file.endsWith('.json') || file.endsWith('.scss')) continue
  deepListRefs += countMatches(text, /\bref<[A-Za-z][\w<>\[\]| ]*\[\s*\]>\s*\(/g) + countMatches(text, /\bref\s*\(\s*\[\s*\]\s*\)/g)
  shallowRefs += countMatches(text, /\bshallowRef\s*\(/g)
  computedCount += countMatches(text, /\bcomputed\s*\(/g)
  watchCount += countMatches(text, /\bwatch(?:Effect)?\s*\(/g)
}
emit('client.vue.deep_list_refs', deepListRefs, '处', 'ref<数组>（逐元素深层代理；大列表首要优化位）')
emit('client.vue.shallow_ref_usages', shallowRefs, '处', '浅响应容器用量')
emit('client.vue.computed_usages', computedCount, '处', 'computed 用量')
emit('client.vue.watch_usages', watchCount, '处', 'watch/watchEffect 用量（每处都是一个响应式订阅）')

/* —— 图片懒加载覆盖率（小程序长列表首屏解码成本） —— */
let imageTags = 0
let imageLazyTags = 0
for (const text of sourceTexts.values()) {
  imageTags += countMatches(text, /<image[\s>]/g)
  imageLazyTags += countMatches(text, /<image[^>]*lazy-load/g)
}
emit('client.image.tags', imageTags, '处', '模板内 <image> 总数')
emit('client.image.lazy_load_coverage', imageTags ? (imageLazyTags / imageTags) * 100 : 0, '%', `${imageLazyTags}/${imageTags} 带 lazy-load`)

/* —— 日志残留（生产端 console 在小程序里是真实开销且会外泄内部结构） —— */
let consoleCalls = 0
for (const text of sourceTexts.values()) {
  consoleCalls += countMatches(text, /\bconsole\.(log|warn|error|info|debug)\s*\(/g)
}
emit('client.console_calls', consoleCalls, '处', '源码内 console.* 调用点')

/* ==================== 3. 未使用导出（死代码面） ==================== */

/** 声明式导出（`export const/function/class/type/interface/enum NAME`） */
const DECL_EXPORT = /^\s*export\s+(?:default\s+)?(?:async\s+)?(const|let|function|class|enum|interface|type)\s+([A-Za-z_$][\w$]*)/
/** 聚合式导出（`export { a, b as c }`） */
const BARREL_EXPORT = /^\s*export\s*\{([^}]*)\}/

/** 导出名 → 声明所在文件 */
const exportsByName = new Map()
for (const [file, text] of sourceTexts) {
  if (!file.endsWith('.ts') || file.endsWith('.d.ts') || rel(file).includes('/api/shared.ts')) continue
  for (const line of text.split('\n')) {
    const decl = DECL_EXPORT.exec(line)
    if (decl) exportsByName.set(decl[2], file)
    const barrel = BARREL_EXPORT.exec(line)
    if (barrel) {
      for (const part of barrel[1].split(',')) {
        const name = part.trim().split(/\s+as\s+/).pop()
        if (name && /^[A-Za-z_$][\w$]*$/.test(name)) exportsByName.set(name, file)
      }
    }
  }
}

const unusedExports = []
for (const [name, declarationFile] of exportsByName) {
  const usage = /\b{name}\b/.source.replace('{name}', name)
  let hits = 0
  for (const [file, text] of sourceTexts) {
    if (file === declarationFile) continue
    hits += countMatches(text, new RegExp(usage, 'g'))
  }
  if (hits === 0) unusedExports.push(`${name}@${rel(declarationFile).replace('src/', '')}`)
}
emit('client.unused_exports', unusedExports.length, '个', unusedExports.length ? unusedExports.join(', ') : '无清单外死导出')

/* ==================== 4. 请求面（静态调用点） ==================== */

/** api 层导出的请求函数（页面侧每有一处调用 = 一次潜在网络往返） */
const apiFunctions = []
for (const [file, text] of sourceTexts) {
  if (!rel(file).startsWith('src/api/') || file.endsWith('.d.ts') || rel(file).includes('shared.ts')) continue
  for (const line of text.split('\n')) {
    const decl = DECL_EXPORT.exec(line)
    if (decl && (decl[1] === 'function' || decl[1] === 'const')) apiFunctions.push({ name: decl[2], file })
  }
}

/** 字典类端点：全站共享、变化极慢的 GET——请求合并与响应缓存的主要收益来源 */
const DICTIONARY_API = new Set(['listBanners', 'listDishViews', 'listReportReasons', 'listDishEditAttributes', 'listGuessLike'])

let apiCallSites = 0
let dictionaryCallSites = 0
for (const { name, file } of apiFunctions) {
  let sites = 0
  for (const [otherFile, text] of sourceTexts) {
    if (otherFile === file) continue
    sites += countMatches(text, new RegExp(`\\b${name}\\s*\\(`, 'g'))
  }
  apiCallSites += sites
  if (DICTIONARY_API.has(name)) dictionaryCallSites += sites
}
emit('client.api.call_sites', apiCallSites, '处', `${apiFunctions.length} 个 api 请求函数在页面/组件/Store 内的调用点总数`)
emit('client.api.dictionary_call_sites', dictionaryCallSites, '处',
  `字典类端点调用点（${[...DICTIONARY_API].join('/')}），缓存与合并的作用面`)

/* ==================== 落盘（dist 已被 git 忽略，不污染工作区） ==================== */

if (!existsSync(join(ROOT, 'dist'))) mkdirSync(join(ROOT, 'dist'))
writeFileSync(OUT, metrics.map((m) => [m.key, m.value, m.unit, m.note].join('\t')).join('\n') + '\n', 'utf8')
console.log(`\n度量已写入 ${rel(OUT)}（${metrics.length} 项）`)

