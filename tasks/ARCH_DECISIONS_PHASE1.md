# 一期技术评审 · 架构决策与接口契约（ARCH_DECISIONS_PHASE1）

> 文档性质：技术架构师定稿。覆盖 task-01~task-05（一期）。
> 配套文档：`project_spec.md`（唯一权威约束）、`tasks/task-0X.md`（开发单元）。
> 本文所有契约/ DDL / 白名单为强制结论；task 文档中与之冲突处已在对应 task 文档内以「架构师修订」标注，以本文为准。
> 依赖关系现状（来自需求梳理师）：task-04（白名单）→ task-01/02/03；task-03（实体扩展）→ task-05（web 表单）。本文按此拓扑推进。

---

## 0. 一期限定范围（Scope）

一期**仅**交付 task-01~05，涉及：
- 首页改版、搜索/发现页、菜品详情强化、游客白名单、web 字段表单。
- **明确不出一期**：搜索埋点/浏览量统计新表（无 `search_log` 表）、分类字典表（`dish_category`）、活动运营位新字段、关联动态（task-06/二期占位）。
- 热搜榜单一期采用**基于现有 `dish.view_count` / `rating_count` 的派生热度公式**，**不新建埋点表**（见 §5）。

---

## 1. 实体字段扩展（DDL 变更）

### 1.1 现状核对结论

| 需求方要求字段 | 现有 schema/实体 | 结论 |
| --- | --- | --- |
| `stall.floor` / `stall.window_no` / `stall.business_hours` | 均**不存在** | 需新增 |
| `dish.spice_level` / `portion` / `serve_period` / `limited` | 均**不存在** | 需新增 |
| `review.useful_count` | 不存在 | 需新增（计数冗余列） |
| `review_useful` 表 + `uk_useful_user_review` | **已存在**（schema.sql §129-141，实体 `ReviewUseful` 已建） | 无需新建表，仅缺 `useful_count` 列 + 幂等/计数逻辑 |

> 注：`review_useful` 表与 `ReviewUseful` 实体此前已落地，但 `ReviewServiceImpl.likeReview` 仍是**非幂等**实现（已存在则抛 `BusinessException`），与「切赞/取消」语义冲突，见 §3.2 修订。

### 1.2 确切 DDL（追加到 `backend/src/main/resources/db/schema.sql`）

```sql
-- ===== 一期扩展字段（追加，不改动既有列） =====

-- 档口：楼层 / 窗口号 / 营业时间
ALTER TABLE `stall`
    ADD COLUMN `floor`          VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '楼层（如 1F/2F）',
    ADD COLUMN `window_no`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '窗口号',
    ADD COLUMN `business_hours` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '营业时间，如 10:00-20:00';

-- 菜品：辣度 / 分量 / 供应时段 / 是否限量
ALTER TABLE `dish`
    ADD COLUMN `spice_level` TINYINT NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣',
    ADD COLUMN `portion`     TINYINT NOT NULL DEFAULT 0 COMMENT '分量枚举：0=小 1=中 2=大',
    ADD COLUMN `serve_period` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight',
    ADD COLUMN `limited`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否限量（0=否 1=是）';

-- 评价：有用计数（冗余列，由 review_useful 聚合维护，见 §3.2）
ALTER TABLE `review`
    ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用」标记数（一人一票，uk_useful_user_review）';
```

> 枚举值域在后端用常量类/枚举定义（建议 `com.bjtufood.dish.constant.DishAttrConst` 与 `com.bjtufood.common.constant.*`），**不引入新字典表**。
> `serve_period` 逗号分隔 tag 字符串，复用与 `tags` 一致的存储风格（最小代价，见 §4）。

### 1.3 实体 / DTO / VO 影响面清单

**Backend 实体（entity/）**
- `canteen/entity/Stall.java`：新增 `floor`、`windowNo`、`businessHours`（Lombok `@Data` + MP 自动驼峰，无需 `@TableField`）。
- `dish/entity/Dish.java`：新增 `spiceLevel`(Integer/TINYINT)、`portion`(Integer)、`servePeriod`(String)、`limited`(Integer/TINYINT)。
- `review/entity/Review.java`：新增 `usefulCount`(Integer)，`@TableField("useful_count")`。
- `review/entity/ReviewUseful.java`：已存在，无需改动。

**Backend VO（dto/）**
- `dish/dto/DishVO.java`：新增以下字段（camelCase，§3.x.6.1 红线）：
  - `floor`(String)、`windowNo`(String)、`businessHours`(String) —— 来自 stall 联表。
  - `spiceLevel`(Integer)、`portion`(Integer)、`servePeriod`(String)、`limited`(Boolean/Integer) —— 来自 dish。
- `dish/dto/DishDetailVO.java`：继承 `DishVO`，自动获得上述字段，**无需改动**。
- `review/dto/ReviewVO.java`：新增 `usefulCount`(Integer)；新增 `useful`(Boolean?，仅登录态返回当前用户是否标记过，公开列表可不返回该字段以免泄露）。
- `review/dto/ReviewAdminVO.java`：已带 `isHidden`，新增 `usefulCount` 回显（可选）。
- **web Admin DTO（前端不感知，但后端 Admin 出参需对齐）**：
  - `dish/dto/DishAdminReq.java`：新增 `spiceLevel`、`portion`、`servePeriod`、`limited`（接收并写入）。
  - `dish/dto/DishAdminVO.java`：新增同字段回显（供 web 表单回填）。
  - `canteen/dto/StallAdminVO.java`：新增 `floor`、`windowNo`、`businessHours` 回显。
  - `canteen/dto/StallDetailVO.java`：新增 `floor`、`windowNo`、`businessHours`（详情页位置链路）。
  - `canteen/dto/CanteenInfoVO.java`：**不新增**（食堂级营业时间沿用 canteen 现有字段，不扩 business_hours，避免 scope 蔓延；如确需食堂营业时间，登记至 §7 待 spec 补充，一期不做）。

**Mapper XML 影响**
- `mapper/DishMapper.xml`：
  - `joinDishSql` 片段需 `LEFT JOIN stall s` 已存在，新增选取 `s.floor AS floor, s.window_no AS window_no, s.business_hours AS business_hours`；`dishVOMap` / `dishDetailMap` 结果映射补充对应 `<result>`。
  - `dishVOMap` 补充 `spiceLevel/portion/servePeriod/limited` 列映射（`d.spice_level` 等）。
  - `selectDishPage` 排序 `<choose>` 新增 `heat` 分支（见 §3.1）。
- `mapper/ReviewMapper.xml`：
  - `selectReviewPageByDishId` / `selectReviewPageByUserId` 增加 `r.useful_count AS useful_count` 及结果列；新增 `sort=useful` 排序分支（联表 `review_useful` 计数或读冗余列 `useful_count` —— 一期用冗余列，索引 `idx_review_dish` 已覆盖）。

**三端影响面（对齐 project_spec.md §5.y）**
- 后端 P1：DishVO/StallAdminVO/DishAdminVO 字段补齐；DishMapper XML 联表字段与 resultMap 补齐。
- 后端 P2：`review.useful_count` 异步维护（提交/取消 useful 时 +1/-1，或直接 `UPDATE review SET useful_count=(SELECT COUNT(*) FROM review_useful WHERE review_id=?)`，轻量实现）。
- 小程序 F1：`types/dish.ts` 消费新增字段；`types/review.ts` 的 `likeCount?` 重命名为 `usefulCount?`（语义统一见 §3.2）。
- 小程序 F2：`api/review.ts` `likeReview` 改为调用 `/reviews/{id}/useful` 并返回 `{useful, usefulCount}`（现有实现调用 `/reviews/{id}/like` 且忽略返回，需改）。
- Web W1（task-05）：`api/adapter.ts` 新增 `dishToLegacy`/`stallToLegacy` 字段适配；表单组件接入枚举控件（辣度/分量 `el-select`，供应时段多选 tag，限量 `el-switch`）。
- Web W2：禁止将 `useful`/`likeCount` 与 `favoriteCount` 混用（§5.x 喜欢/收藏单一概念红线）。

---

## 2. 接口契约总表（一期新增/扩展，权威登记）

> 鉴权列：`PUB`=公开白名单、`STU`=需 STUDENT、`ADM`=仅 ADMIN。
> 路径风格 kebab-case；金额一律「分」；分页 `PageResult<T>{list,total}`（或 `records` 兼容）。
> 下列契约须同步回 `project_spec.md §3.x.5`（条目见 §7）。

### 2.1 菜品浏览（Dish）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 一期动作 |
| --- | --- | --- | --- | --- | --- |
| GET | `/dishes` | PUB | 现有 + `sortBy` 增 `heat`/`price`（见 §3.1） | `PageResult<DishVO>` | 扩展排序 |
| GET | `/dishes/suggest` | PUB | `keyword`(必填, 可空串返回空) | `List<SuggestionVO>{type(dish/stall/canteen), id, name, image}` | **新增**（task-02） |
| GET | `/dishes/hot-search` | PUB | 无 | `List<HotSearchVO>{keyword, heat}`（TOP10） | **新增**（task-02，派生热度，见 §5） |
| GET | `/dishes/rising` | PUB | 无 | `List<DishVO>`（新晋黑马，基于热度增速） | **新增**（task-02，见 §5） |

> 说明：`/dishes/hot`、`/dishes/new`、`/dishes/recommend`、`/dishes/promotions`、`/dishes/{id}` 已在契约内，沿用。
> `SuggestionVO` / `HotSearchVO` 为新增轻量 VO（dish/dto/ 下），字段 camelCase。
> `/dishes/suggest` 联想：混合菜品/档口/食堂名，按 `keyword` LIKE 匹配 `name`，各取 TOP5 合并，点击按 `type` 跳转（dish→菜品详情、stall/canteen→对应详情）。**不新建搜索词存储表**。

### 2.2 评价「有用」（Review useful）

| Method | Path | 鉴权 | 入参/出参 | 语义 |
| --- | --- | --- | --- | --- |
| POST | `/reviews/{id}/useful` | STU | 出参 `{ useful: boolean, usefulCount: int }` | **切换（幂等）**：未标记→插入 `review_useful` 并 `usefulCount+1`，返回 `useful=true`；已标记→删除记录并 `usefulCount-1`，返回 `useful=false`。重复点击即取消，不抛错（**与现有 `/like` 非幂等行为不同，见 §3.2 修订**）。 |
| GET | `/reviews` | PUB | 现有 `dishId,page,pageSize` + `sort=latest|useful`，`isWithImage` | `PageResult<ReviewVO>`（含 `usefulCount`）；`sort=useful` 按 `useful_count DESC`；`isWithImage` 过滤有图 |

> **架构师修订（task-03§契约）**：原 task-03 写 `POST /reviews/{id}/useful` 与 `uk_useful_user_review` 方向正确，但需明确**语义为「切赞/取消」幂等**，且**废弃现有 `POST /reviews/{id}/like`**（见 §3.2）。emoji 语义：👍=有用（不混用 ❤️ 喜欢，§0.6 红线 3）。

### 2.3 分类宫格「品类」契约

- **不新增接口、不新增字典表**。品类数据来源 = `dish.tags` 派生的固定枚举（后端常量 `DISH_CATEGORIES = [面食, 盖饭, 麻辣烫, 早餐, 夜宵, 快餐, 小吃, 饮品]`）。
- 前端获取品类列表：一期**前端静态配置**（constants/categories.ts）即可，无需后端接口；点击品类 → `GET /dishes?tag={category}`（复用现有 `tag` 筛选，`DishQueryReq.tag` LIKE 匹配）。
- 若后续需后台可维护品类，再单独立项加 `dish_category` 表 + `/admin/categories`（**不在一期**）。

---

## 3. 关键机制裁决（修订现有实现偏差）

### 3.1 `GET /dishes` 的 `sortBy` 扩展

现有 `DishQueryReq.sortBy` 注释写 `rating、collects、price、created_at`，但 `DishMapper.xml` 仅实现 `rating`/`price`/`collects`/`created_at`，**缺 `heat`**。
- **定稿取值**：`heat`（热度，默认降序）、`rating`（评分）、`price`（价格）、`collects`（收藏，兼容历史）、`created_at`（上新）。
- `heat` 排序公式（复用 §3.x.4 权重，一期无浏览历史表，纯热度降序）：
  `ORDER BY (d.view_count*1 + d.rating_count*5*20 + COALESCE(d.avg_rating,0)*20) DESC`
  （与 `DishServiceImpl.heat()` 对齐；`rating_count*5*20` 为视野放大，w3=20 作用于 avg_rating）。
- 默认（无 sortBy）维持现状 `favorite_count DESC, avg_rating DESC`（等同「热门」弱化版）。

> **架构师修订（task-02§契约）**：task-02 表述「扩展 sortBy 支持 heat/rating/price」，实则 `rating/price` 已支持，本期**新增 `heat`** 即可；`sortBy` 入参文档在 spec §3.x.5 修正为上述取值集。

### 3.2 评价「有用」语义定稿（修订现有 `/like`）

**现状问题**：`ReviewController.likeReview` → `ReviewServiceImpl.likeReview` 走 `POST /reviews/{id}/like`，且仅支持「点赞」单向，重复点抛 `BusinessException("你已经喜欢过这条评价")`，**不符合 task-03「再点取消」语义**。

**定稿裁决**：
1. 新增 `POST /reviews/{id}/useful`（STU），**取代** `POST /reviews/{id}/like`。语义：**切换（toggle）+ 幂等**。
2. 实现（service 侧）：
   - 查 `review_useful` 是否存在 `(userId, reviewId)`；
   - 不存在 → `insert` + `review.useful_count += 1` + 返回 `useful=true`；
   - 存在 → `delete` + `review.useful_count -= 1` + 返回 `useful=false`；
   - 全程 `@Transactional`；计数可走冗余列直接维护（`useful_count` 已新增）。
3. `ReviewService` 接口新增 `toggleUseful(Long userId, Long reviewId): UsefulResult(useful, usefulCount)`；保留 `likeReview` 方法**仅内部标记 @Deprecated**，新 Controller 不再暴露 `/like` 端点，避免双概念（呼应 §5.x 喜欢/收藏单一概念红线，此处「有用」为独立语义 👍，不与 ❤️ 混）。
4. 错误码：正常切换返回 200；`review` 不存在返回 400（BusinessException → 400）。**不返回 401 给已登录用户**（STU 鉴权由 `@PreAuthorize`/`SecurityUtil` 保证，未登录走 401 白名单逻辑）。

> **架构师修订（task-03§AC-4）**：验收改为「点击切换有用状态（再点取消），受一人一票约束且幂等，不抛错」。

### 3.3 游客免登录白名单（修订 SecurityConfig）

**现状问题**：`SecurityConfig.PUBLIC_URLS` 使用精确项 `/dishes/{id:[0-9]+}`、`/dishes/{dishId:[0-9]+}/reviews`、**未覆盖** `/dishes/suggest`、`/dishes/hot-search`、`/dishes/rising`，且 `GET /reviews` 虽放行但 `/reviews/{id}/useful` 为写操作不在此列（正确）。新增 dish 子路径若继续写精确项会遗漏。

**定稿白名单（直接放行，PUB）**：

| 接口 | 放行方式 | 说明 |
| --- | --- | --- |
| `/auth/login`、`/auth/register`、`/auth/email-code`、`/auth/password/reset` | 精确 | 既有 |
| `GET /dishes/**` | **通配** | 覆盖 `/dishes`、`/dishes/{id}`、`/dishes/hot/new/recommend/promotions`、`/dishes/suggest/hot-search/rising` |
| `GET /canteens/**` | 通配 | 覆盖 banners/images/stallDetail/all（注：`/canteens` 本身也匹配） |
| `GET /stalls` | 精确 | 既有（`/stalls/**` 可一并放行，无敏感写操作） |
| `GET /reviews` | 精确 | 评价浏览（仅返回 `is_hidden=0`） |
| `GET /activities`、`GET /activities/{id}` | 精确 | 既有 |
| `GET /lists/share/**` | 通配 | 既有 |
| `GET /images/**` | 通配 | 静态资源 |
| Knife4j/Swagger 路径 | 既有 | doc.html 等 |

**仍需登录（STU）**：`POST /reviews`、`POST /reviews/{id}/useful`、`/favorites/**`、`POST /dishes`、`PUT /dishes/{id}`、`GET /my/**`、`POST /dishes/{id}/view`、`POST /feedback`、`POST /upload/image`、`POST /lists`、`/my/stalls`。
**仅 ADMIN**：`/admin/**`（`.hasRole("ADMIN")`）。

> **架构师修订（task-04）**：原 task-04 仅描述「通配覆盖」，未指出当前 `SecurityConfig` 用精确项会漏掉新增 dish 子路径。定稿改为 `GET /dishes/**` 通配；同时明确 `/dishes/{dishId}/reviews` 与 `/reviews` 二选一保留（当前两者都在，保留无妨），新增 `/reviews?dishId=` 已是 PUB。

---

## 4. 分类宫格「品类」实现方案（最小代价定稿）

**需求方二选一**：① 后台可维护「菜品大类」枚举（新表）② 复用 `Dish.tags` 派生。

**架构师裁决：方案②（复用 tags，前端静态常量）**，理由：
1. 现有 `DishQueryReq.tag` + `DishMapper.xml` 的 `d.dags LIKE '%tag%'` 已完整支持品类过滤，零后端改动。
2. 一期「品类」为展示型导航，无需后台动态增删；固定枚举（面食/盖饭/麻辣烫/早餐/夜宵/快餐/小吃/饮品）前端 `constants/categories.ts` 维护即可。
3. 避免引入 `dish_category` 表 + admin 管理菜单 + adapter 适配的连锁成本，符合 §3.x.3「克制」原则。

**数据契约（不新增接口）**：
- 品类列表：前端静态（或后续 `/dishes/categories` 只读返回常量，一期免）。
- 点击品类 → `GET /dishes?tag={categoryKey}`（`categoryKey` 与菜品 `tags` 取值一致，如 `noodle`/`rice`/`malatang`/`breakfast`/`midnight`/`fastfood`/`snack`/`drink`）。
- 学生发布/后台表单的 `tags` 多选控件可包含这些品类 key（与现有 `recommended/signature` 共存，逗号分隔）。

> **架构师修订（task-02§2.2）**：品类来源由「待裁决」定稿为「复用 Dish.tags + 前端静态品类常量」，删除「后台可维护菜品大类枚举」选项；task-02「⚠ 契约/数据模型影响」中「新增 dish_category 表」一笔删除。

---

## 5. 热搜榜单数据来源（一期最小实现）

**现状**：无 `search_log` 表、无搜索词记录；菜品有 `view_count`、`rating_count`、`avg_rating`，评价有 `useful_count`（新增）。

**一期定稿（不新建埋点表）**：
- **`GET /dishes/hot-search`（本周热搜 TOP10）**：因无真实搜索词，一期**语义降为「热门搜索词/热门菜品词云」**，数据来源 = 取 `view_count`+`rating_count` 综合热度最高的 TOP10 菜品的 `name` 作为热搜词条：
  `List<HotSearchVO>{ keyword=dishName, heat=热度分 }`，按热度降序取前 10。
  （前端展示为「热搜 TOP10」词条，点击 → `GET /dishes?keyword={keyword}` 跳转搜索结果。）
- **`GET /dishes/rising`（新晋黑马）**：取近 N 天（默认 14 天）内 `created_at` 较新且 `view_count` 增速高（或 `rating_count>0` 且 `avg_rating` 高）的菜品，按 `(rating_count*20 + view_count)` 降序取 TOP10。`created_at` 已有索引，无需新表。
- **`/dishes/new`**：沿用现有 `created_at DESC`（已存在）。

**二期展望（不在一期）**：若需真实「搜索词热搜」，再立 `search_log` 表（user_id/dish_id/keyword/created_at）+ 异步写 + 聚合接口，届时 `hot-search` 改为读该表。本文明确一期**不建该表**。

> **架构师修订（task-02§2.2/⚠契约）**：`hot-search` 由「搜索词统计」修正为「基于菜品热度的热门词条派生」，并在文档注明二期可升级为真实搜索词。

---

## 6. 需要同步回 project_spec.md 的条目（§7 汇总）与 task 文档修订清单

### 6.1 需回填 project_spec.md 的条目
- §3.x.5 契约总表：登记 `/dishes/suggest`、`/dishes/hot-search`、`/dishes/rising`（PUB）；`GET /dishes` 的 `sortBy` 取值集补 `heat`；登记 `POST /reviews/{id}/useful`（STU，切换语义）；`GET /reviews` 的 `sort` 补 `useful`。
- §3.x.6.1 字段命名：补充 `dish.spiceLevel/portion/servePeriod/limited`、`stall.floor/windowNo/businessHours`、`review.usefulCount`、`ReviewVO.usefulCount/useful`。
- §3.x.6.3 DishVO：补充上述 stall/dish 扩展字段。
- §3.x.6.4 ReviewVO：补充 `usefulCount`（及登录态 `useful`）；明确「有用」走 `/useful` 而非 `/like`，`uk_useful_user_review` 已建。
- §0.4 实体：补充楼层/窗口号/属性标签字段说明。
- §3.x.2 / §3 白名单：明确 `GET /dishes/**` 通配放行（覆盖一期全部 dish 子路径）。

### 6.2 task 文档「架构师修订」标注（已确认，由本文定稿后落笔）
- task-02：品类方案定稿为复用 tags + 前端静态常量；`hot-search` 语义修正；`sortBy` 仅新增 `heat`。
- task-03：`POST /reviews/{id}/useful` 幂等切换语义定稿；废弃 `/like`；`useful_count` 新增列（非新表）；AC-4 改为「再点取消幂等」。
- task-04：`SecurityConfig` 改 `GET /dishes/**` 通配；列出精确白名单与仍需登录清单（见 §3.3）。
- task-05：admin DTO 扩展字段清单（见 §1.3）；web 表单枚举控件映射。
- task-01：无数据模型变更，确认复用 §3.x.5 白名单与 `/dishes?sortBy=heat`。

---

## 7. 实施顺序建议（依赖拓扑）

1. **task-04（白名单 + SecurityConfig 通配）** → 解除浏览接口 401 风险，前置所有前端任务。
2. **后端实体/DDL（§1.2）/ DTO/VO/ Mapper XML（§1.3）** → 提供字段底座。
3. **task-02 接口（suggest/hot-search/rising + heat 排序）** + **task-03（/useful 切换 + useful_count + ReviewVO 扩展）**。
4. **task-01（首页复用公开接口）** / **task-03 前端（详情页字段/评价区）** / **task-05（web 表单）** 并行。
5. 全域联调：三端字段名 camelCase 对齐（§5.y 影响面）。

---

## 8. 开放待确认项（不阻塞一期）

- 食堂级 `business_hours`：一期仅落 `stall.business_hours`，canteen 不扩（如产品需食堂营业时间，二期补）。
- `serve_period` 取值是否要国际标准枚举键（breakfast/lunch/dinner/midnight）还是中文——建议英文键 + web 表单中文 label，待 web 工程师确认。
- `limited` 用 TINYINT(0/1) 还是 Boolean——MyBatis-Plus `Integer` 映射 TINYINT 即可，VO 出参用 Boolean 更友好（建议 Boolean）。
