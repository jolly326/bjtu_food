# 学生账号管理 — 页面 UI 设计稿

> 所属端：管理端（Web 后台）
> 落点：`/dashboard/system`（`UserView`）；查看活动走 `UserActivityModal` 弹窗（不跳页）。

## 1. 页面构成（自上而下）

| 区块 | 内容 |
|---|---|
| 页头 | `PageHeader` 标题「学生账号」+ 唯一主操作 **刷新** 按钮（`:disabled="loading"`） |
| 筛选条 | `FilterBar`（关键词搜索）+ 两个 `FilterSelect`（**状态** / **认证**）+ 批量按钮区（选中才显示） |
| 表格 | `DataTable`（`selectable`）：头像（首字母圆形）/ 昵称 / 绑定邮箱 / 注册时间 / 状态（`el-switch`）/ 操作（行为 / 禁用·启用） |
| 分页 | **无分页控件**：数据前端全量过滤，footer 仅「共 N 条」 |
| 弹窗 | `UserActivityModal`：`:show="!!activityUser"` + `:user="activityUser"`（活动聚合并按类型分 tab） |

## 2. 三态（由 `DataTable` 承担）

| 态 | 呈现 |
|---|---|
| 加载中 | 表内「⟳ 加载中…」 |
| 失败 | `{{ error }}` + **重试**（`retryable` + `@retry="refresh"`） |
| 空 | 动态文案：`searchQuery` 有值 →「没有匹配的学生」；否则「暂无学生用户」 |
| 正常 | 表格 + footer「共 N 条」 |

## 3. 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `PageContainer` / `PageHeader` | 公共 `components/layout` | 外壳 / 标题 + 刷新 |
| 2 | `FilterBar` / `FilterSelect` | 公共 `components/layout` | 关键词 + 状态 / 认证筛选 |
| 3 | `DataTable` | 公共 `components/DataTable.vue` | 列表、三态、多选、重试 |
| 4 | `StatusTag` | 公共 | 认证态标签 |
| 5 | `UserActivityModal` | 公共 | 行为聚合弹窗（按类型分 tab） |
| 6 | `el-switch` / 图标 | Element Plus | 状态开关 / 图标 |

## 4. 数据映射

| # | 字段 | 来源 | 中文含义 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|---|---|
| 1 | 头像 | `nickname || username` 首字母 | 头像 | `span.avatar-circle` | 圆形字（**非真实头像 URL**） |
| 2 | 学号 | `id` | 账号 | 仅 `guestLabelOf` 取尾 4 位（游客态） | **表格不单独展示学号列** |
| 3 | 昵称 | `nickname || guestLabel || username` | 昵称 | `.user-name` | 空兜底 |
| 4 | 绑定邮箱 | `bindEmail` | 邮箱 | `.user-email` | 空 → 「未绑定」 |
| 5 | 注册时间 | `created_at` | 注册时间 | `.reg-time` | `toLocaleDateString`；空 → 「—」 |
| 6 | 状态 | `status` | 正常 / 禁用 | `el-switch` + 文字 | 开 = 禁用（danger），关 = 正常 |
| 7 | 认证态 | `bindEmail` 派生 | 是否认证 | `StatusTag` | 有邮箱 = 已认证 |

> **无角色列**：本页全量即学生，不做角色过滤（后端仅 `STUDENT` / `ADMIN` 两种角色，管理端自身为 `ADMIN` 不走此列表）。

## 5. 交互清单

- 关键词 / 状态 / 认证 → 前端 `computed` 过滤（**无搜索防抖**）
- **无分页**：全量前端过滤
- 行「行为」→ 打开 `UserActivityModal`（`activityUser = row`）
- 单条禁用 / 启用：`el-switch` 直接 `toggleStatus`，**无二次确认**（易误触）
- 批量：选中才出现批量栏；`batchSetStatus` 有二次确认，循环单条失败不中断
- 刷新按钮：重新 `userStore.loadAll()`（loading 期间禁用）

## 6. 接口与错误呈现

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 全量学生列表（`userStore.loadAll`） |
| PUT | `/admin/users/{id}/status` | 单条 / 批量禁用 · 启用（`toggleUserStatusById`） |

- 列表失败 → 错误条 + **重试**（文案「加载学生列表失败」）；状态更新失败 → `toast.error('状态更新失败')`
- 仅透传 `e.message`，无显式错误码分支

## 7. 已知取舍（保留现状）

- **单条开关无二次确认**（批量有、单条无）—— 易误触；如需一致可补确认
- **无分页**（数据量大时前端过滤有性能风险）
- **无搜索防抖**；无导出 / 新建账号操作（管理端不建账号）
- 头像仅为首字母（非真实头像 URL）；学号未作为独立列展示
- 认证态依赖 `bindEmail` 派生（非权威字段）
