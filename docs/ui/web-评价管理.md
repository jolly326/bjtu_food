# 评价管理 — 页面 UI 设计稿

> 所属端：管理端（Web 后台）
> 落点：`/dashboard/reviews`（`ReviewManageView`）；详情走 `ReviewDetailDialog` 只读弹窗（不跳页）；支持 `?rid=<id>` 深链定位。

## 1. 页面构成（自上而下）

| 区块 | 内容 |
|---|---|
| 页头 | `PageHeader` 标题「评价」+ 副标题「隐藏 / 删除不当内容」 |
| 筛选条 | `FilterBar`（关键词搜索）+ **显示状态** 下拉（`FilterSelect`，140px，选项：全部 / 显示中 / 已隐藏） |
| 表格 | `DataTable`（**无分页**，后端 `listAllReviews` 不接收页码 → 全量加载）：用户 → 评分（`StarRating`）→ 内容（ellipsis + 配图角标）→ 菜品 → 时间 → 状态（`el-switch`）→ 操作列（查看 / 删除，宽 160） |
| 分页 | **无分页控件**；计数仅由 footer「共 N 条」承担 |
| 详情 | `ReviewDetailDialog`（只读）：收 `show` / `review` / `user-name` / `dish-name` |
| 批量 | 选中行才渲染：批量隐藏 / 显示 / 删除 |

## 2. 三态（由 `DataTable` 承担）

| 态 | 呈现 |
|---|---|
| 加载中 | 表内「⟳ 加载中…」 |
| 失败 | `{{ error }}` + **重试**（`retryable` + `@retry="loadList"`） |
| 空 | 文案「暂无评价」 |
| 正常 | 表格 + footer「共 N 条」 |

## 3. 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `PageContainer` / `PageHeader` | 公共 `components/layout` | 外壳 / 标题与副标题 |
| 2 | `FilterBar` / `FilterSelect` | 公共 `components/layout` | 关键词 + 显示状态筛选 |
| 3 | `DataTable` | 公共 `components/DataTable.vue` | 列表、三态、批量多选、重试 |
| 4 | `StarRating` | 公共 | 评分星展示 |
| 5 | `ReviewDetailDialog` | 公共 | 只读评价详情（动作不放弹窗内） |
| 6 | `el-switch` / 图标 | Element Plus | 显隐开关 / 删除图标 |

## 4. 数据映射

| # | 字段 | 来源 | 中文含义 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|---|---|
| 1 | `user_id` | 接口 + 用户字典 `getUserName` | 提交人 | 用户列 | 昵称（字典缺失降级「用户#id」） |
| 2 | `rating` | 接口 | 评分 | `StarRating` | 星 + 数值 |
| 3 | `content` + `images` | 接口 | 内容 / 配图 | 内容列（截断 + 悬停）+ `Picture` 角标「N」 | 空 → 文本占位 |
| 4 | `dish_id` | 接口 + 菜品字典 `getDishName` | 关联菜品 | 菜品列 | 名称（缺失降级「菜品#id」） |
| 5 | `created_at` | 接口 | 提交时间 | 时间列 | `toLocaleString`；空 → 「—」 |
| 6 | `is_hidden` | 接口 | 显示状态 | `el-switch` + 文案「已隐藏 / 显示中」 | 开 = 已隐藏（danger），关 = 显示中 |

## 5. 交互清单

- 关键词输入：**300ms 防抖** → 重拉；显示状态切换 → 重拉
- **无翻页**：`listAllReviews` 不传页码，全量加载
- 深链 `?rid=<id>`：列表落地后按 id 定位、高亮并自动开详情；目标不存在仅 Toast、不阻断
- 行「查看」→ `ReviewDetailDialog`；状态开关 → `toggleHidden`（立即隐 / 显）；删除 → 二次确认
- 批量：选中才出现批量栏；批量隐 / 显 / 删（循环单条，**部分失败汇总报错并回填失败项**，不中断其余）
- 单条操作期间**开关无禁用态**（可并发）

## 6. 接口与错误呈现

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/reviews` | `isHidden` / `keyword`（**不接收页码** → 全量） |
| PUT | `/admin/reviews/{id}` | 隐 / 显（`isHidden`） |
| DELETE | `/admin/reviews/{id}` | 删除（单 / 批） |

- 列表失败 → 错误条 + **重试**；写操作失败 → `toast.error(e.message)`；批量部分失败 → 汇总
- **隐患（保留现状）**：`isHidden` 须传 `0 / 1`，传布尔值会触发后端 `400`（筛选回传已按数值处理，单条开关的载荷需保持一致）

## 7. 已知取舍（保留现状）

- **无分页**：评价量大时全量拉取有性能风险（与菜品管理的前端分页不同，本页后端不提供分页参数）
- 进页统计行已移除，无额外数量 / 维度统计（footer 仅「共 N 条」）
- 空态无 CTA；详情弹窗无动作入口（只读，处理动作在表格行内）
- 单条开关无 loading / 禁用态 → 可并发切换（批量有二次确认，单条无）
