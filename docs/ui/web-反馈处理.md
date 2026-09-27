# 反馈处理 — 页面 UI 设计稿

> 所属端：管理端（Web 后台）
> 落点：`/dashboard/feedback`（`FeedbackView`）；详情与处理走 `FormDialog` 抽屉（不跳页）；支持 `?fid=<id>` 深链直达。

## 1. 页面构成（自上而下）

| 区块 | 内容 |
|---|---|
| 页头 | `PageHeader` 标题「反馈处理」+ 副标题「处理学生反馈：采纳回复 / 不采纳（必填原因）」 |
| 筛选条 | `FilterBar`：左槽 **状态**（140px）/ **类型**（150px）两个下拉；右槽 = 关键词搜索框（有值显清除钮） |
| 表格 | `DataTable`（**服务端受控分页**）：类型 170px（居中）→ 关联对象 140px（居中）→ 内容（超长截断）→ 提交人 170px → 提交时间 170px（可排序）→ 状态 100px（居中）→ 操作列（默认 160px） |
| 分页 | **后端真分页**（`pageSize` 默认 20；分页栏仅当 `total > pageSize` 出现） |
| 详情抽屉 | `FormDialog`（宽 520）：标题按状态切换「反馈详情 / 处理反馈」；已处理态无默认 footer，仅「关闭」 |

> **无批量处理**：未启用多选，只能逐条处理。

## 2. 三态（由 `DataTable` 承担）

| 态 | 呈现 |
|---|---|
| 加载中 | 表内「⟳ 加载中…」 |
| 失败 | `{{ error }}，请刷新页面重试`（页内补「重试」动作） |
| 空 | 文案「暂无反馈」 |
| 正常 | 表格 + 分页栏 |

## 3. 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `PageContainer` / `PageHeader` | 公共 `components/layout` | 外壳 / 标题与副标题 |
| 2 | `FilterBar` | 公共 `components/layout` | 关键词搜索 + 筛选容器 |
| 3 | `FilterSelect` ×2 | 公共 `components/layout` | 状态 / 类型下拉 |
| 4 | `DataTable` | 公共 | 列表、三态、排序、受控分页 |
| 5 | `StatusTag` | 公共 | 状态标签（待处理 warning / 已处理 success） |
| 6 | `FormDialog` | 公共 | 详情 + 处理抽屉 |
| 7 | `el-image` | Element Plus | 配图缩略图 + 点击放大预览 |
| 8 | `useAsyncGuard` | 公共 `composables` | loading / error / 请求竞态守卫 |
| 9 | `toastStore` / `confirmStore` | 公共 `stores` | 成功失败提示 / 二次确认 |

## 4. 数据映射

| # | 字段 | 来源 | 中文含义 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|---|---|
| 1 | `type` + `sub` | `GET /admin/feedbacks` + 类型常量 + 举报原因字典 | 反馈类型 | `.type-pill` | 「功能建议 · 想法/问题」「举报 · 垃圾广告」；未登记值回落原值；字典失败静默降级只显一级类型 |
| 2 | `relatedType` / `relatedId` / `relatedDishName` | 接口 | 关联对象 | `.link` 按钮 | 评价 → 「评价 #id →」（跳 `/dashboard/reviews?rid=`）；菜品 → 菜品名（跳 `/dashboard/content?tab=dish&q=`）；否则「—」 |
| 3 | `content` | 接口 | 内容 | 内容列（整段可点） | 空 → 「（无内容）」；点击开抽屉 |
| 4 | `images.length` | 接口 | 配图数 | `.img-flag` | 数字角标 + `title`「该反馈附有配图」 |
| 5 | 提交人（派生） | `userId` + 用户字典 | 提交人 | `.sub-name` | 昵称 → `用户#id` → 「游客」（匿名） |
| 6 | `createdAt` | 接口 | 提交时间 | 时间列 | `zh-CN` 本地化；空 / 非法 → 「—」 |
| 7 | `status` | 接口 | 状态 | `StatusTag` | 待处理 / 已处理；未知值回落 warning + 原值 |
| 8 | 详情字段 | 接口 | 类型 / 提交人 / 时间 / 关联 / 内容 / 配图 / 处理时间 / 历史回复 / 不采纳原因 | 抽屉内 | 不采纳原因用错误色浅底块 |

## 5. 交互清单

- 关键词输入：**300ms 防抖** → 回第 1 页重拉；状态 / 类型切换同样回第 1 页
- 翻页 / 改每页条数 → 重新请求（不重置页码）
- 打开详情：内容链接 / 操作列按钮 / **深链自动打开**
- 深链 `?fid=<id>`：先拉列表再按 id 定位并自动开抽屉；不在当前页且带 `?title=` 时用标题作关键词服务端检索后定位；否则 Toast 提示
- 处理提交：回复必填（匿名 / 非匿名提示文案不同）→ 长度 ≤1000 → 不采纳时原因必填且 ≤200 → **二次确认**（文案说明不可逆）→ PUT → Toast 成功 → 刷新列表并关抽屉
- 已处理态：抽屉只读回看（仅「关闭」）
- 配图：点击缩略图放大预览

## 6. 接口与错误呈现

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/feedbacks` | `status` / `keyword` / `type` / `page` / `pageSize`（服务端过滤与分页） |
| PUT | `/admin/feedbacks/{id}` | `{ reply, outcome?, rejectReason? }` |
| GET | `/feedback/report-reasons` | 举报原因字典（仅用于文案翻译，失败静默） |

- 列表失败 → 错误条（补重试）；处理失败 → `toast.error(message || '处理失败')`；字典失败 → 静默降级

## 7. 已知取舍（保留现状）

- 类型筛选保留存量脏值（`suggestion` / `add` / `error` / `bug` / `report` / `other`），新数据只有 `issue`，可能出现「筛出 0 条」的空态；`error`（菜品信息纠错）已迁出为独立「信息纠错」页，此处仅为历史数据兼容
- 无批量处理、整行不可点（打开详情靠内容链接与操作列）
- 反馈处理不可逆（无撤销），确认文案已明示
