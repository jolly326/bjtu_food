# 信息纠错处理 — 页面 UI 设计稿

> 所属端：管理端（Web 后台）
> 落点：`/dashboard/corrections`（`CorrectionView`）；详情与处理走 `FormDialog` 抽屉（不跳页）；支持 `?cid=<id>` 深链直达。

## 1. 页面构成（自上而下）

| 区块 | 内容 |
|---|---|
| 页头 | `PageHeader` 标题「信息纠错」+ 副标题（说明处理学生提交的菜品纠错） |
| 筛选条 | 仅 **状态** 下拉（`FilterSelect`，140px）；**无关键词搜索**（后端 `listCorrections` 不接收 keyword） |
| 表格 | `DataTable`（**服务端分页**）：目标菜品 180px → 提交内容（ellipsis）→ 提交人 130px → 提交时间 170px（可排序）→ 状态 100px（居中） |
| 分页 | **后端真分页**（`server-mode` + `server-total`；`page` / `pageSize` 参数，后端 `listCorrections` 支持） |
| 详情抽屉 | `FormDialog`（宽 520，`footer=false`）：元信息（目标菜品 / 提交人 / 时间）→ **对比卡七字段**（名称 / 价格 / 食堂 / 档口 / 口味 / 食材 / 图片，原值 vs 提交值）→ 已处理时只读回显（处理时间 / 回复 / 拒绝原因） |
| 档口确认 | 采纳时若后端返回 `needStallConfirm`，弹二级 `FormDialog`（宽 420）选择 / 新建档口 |

> **无批量处理**：只能逐条采纳 / 拒绝。

## 2. 三态（由 `DataTable` 承担）

| 态 | 呈现 |
|---|---|
| 加载中 | 表内「⟳ 加载中…」 |
| 失败 | `{{ error }}` + **重试**按钮（`retryable` 已接入 `@retry="loadList"`） |
| 空 | 文案「暂无纠错」 |
| 正常 | 表格 + 分页栏 |

## 3. 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `PageContainer` / `PageHeader` | 公共 `components/layout` | 外壳 / 标题与副标题 |
| 2 | `FilterSelect` | 公共 `components/layout` | 状态筛选 |
| 3 | `DataTable` | 公共 `components/DataTable.vue` | 列表、三态、排序、服务端分页、重试 |
| 4 | `StatusTag` | 公共 | 状态标签（待处理 / 已采纳 / 已拒绝） |
| 5 | `FormDialog` | 公共 | 详情抽屉 + 档口确认对话框 |
| 6 | `useAsyncGuard` | 公共 `composables` | loading / error / 请求竞态守卫 |
| 7 | `el-image` / `el-icon` | Element Plus | 附图点击预览 / 图标 |

## 4. 数据映射

| # | 字段 | 来源 | 中文含义 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|---|---|
| 1 | `dishName` | 接口（后端回填） | 目标菜品 | 目标菜品列 / 详情卡 | 文本 + 链接按钮（跳 `/dashboard/content?tab=dish&q=`）；取不到显「—」 |
| 2 | `snapshot`（七字段） | 接口 | 学生提交的差异 | `summaryText()` 单行摘要 / 详情对比卡 | 名称 / 价格 / 食堂 / 档口 / 口味 / 食材 / 图片 原值↔提交值并排 |
| 3 | `userId` | 接口 | 提交人 | `submitterLabel()` | 「用户#id」/「游客」 |
| 4 | `createdAt` | 接口 | 提交时间 | `fmtTime()` | 本地化；空 → 「—」 |
| 5 | `status` | 接口 | 状态 | `StatusTag` + `CORRECTION_STATUS_META` | 待处理 warning / 已采纳 success / 已拒绝 info |
| 6 | 对比图片 `snapshot.images` + `originalDish.images` | 接口 + `dishStore` | 配图对比 | `detailImages`（`el-image`） | 两侧均无图时不渲染图片行 |
| 7 | 处理回复 / 拒绝原因 | 接口（仅已处理） | 处理结果 | 详情卡只读区 | 回复、`rejectReason` 错误色浅底块 |

## 5. 交互清单

- 状态筛选切换 → 回第 1 页重拉（`onStatusChange`）
- 翻页 / 改每页条数 → 重新请求
- 打开详情：目标菜品列 / 操作列按钮 / **深链自动打开**
- **深链 `?cid=<id>`**（本次新增）：先拉列表再按 id 定位并自动开抽屉；不在当前「待处理」筛选内时回退切「全部状态」再定位一次，仍无则 Toast「该纠错不在当前列表中」
- 采纳：`submitAdopt` → 确认 → `adoptCorrection`（后端可能回 `needStallConfirm`，弹档口选择对话框）
- 拒绝：`submitReject` → 回复选填、**拒绝原因必填且 1~200 字** → `rejectCorrection`
- 无批量、整行不可点（打开详情靠链接与操作列）

## 6. 接口与错误呈现

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/corrections` | `status` / `page` / `pageSize`（服务端过滤与分页） |
| POST | `/admin/corrections/{id}/adopt` | 采纳（携带档口选项，若需确认） |
| POST | `/admin/corrections/{id}/reject` | 拒绝（携带 `reply` / `rejectReason`） |

- 列表失败 → 错误条 + **重试**；处理失败 → `toast.error(e.message)`；字典失败 → 静默降级
- 特殊：原菜品已删除时后端 `4001` 拦截（详情卡降级「—」）

## 7. 已知取舍（保留现状）

- **无关键词搜索 / 无批量处理**：后端 `listCorrections` 不接收 keyword，处理只能逐条（与反馈页的历史设计一致）
- 原菜品定位依赖 `dishStore.loadAll()` 全量缓存按 id 取，全量加载失败则降级「—」
- 排序仅「提交时间」由前端 `sortValue` 提供（非后端排序）
- 档口确认对话框仅当 `snapshot.stallName` 存在时才提供「新建」选项
