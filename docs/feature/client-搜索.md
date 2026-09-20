# 搜索（A-03）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免登录）
> 返回：[功能总览](./README.md)

## 干什么

按关键词找菜。命中规则为**菜名或搜索别名**（别名由管理员在菜品表单里配置），并提供**热搜词条**入口帮助用户快速起搜。

## UI

- 首页顶部搜索入口 → 二级页 `pages/find/index`（该页为**核心板块，不得降级或移除**）。
- 页面构成：搜索框 + 热搜词 chips + 结果列表（`FindResults`，复用菜品卡片）。
- 热搜 chips 来自后端派生（一期无真实搜索词埋点，取热度最高的菜品名作词条）。

## 操作

1. 进入页 → 展示热搜词条（未输入时）。
2. 输入关键词 → 调 `GET /dishes?keyword=…` → 展示结果列表。
3. 点热搜 chip → 以该词发起搜索。
4. 点结果卡片 → 进菜品详情（A-04）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dishes?keyword=…` | 🔓 公开 | 关键词搜索：**同一端点**复用于首页/筛选/搜索；对 `dish.name` 与 `dish.alias` 同时匹配 |
| GET | `/dishes/hot-search` | 🔓 公开 | 热搜榜单 TOP10（一期语义：基于菜品热度的热门词条派生） |

## 字段

### 请求 · `GET /dishes`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `keyword` | string | 否 | 关键词，**同时匹配菜名 `dish.name` 与管理员配置的搜索别名 `dish.alias`**（搜索页主参数；空则不筛选） |
| `canteenId` | number | 否 | 按食堂筛选（搜索结果加食堂条件时传） |
| `stallId` | number | 否 | 按档口筛选 |
| `minPrice` / `maxPrice` | number | 否 | 价格区间，**单位：分**（端上「元」由 API 层转分后提交） |
| `sortBy` | string | 否 | 排序维度：`heat`=热度（首页默认）/ `rating`=评分 / `price`=价格 / `created_at`=上架时间 |
| `sortOrder` | string | 否 | 排序方向：`asc` 升序 / `desc` 降序（搜索一般用默认热度倒序） |
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（服务端有上限，超限被截断） |

### 响应 · `GET /dishes`（`data` = `PageResult<DishVO>`）

**分页外壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | DishVO[] | 当前页命中菜品行数组（端上以它为准） |
| `total` | number | 命中总条数（搜索页据此判「无结果」） |
| `page` / `pageSize` | number | 服务端归一化后的实际页码 / 每页条数 |

**行字段 `DishVO`（15 个；2026-09-20 §7.31 起删 `latitude` / `longitude`）**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `name` | string | 菜品名称 |
| `price` | number | **现价**（单位：分；端上转「元」展示，已含折扣） |
| `originalPrice` | number \| null | **原价**（单位：分，折扣前）；`originalPrice > price` 即表示有折扣、端上在原价上加删除线；无折扣为 null |
| `description` | string | 菜品描述 |
| `images` | string[] | 菜品图片 URL 数组（首图作封面；无图为空数组） |
| `stallName` | string | 档口名称 |
| `canteenName` | string | 食堂名称 |
| `floor` | string | 档口所在楼层（如 1F / 2F） |
| `avgRating` | number | 平均评分（缓存值，口径 = 仅未隐藏评价） |
| `ratingCount` | number | 评价数（同上口径） |
| `dietType` | string | 荤素 / 饮食属性：`meat`=荤 / `half`=半荤 / `veg`=素 / `halal`=清真 |
| `ingredients` | string | 主料 / 食材（逗号分隔**英文机器值**，端上映射中文展示）：`pork` 猪 / `beef` 牛 / `lamb` 羊 / `chicken` 鸡 / `duck` 鸭 / `fish` 鱼虾 / `egg` 蛋 / `tofu` 豆制品 / `mushroom` 菌菇 / `veg` 青菜 / `noodle` 面 / `rice` 米（**映射唯一真源 = client `api/dish.ts`**，见 `project_spec.md` §7.28） |
| `flavorTags` | string | 口味（逗号分隔**英文机器值**，端上映射中文展示）：`spicy` 辣 / `numbing` 麻 / `sour` 酸 / `sweet` 甜 / `salty` 咸 / `umami` 鲜 / `light` 清淡 / `heavy` 重口（**吸收原「辣度」语义**） |
| `serveTemp` | string | 冷热：`hot`=热食 / `room`=常温 / `ice`=冰 |

> **公开 `DishVO` 字段精简（2026-09-20 用户拍板，见 `project_spec.md` §7.26–§7.31）**：已删 `promoPrice` / `status` / `createdAt` / `canteenId` / `stallId` / `viewCount` / `tags` / `spiceLevel` / `region` / `windowNo` / `updatedAt` / **`latitude` / `longitude`（「坐标 + 距离」概念全链下线，§7.31）**；`spiceLevel` / `region` 由 `dietType` / `ingredients` / `flavorTags` / `serveTemp` 替换。**公开 VO 最终 15 字段**；搜索结果卡片与详情页均**不显示距离**（无定位、无 Haversine）。

### 响应 · `GET /dishes/hot-search`（`List<HotSearchVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `keyword` | string | 热搜词条（当前实现为热门菜品名）；**端上只消费这个字段** |
| `heat` | number | 该词条的派生热度分（**端上零消费**，仅为后端排序留痕） |

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `dish` | `name`、`alias` | 关键词匹配对象：菜名 + 管理员配置的搜索别名（逗号分隔） |
| `dish` | `view_count`、`rating_count`、`avg_rating` | 派生热搜词条的热度依据 |

## 答疑

### Q：为什么要配置 alias？美团那些搜索是怎么做的？

**A：alias 解决「用户叫法与菜卡名不一致」的第一层命中问题。**
菜卡叫「番茄炒蛋盖饭」，学生搜「西红柿炒蛋」「番茄盖饭」用 `LIKE` 天然搜不到；管理员登记别名即可命中（`name OR alias`）。它是**零架构成本的同义词替代方案**。

美团那类搜索是一整套检索系统、不是一张表：① 中文分词建**倒排索引**（不是 `LIKE %词%`）；② 相关性打分（BM25 / TF-IDF + 业务加权）；③ **同义词词典 / 实体归一**（西红柿=番茄、土豆=马铃薯；品牌/门店/菜品归一）；④ 拼音、首字母、错字纠错；⑤ 个性化排序（LBS 距离、点击/下单反馈、用户画像）；⑥ query 改写与意图识别（「好吃的面」→ 品类词）。

**本项目建议维持 `LIKE + alias`，不引 ES**——项目定型为「轻运营」，引入检索中间件属架构级变更，须重新拍板。若将来要往上走，性价比顺序建议：① 把 alias 升级为**可维护的同义词词表** → ② 拼音 / 首字母匹配 → ③ 再考虑 ES 分词打分。
