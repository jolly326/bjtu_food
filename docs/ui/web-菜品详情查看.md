# 菜品详情查看 — 页面 UI 设计稿

> 所属端：管理端（Web 后台）
> 落点：`/dashboard/content/dishes/:id`（`DishDetailView`，**只读查看** + 编辑 / 删除经弹窗）；编辑复用新增同款 `DishFormDialog`。

## 1. 页面构成（自上而下）

| 区块 | 内容 |
|---|---|
| 页头 | `PageHeader`：返回箭头 + 标题 = `dish.name` + 副标题「食堂 · 档口 · ★评分 (N人)」+ 右侧主操作 **编辑** / **删除** |
| 信息卡 | 左 `EntityImage`（封面图，点击触发编辑）/ 右字段平铺 `dl.info-grid`：价格（促销价 + 原价划线）/ 状态（`StatusTag`）/ 荤素 / 主料 / 口味 / 冷热 / 介绍 |
| 评价区 | `<h2>评价</h2>` + `DataTable`：用户 → 评分（`StarRating`）→ 内容（截断 + 配图数角标）→ 状态（`el-switch`）→ 时间 → 操作（查看 / 删除） |
| 弹窗 | `DishFormDialog`（编辑，`footer` 自带确认）/ `ReviewDetailDialog`（只读评价详情） |

## 2. 四态

| 态 | 呈现 |
|---|---|
| 加载中 | 整页转圈 + 「加载中…」 |
| 失败 | `loadError` 文案 + 「请返回列表重试」+「返回菜品列表」按钮（**仅返回，无重试**，因加载由 `onMounted` 一次触发） |
| 不存在 | 「菜品不存在或已删除」+ 返回 |
| 正常 | `v-if="dish"` 渲染信息卡 + 评价区 |

## 3. 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `PageContainer` / `PageHeader` | 公共 `components/layout` | 外壳 / 返回 + 标题 + 主操作 |
| 2 | `EntityImage` | 公共 | 封面图（点击 = 编辑） |
| 3 | `DataTable` | 公共 `components/DataTable.vue` | 评价列表（含分页由组件内处理） |
| 4 | `StatusTag` | 公共 | 在售 / 已下架 |
| 5 | `StarRating` | 公共 | 评分星 |
| 6 | `DishFormDialog` | 公共 | 唯一编辑实现（与新增同组件） |
| 7 | `ReviewDetailDialog` | 公共 | 只读评价详情 |

## 4. 数据映射

| # | 字段 | 来源 | 中文含义 | 显示在哪个组件 | 呈现形式 |
|---|---|---|---|---|---|
| 1 | `firstImage` | `images` 首张 | 封面 | `EntityImage` | 点击同编辑；无图 → 灰底占位 |
| 2 | `price` / `originalPrice` | 接口 | 现价 / 原价 | 信息卡 | `¥xx.xx`；`originalPrice` 高于现价时划线（红底「折扣」标） |
| 3 | `status` | 接口 | 状态 | `StatusTag` | 在售 / 已下架 |
| 4 | 荤素 `dietType` / 主料 `ingredients` / 口味 `flavorTags` / 冷热 `serveTemp` | 接口 + `attrStore` 字典 | 四维属性 | 信息卡 | 字典中文标签；加载失败降级枚举键 |
| 5 | `description` | 接口 | 介绍 | 信息卡 | 空 → 「暂无介绍」 |
| 6 | 副标题 | `canteenName` / `stallName` / `avg_rating` / `rating_count` | 位置 + 评分 | 页头副标题 | 「食堂 · 档口 · ★评分 (N人)」 |
| 7 | 评价列 | `user_id`→`getUserName` / `rating` / `content`+`images` / `is_hidden`(`el-switch`) / `created_at` | 评价 | 评价 `DataTable` | 用户降级「用户#id」；配图角标；状态开关 `toggleReviewHidden` |

> **未呈现字段**：独立的「位置」字段与「信息更新时间」均未单独展示 —— 位置并入页头副标题，更新时间页面无对应位置（如需追溯走接口 / 数据库）。

## 5. 交互清单

- 返回 → `/dashboard/content?tab=dish`（回到列表）
- 编辑 / 封面图点击 → 打开 `DishFormDialog`（`dish` 预填）
- 删除 → 二次确认 → `store.deleteDish` → 回列表
- **评价行不可整行点击**：仅「查看」（开 `ReviewDetailDialog`）与「删除」；状态 `el-switch` → `toggleReviewHidden`
- 无手动刷新按钮（仅 `onMounted` 加载一次）

## 6. 接口与错误呈现

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/dishes`（经 `dishStore.loadAll` 全量缓存按 id 取） | 主域数据 |
| GET | 评价 / 用户 / 四维字典 | 附属数据（`reviewStore` / `userStore` / `attrStore`） |
| PUT | `/admin/dishes/{id}` | 编辑（经 `DishFormDialog`） |
| DELETE | `/admin/dishes/{id}` | 删除 |

- 主域加载失败 → `loadError`（仅取 `e.message`，无显式 HTTP 码）；附属加载失败 `.catch(()=>{})` **静默吞掉**（评价区空且无提示）
- 删除 / 评价操作失败 → `toast.error`

## 7. 已知取舍（保留现状）

- **失败态无重试入口**（仅可返回列表）；**评价加载失败被静默吞**（无提示、列表空）
- 四维字典加载失败仅降级透出枚举键（无「—」兜底，与介绍不同）
- 数据来源：`dish` 取自 `adminStore.dishes`，但加载走 `dishStore.loadAll()` —— 两 store 须保持同源，否则恒为「不存在」态（已在代码层确保）
- 评价区分页由 `DataTable` 组件内处理，模板未显式配置
