# web/stalls — 食堂与档口管理

**归属**：`CanteenAdminController`（`/admin/canteens*` 与 `/admin/stalls*` **同属此控制器** —— 不存在 `StallAdminController`）
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 食堂与档口均为**可维护实体**：A3 菜品录入**从下选中**，避免「自由文本错别字 → 重复实体」。

---

## GET /admin/canteens

**用途**：食堂列表（供菜品录入下拉）

### 请求参数
无（**不分页**，量级为十数条）。

### 响应 `data` = `CanteenAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 食堂 ID |
| `name` | string | 食堂名称 |
| `stallCount` | number | **其下档口数**（联表统计，供删除前判断与列表展示） |
| `location` | string | 食堂位置（恒非空串，无为空串） |
| `description` | string | 食堂描述（恒非空串） |
| `images` | string[] | 食堂图片绝对 URL 数组（有序，首图作封面；无图 `[]`） |
| `sortOrder` | number | 排序位（**本列表的排序键**，见下） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：`sortOrder` 升序 → `updatedAt` 降序。

> **全部业务列均可达**（[schema/README「管理端字段可达性」](../../schema/README.md)）：`location` / `description` / `images` / `sortOrder` 与 `name` 一样可读可写，在 **A1 详情抽屉**内查看与编辑（`sortOrder` 是**排序位**，本页**无拖拽排序入口**，直接在该表单里维护数值）。

---

## POST /admin/canteens

**用途**：新增食堂

### 请求体 `CanteenSaveReq`
| 字段 | 类型 | 必填 | 校验（服务端 `@Valid`） | 取值域 · 长度 |
|---|---|---|---|---|
| `name` | string | **是** | `@NotBlank`「食堂名称不能为空」；`@Size(max=64)`「食堂名称不能超过 64 字」 | 1~64 字（trim 后）；**全站唯一**（应用层查重，重名 `400`「食堂名称已存在」） |
| `location` | string | 否 | `@Size(max=128)`「食堂位置不能超过 128 字」 | ≤128 字；空串 = 空 |
| `description` | string | 否 | `@Size(max=512)`「食堂描述不能超过 512 字」 | ≤512 字；空串 = 空 |
| `images` | string[] | 否 | `@Size(max=5)`「食堂图片最多 5 张」；序列化后 ≤1024（服务层硬校验） | 有序，**首图作封面**；超长 `400`「食堂图片地址过长（序列化后不得超过 1024 字符）」 |
| `sortOrder` | number | 否 | 整数 | 排序位（升序，越小越靠前）；缺省落库默认 `0` |

> **不接收（服务端自持）**：`id`（走路径参数）、`updatedAt`（**时间列由 DB 时钟维护**，见 [schema/README「时间戳写入来源」](../../schema/README.md)）、`stallCount`（联表派生统计）。请求体中出现这些字段**一律忽略**（不落库、不回写）。

### 响应 `data` = `CanteenAdminVO`
新建的食堂。

### 写入细则
- **`images` 入库前还原为站内相对路径**（该列是相对路径列，出参才拼绝对地址；直接存绝对地址会导致出参二次拼域名、图片 404）。
- **张数不设静默截断**：超过 5 张或序列化超列宽一律 `400`。

---

## PUT /admin/canteens/{id}

**用途**：编辑食堂（**可编辑字段整体替换**：`name`；其余字段按下方空值语义）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 食堂 ID |

### 请求体 `CanteenSaveReq`
同 `POST`。

**逐字段空值语义**：
| 字段 | 缺省 / `null` | 给值 |
|---|---|---|
| `name` | **`400`**「食堂名称不能为空」（必填） | 整体替换（全站查重） |
| `location` / `description` | **保持原值** | 覆盖；`""` = 清空 |
| `images` | **保持原值** | 整体替换；`[]` = 清空 |
| `sortOrder` | **保持原值** | 覆盖 |

### 响应 `data`
`null`（成功即 `code=200`）。

---

## DELETE /admin/canteens/{id}

**用途**：删除食堂

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 食堂 ID |

### 响应 `data`
`null`（成功即 `code=200`）。

> **删除约束**：食堂下仍有档口时**禁止删除**（`400`），避免孤儿档口。

---

## GET /admin/stalls

**用途**：档口列表（供菜品录入下拉）

### 请求参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `canteenId` | number | 否 | 按食堂筛选；不传 = 全部 |

### 响应 `data` = `StallAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 档口 ID |
| `canteenId` | number | 所属食堂 ID |
| `canteenName` | string | **所属食堂名**（联表带出） |
| `name` | string | 档口名称 |
| `location` | string | 档口位置（恒非空串） |
| `floor` | string | 楼层（恒非空串，**无为空串**） |
| `windowNo` | string | 窗口号（恒非空串，**无为空串**；client 不展示，仅管理侧参考） |
| `description` | string | 档口描述（恒非空串） |
| `images` | string[] | 档口展示图片绝对 URL 数组（有序，首图作封面；无图 `[]`） |
| `avgRating` | number | **平均评分**（实时聚合，2 位小数；**无 approved 评价按 `0.00`，不为 `null`**） |
| `sortOrder` | number | 排序位（纠错候选档口列表的排序键；**本列表不按其排序**） |
| `dishCount` | number | **其下菜品数**（联表统计，供删除前判断） |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`） |

**排序**：`canteenId` 升序 → `name` 升序 → `updatedAt` 降序；**不分页**。

> **`dishCount`**：无菜品按 `0` 补齐（联表统计，供删除前判断）。
> **空值口径**：`location` / `floor` / `windowNo` / `description` 走 [api/README「可空性约定」](../README.md#可空性约定) 的**字符串恒非空**（可空列由服务端 `COALESCE(..., '')` 兜底，端上**无需判空**）—— 与 client 侧菜品详情的 `COALESCE(s.floor, '')`（`DishMapper.xml`）同口径。
> **全部业务列均可达**（[schema/README「管理端字段可达性」](../../schema/README.md)）：`location` / `description` / `images` / `sortOrder` 与 `canteenId` / `name` / `floor` / `windowNo` 一样可读可写，在 **A2 详情抽屉**内查看与编辑。

---

## POST /admin/stalls

**用途**：新增档口

### 请求体 `StallSaveReq`
| 字段 | 类型 | 必填 | 校验（服务端 `@Valid`） | 取值域 · 长度 |
|---|---|---|---|---|
| `canteenId` | number | **是** | `@NotNull`「请选择所属食堂」；`@Positive`「请选择所属食堂」 | 须为**已存在**的食堂 ID（服务层复核；缺失 / ≤0 / 不存在 → `400`「请选择所属食堂」） |
| `name` | string | **是** | `@NotBlank`「档口名称不能为空」；`@Size(max=64)`「档口名称不能超过 64 字」 | 1~64 字（trim 后）；**同食堂下唯一**（应用层查重，重名 `400`「该食堂下已存在同名档口」） |
| `floor` | string | 否 | 字典校验在服务层（真源 `FloorDict`，**不在 DTO 里枚举**，避免第二真源） | **受控字典 · 值即汉字**：`负一层` / `一层` / `二层` / `三层` / `四层`（真源见 [schema/stall.md](../../schema/stall.md)）；字典外值 → `400`「楼层不在预设范围内」；**空白串 → `400`「楼层不能为空」**（字典内无「空楼层」，**不支持清空**） |
| `windowNo` | string | 否 | `@Size(max=32)`「窗口号不能超过 32 字」 | ≤32 字（如 `3号窗口`）；`""` = 清空；client 不展示，仅管理侧参考 |
| `location` | string | 否 | `@Size(max=128)`「档口位置不能超过 128 字」 | ≤128 字；`""` = 清空 |
| `description` | string | 否 | `@Size(max=512)`「档口描述不能超过 512 字」 | ≤512 字；`""` = 清空 |
| `images` | string[] | 否 | `@Size(max=5)`「档口图片最多 5 张」；序列化后 ≤1024（服务层硬校验） | 有序，**首图作封面**；超长 `400`「档口图片地址过长（序列化后不得超过 1024 字符）」 |
| `sortOrder` | number | 否 | 整数 | 排序位；缺省落库默认 `0` |

> **不接收（服务端自持）**：`id`（走路径参数）、`updatedAt`（**时间列由 DB 时钟维护**，见 [schema/README「时间戳写入来源」](../../schema/README.md)）、`canteenName` / `dishCount` / `avgRating`（联表 / 派生统计）。请求体中出现这些字段**一律忽略**（不落库、不回写）。

### 响应 `data` = `StallAdminVO`
新建的档口。

---

## PUT /admin/stalls/{id}

**用途**：修改（**必填字段整体替换**：`canteenId` / `name`；其余可空字段**缺省 / `null` = 保持原值**，给值即覆盖）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 档口 ID |

### 请求体 `StallSaveReq`
同 `POST`（`canteenId` / `name` 必填；`floor` / `windowNo` / `location` / `description` / `images` / `sortOrder` 可选）。

**逐字段空值语义**：
| 字段 | 缺省 / `null` | 给值 |
|---|---|---|
| `canteenId` | **`400`**「请选择所属食堂」（必填） | 整体替换（须为已存在食堂） |
| `name` | **`400`**「档口名称不能为空」（必填） | 整体替换（同食堂下查重；**改名亦查重**） |
| `floor` | **保持原值** | 覆盖，须命中楼层字典；空白串 → `400`（字典内无「空楼层」，**不支持清空**） |
| `windowNo` | **保持原值** | 覆盖；`""` = 清空 |
| `location` / `description` | **保持原值** | 覆盖；`""` = 清空 |
| `images` | **保持原值** | 整体替换；`[]` = 清空（入库前还原相对路径） |
| `sortOrder` | **保持原值** | 覆盖 |

### 响应 `data`
`null`。

> ⚠️ **楼层归属档口**：`floor` 是该档口的**场所属性**，改动**影响该档口下所有菜品**（菜品详情位置行取档口楼层）。

---

## DELETE /admin/stalls/{id}

**用途**：删除档口

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 档口 ID |

### 响应 `data`
`null`。

> **删除约束**：档口下仍有菜品时**禁止删除**（`400`），避免孤儿菜品。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | 名称为空 / 超长（>64 字）；**食堂名全站重名**；**同食堂下档口重名**；`canteenId` 缺失 / ≤0 / 不存在；**`floor` 为空串或不在楼层字典内**；`windowNo` 超 32 字；`location` / `description` 超长；`images` 超 5 张或序列化后超 1024 字符；删除时其下仍有档口 / 菜品 |
| `4001` | 食堂 / 档口不存在（`PUT` / `DELETE` 目标不存在）→ 文案「食堂不存在」/「档口不存在」 |
| `401` | 未带 / token 失效 |

> **重名约束由应用层校验**（不加强 DB 唯一索引，避免历史重复数据阻塞）；改名场景同样查重（避免以已有名绕过约束）。
> **`DELETE` 幂等语义**：目标不存在返回 `4001`，端上按「已被删除」处理并刷新列表。

### 接口选型说明
- **动词与命名**：资源导向 + 复数名词（`/admin/canteens` / `/admin/stalls`）+ 标准动词（`GET` / `POST` / `PUT` / `DELETE`）。
- **`PUT` 而非 `PATCH`**：食堂 5 个 / 档口 8 个可编辑字段，且 web 端详情表单**恒全量提交**；必填字段走「整体替换」、可选字段走「缺省 = 保持原值」——与 [`PUT /admin/dishes/{id}`](./dishes.md) 的「未传字段不修改」同口径，**不引入 `PATCH`**。
- **不引入 HTTP `409`**：业务冲突（重名 / 删除受阻）归 `400` + 明确 `message`。
