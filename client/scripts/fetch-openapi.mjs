/**
 * 抓取后端 OpenAPI 契约 → `client/openapi.json`（契约单一真源的**镜像**）。
 *
 * <p><b>为何需要这一步</b>：`src/types/generated/api.d.ts` 是**生成产物**，
 * 由本脚本的输出 `openapi.json` 派生。契约的<b>真源始终是后端的 VO</b>——
 * 脚本只是把真源导出成前端可消费的 TS 类型，不引入任何手工维护的副本。
 *
 * <p><b>使用</b>：
 * <pre>
 *   1) 启动后端：cd server && uv run uvicorn app.main:app --reload
 *   2) 刷新契约：npm run gen:api:fresh   （= 本脚本 + openapi-typescript）
 *   3) 之后 npm run type-check —— 后端改字段而契约未刷新时会红（见 check:contract）
 * </pre>
 *
 * <p><b>失败即抛</b>：拿不到契约时明确报错退出，不静默产出陈旧类型——
 * 静默失败会让人误以为「类型是最新的」，那比没有契约保障更危险。
 */
import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(__dirname, '..', 'openapi.json')

/** 后端地址：默认本地 8080；可用 API_ORIGIN 覆盖（如云托管 / 局域网联调）。 */
const ORIGIN = process.env.API_ORIGIN ?? 'http://127.0.0.1:8080'
/** 与后端路由前缀保持一致（不含尾斜杠）。当前 FastAPI 无路由前缀。 */
const CONTEXT_PATH = ''
const DOCS_URL = `${ORIGIN}${CONTEXT_PATH}/openapi.json`

const res = await fetch(DOCS_URL)
if (!res.ok) {
  throw new Error(
    `拉取 OpenAPI 契约失败：${DOCS_URL} → HTTP ${res.status}\n` +
      `请先启动后端（cd server && uv run uvicorn app.main:app --reload），或用 API_ORIGIN 指定其他地址。`,
  )
}

const text = await res.text()
try {
  JSON.parse(text)   // 校验是合法 JSON，避免把 HTML 错误页当契约写入
} catch {
  throw new Error(`${DOCS_URL} 返回的不是合法 JSON（可能命中了网关错误页）。`)
}

// 契约快照按 2 空格缩写入库 —— 便于 git diff 阅读；语义与后端一致
writeFileSync(OUT, JSON.stringify(JSON.parse(text), null, 2) + '\n', 'utf8')
const { paths = {}, components = {} } = JSON.parse(text)
console.log(`✓ 契约已更新：${OUT}`)
console.log(`  端点 ${Object.keys(paths).length} 个 / schema ${Object.keys(components.schemas ?? {}).length} 个`)
