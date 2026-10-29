/**
 * 一次性契约快照同步（本机无 Python，无法跑 gen:api:fresh；刷新链路见 docs/README.md 变更流程）。
 *
 * 对齐后端代码当前事实：
 * 1. `/admin/dishes` GET：query 参数 `status` → `activate`（boolean），新增 `name`（名称模糊匹配）；
 * 2. `DishOut` / `DishSaveIn`：`status` → `activate`（boolean，SaveIn 缺省 true）；
 * 3. `DishIssueOut`：新增 `dishFeedbackCount`（该菜当前待处理纠错条数）。
 *
 * ⚠️ 仅为临时手段：等有 Python 环境后跑 `npm run gen:api:fresh` 重新抓取即可覆盖本文件。
 */
import { readFileSync, writeFileSync } from 'node:fs'

const path = new URL('../openapi.json', import.meta.url)
const doc = JSON.parse(readFileSync(path, 'utf8'))

/* 1) /admin/dishes GET 参数 */
const get = doc.paths['/admin/dishes'].get
get.parameters = get.parameters.filter((p) => p.name !== 'status')
get.parameters.splice(2, 0, {
  name: 'activate',
  in: 'query',
  required: false,
  schema: { anyOf: [{ type: 'boolean' }, { type: 'null' }], title: 'Activate' },
})
get.parameters.splice(4, 0, {
  name: 'name',
  in: 'query',
  required: false,
  description: '名称模糊匹配',
  schema: {
    anyOf: [{ type: 'string' }, { type: 'null' }],
    title: 'Name',
    maxLength: 64,
    minLength: 1,
  },
})

/* 2) DishOut / DishSaveIn：status → activate */
const dishOut = doc.components.schemas.DishOut
dishOut.properties.activate = { title: 'Activate', type: 'boolean' }
delete dishOut.properties.status
dishOut.required = dishOut.required.map((k) => (k === 'status' ? 'activate' : k))

const dishSaveIn = doc.components.schemas.DishSaveIn
dishSaveIn.properties.activate = { default: true, title: 'Activate', type: 'boolean' }
delete dishSaveIn.properties.status
dishSaveIn.required = (dishSaveIn.required ?? []).map((k) => (k === 'status' ? 'activate' : k))

/* 3) DishIssueOut：+ dishFeedbackCount */
const issueOut = doc.components.schemas.DishIssueOut
issueOut.properties.dishFeedbackCount = { title: 'Dishfeedbackcount', type: 'integer' }
issueOut.required = [...issueOut.required, 'dishFeedbackCount']

writeFileSync(path, JSON.stringify(doc, null, 2) + '\n', 'utf8')
console.log('✓ openapi.json 已同步：admin dishes (name/activate) + DishIssueOut.dishFeedbackCount')
