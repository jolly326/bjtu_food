# 评价「有用」（A-07）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔐 认证**（需学号邮箱认证）
> 返回：[功能总览](./README.md)

## 干什么

给别人的评价点「有用」，用于**评价列表排序**（默认按有用数置顶，帮后来者先看到高质量口碑）。一人对一条评价只有一票，可取消。

## UI

- 评价卡片上的「有用」按钮（图标 `ic-thumb`）＋ 计数。
- 已点过时按钮呈选中态；未认证用户点击 → 弹 `AuthSheet` 认证引导（入口不置灰）。

## 操作

1. 点「有用」→ 未点过则 +1 并变选中；**再点一次取消**（-1）。
2. 请求期间端上做乐观更新，失败回滚。
3. 未认证 → 不发起请求，直接弹认证引导。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/reviews/{id}/useful` | **🔐 认证**（`@RequireVerified`；未认证 → **4031**） | 「有用」**幂等切换**：未标记→标记返回 `useful=true`；已标记→取消返回 `useful=false`，不抛错。**注（2026-09-20 §7.30）：本端点随「评价有用」下线一并删除（spec §7.14 清单 #1），不做 RESTful 化改造。** |

## 字段

### 请求

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 评价 ID（路径参数，如 `/reviews/1/useful`）。**无请求体**，用户身份从 JWT 取 |

### 响应（`data` = `UsefulResult`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `useful` | boolean | **本次切换后的状态**：true=当前已标记「有用」/ false=已取消 |
| `usefulCount` | number | 该评价当前的「有用」总数（端上直接覆盖计数展示） |

### 关联出参（读接口里的同名字段）

| 字段名 | 出现位置 | 中文解释 |
|---|---|---|
| `usefulCount` | `ReviewVO`（`GET /reviews`、`GET /my/reviews`） | 该评价有用总数 |
| `useful` | `ReviewVO`（仅登录态） | 当前用户是否已标记；公开列表未登录时为 null |
| `usefulCount` | `ReviewAdminVO`（`GET /admin/reviews`） | 管理端评价列表的有用数 |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 4031 | 邮箱未认证 | 前端据此弹 `AuthSheet`（与 403 普通无权限分流） |
| 400 | 评价不存在 | 资源缺失已收敛入 400 |

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `review_useful` | INSERT / DELETE | 一人一评一票（唯一索引约束），记录「谁给哪条评价点了有用」 |
| `review.useful_count` | 异步 UPDATE | 评价的有用总数（冗余计数，与 `review_useful` 行数同口径） |

## 答疑

### Q：去除这个功能，评价不允许互动，没有点击有用无用的功能，相关数据链路和数据库全部删掉

**A：技术上可行、能删干净；但我的建议是「不建议删」，请你拍一下。**

**【不建议删的理由】** ① 「有用」是评价列表唯一的轻量质量信号：删掉后只能按时间倒序，长列表里低质评价会淹没高质评价，而本项目定位正是「吃什么不踩雷」，口碑排序是核心体验；② 它不构成社区互动（无评论 / 无回复 / 无关注 / 无跟帖），只是单向聚合排序，与「轻社区边界（UGC 仅评价一种形态）」不冲突，删它并不换来定位上的收益；③ 保留成本≈0，删除代价是跨三端 + 库表 + **评分排序口径重置**。

**【若确认删，这是完整下线清单（按代码实况列全）】**

- **后端**：`POST /reviews/{id}/useful` 端点（`ReviewController.toggleUseful`）、`ReviewService.toggleUseful`、`UsefulResult` DTO、`ReviewUseful` 实体 + `ReviewUsefulMapper`、`ReviewServiceImpl` 的切换/计数逻辑、`ReviewVO.usefulCount/useful`、`ReviewAdminVO.usefulCount`、`ReviewConst` 相关常量、`AuthServiceImpl.migrateOwnership` 的两段 `review_useful` 归属迁移、`deleteReview/deleteByAdmin/deleteDish` 中清理 `review_useful` 的段。
- **排序口径（最易漏）**：`GET /reviews` 默认 `sort=useful` 的 `ORDER BY useful_count DESC, created_at DESC` 必须改为 `created_at DESC`；而「按有用数置顶」被 `spec §7.14 第 2 条 / §7.18 第 3 条`写为唯一权威口径 → **必须先改 spec 再动代码**。
- **库表**：`review_useful` 表（`schema.sql` 的 CREATE 段 + 存量库幂等 DROP 段）、`review.useful_count` 列与其索引（按「零消费即删除」口径一并清）。
- **小程序**：`api/review.ts toggleUseful`、`types/review.ts` 的 `usefulCount/useful`、评价卡「有用」按钮与计数、未认证 `AuthSheet` 分支、详情页乐观更新/回滚逻辑。
- **Web**：评价列表/详情「有用数」列、`adapter` 映射、以及依赖有用数的任何展示统计。

**【需要你回复的】** 回复「确认删除有用」我就按清单拆任务并提醒技术负责人先改 spec；否则本条按「保留」处理，本文档不改。

**（2026-09-17 更新）** 用户已表态：**确认删除**——理由「只是给用户做参考的信息，没必要徒增运营成本，按时间先后排序就好」。结论改为「**已拍板：删除**」；执行**前置条件不变**：先由技术负责人改 `spec §7.14 第 2 条 / §7.18 第 3 条` 与 `api-design §2.3` 的默认排序口径（`useful_count DESC` → `created_at DESC`），再动代码。
同时新增「**只看有图**」筛选（`GET /reviews?hasImage=1`）作为替代质量信号，设计口径见 [client-菜品详情](./client-菜品详情.md#已拍板待实现清单)。
