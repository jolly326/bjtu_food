# 菜品管理（B-05）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)

## 介绍

维护**菜品（`dish`）**——平台的核心主数据。菜品**由管理员独占录入**：学生端无菜品写接口（红线），学生发现信息问题只能走反馈（A-10）/ 纠错（A-16）通道，由管理员在本功能录入或在 B-11 采纳。

**录入即生效**：无独立审核环节；公开可见性唯一判据是 `status='on'`。

**归属**：菜品必须挂靠一个档口（`stallId`）；食堂名与楼层由 `stall → canteen` 联表带出，菜品自身**不冗余存**食堂信息。

**价格**：`price` 为现价（**单位：分**，已含折扣，唯一价格数据源）；`original_price` 为原价（可空）；`originalPrice > price` 即视为有折扣。

**配图**：多图，首图为封面（学生端列表 `coverImage` 取首图）。**首图必填**——无封面菜品在瀑布流里是灰底占位，属低质量数据。图片经 `POST /admin/upload/image` 上传后落绝对 URL。

**描述属性（动态属性模型）**：键 = 维度 `fieldKey`，**值即中文文本**（`single` 为字符串、`multi` 为字符串数组），**无取值字典表**。维度由 B-06 维护；本功能录入时按维度渲染表单，取值为自由输入 + 「全库已用值」候选。

**菜品大类 `mealType`**：决定学生在首页筛选视图里能按哪个大类找到它。**值域由服务端 `GET /dishes/views` 下发**（唯一真源），管理端零硬编码。

**删除**：**物理删除**，并**级联清理该菜品的评价**（`review` 一并删除）；不保留软删标记（无 `is_deleted` 语义）。删除后学生端访问该菜品返回 `4001`。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/dishes` | 🔑 | 菜品列表（分页 + 筛选，见下） |
| GET | `/admin/dishes/{id}` | 🔑 | 菜品详情（编辑回填用；不存在返回 `4001`） |
| POST | `/admin/dishes` | 🔑 | 新建菜品 |
| PUT | `/admin/dishes/{id}` | 🔑 | 修改菜品（不存在返回 `4001`） |
| PUT | `/admin/dishes/{id}/status` | 🔑 | 上下架切换（幂等） |
| DELETE | `/admin/dishes/{id}` | 🔑 | 物理删除 + 级联清理评价 |

### 请求参数（`GET /admin/dishes`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |
| `keyword` | string | 否 | 按菜品名模糊匹配 |
| `canteenId` | number | 否 | 按食堂筛选（联 `stall`） |
| `stallId` | number | 否 | 按档口筛选 |
| `mealType` | string | 否 | 按菜品大类筛选 |
| `status` | string | 否 | `on` / `off`；不传 = 全部（管理端需看到下架的） |

## 字段

### 请求 · 新建 / 修改（`DishSaveReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `name` | string | 是 | 菜品名称（1~64 字） |
| `stallId` | number | 是 | 所属档口 ID（必须存在） |
| `price` | int | 是 | 现价（**单位：分**，> 0） |
| `originalPrice` | int | 否 | 原价（分）；须 > `price` 才构成折扣，否则 `400` |
| `mealType` | string | 是 | 菜品大类（值域 = `GET /dishes/views`） |
| `description` | string | 否 | 菜品描述（≤512 字） |
| `images` | string[] | 是 | 配图绝对 URL 列表（**首图必填**，≤5 张） |
| `attributes` | object | 否 | 描述属性（键 = 维度 `fieldKey`，**值 = 中文文本 / 数组**；仅含该菜实际拥有的维度） |

### 响应 · `DishAdminVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 菜品 ID |
| `name` | string | 菜品名称 |
| `price` | number | 现价（分） |
| `originalPrice` | number \| null | 原价（分，无折扣为 `null`） |
| `description` | string | 描述（无为空串） |
| `images` | string[] | 配图绝对 URL 数组（无图空数组） |
| `stallId` | number | 所属档口 ID |
| `stallName` | string | 档口名（联表带出） |
| `canteenId` | number | 所属食堂 ID（经档口联表） |
| `canteenName` | string | 食堂名（联表带出） |
| `floor` | string | 档口楼层（联表带出） |
| `mealType` | string | 菜品大类 |
| `attributes` | object | 描述属性（键 = `fieldKey`，值 = 中文 / 数组；无为空对象） |
| `status` | string | `on` / `off` |
| `viewCount` | number | 浏览量（只读，学生浏览累计） |
| `avgRating` | number \| null | 平均评分（只读，无评价为 `null`） |
| `ratingCount` | number | 评价数（只读） |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | 名称 / 价格非法；首图缺失；`stallId` 不存在；`mealType` 不在值域；`originalPrice <= price` |
| `4001` | 菜品不存在 |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `dish` | 增改删查 + 价格 / 描述 / 图片 / 属性 / `meal_type` / `status` | 菜品本体 |
| `stall` | 读（校验归属、回填名称与楼层） | 所属档口 |
| `canteen` | 读（经档口回填食堂名） | 所属食堂 |
| `dish_attribute_dimension` | 读（渲染属性表单、校验 `fieldKey` 合法） | 属性维度（B-06） |
| `review` | 删除时级联清理 | 该菜品的评价 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- **首图改为必填**：现有实现允许无图菜品；本功能要求 `images` 至少 1 张（无封面 = 低质量数据）。
- **新增 `canteenId` / `canteenName` / `floor` 出参**：现有列表只回档口名，食堂与楼层由前端另查。
- **新增按 `canteenId` / `mealType` 筛选**：现有列表仅支持关键词与档口。
- **删除语义明示**：物理删除 + 级联清理评价（现有实现已如此，但文档未固化）。
