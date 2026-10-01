# 菜品属性维度管理（B-06）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)
>
> ⚠️ 本管理后台（Web 后台）已软冻结，待后期整体重构移除（详见 [管理后台功能总览](./README.md) 顶部声明）。

## 介绍

维护**菜品描述属性的「维度」**（`dish_attribute_dimension`）——即属性表单上「有哪几行、每行叫什么、单值还是多值、按什么顺序展示」。

**模型要点（方案 A：值即中文）**：
- 维度只定义**字段**（`fieldKey` / `name` / `valueType` / `order`），**不定义取值**；
- **取值就是中文文本本身**，由录入者在 B-05 直接填写，编辑候选来自「全库已用值」去重；
- **无取值字典表**，故**新增取值零登记**。

**扩展路径（这是本模型的核心价值）**：
- 新增一个维度 = 在本功能插一行，**免 ALTER、免发版**；
- 新增一个取值 = 录入者直接输入中文，**零登记**。

**`fieldKey` 契约（强约束）**：`fieldKey` **恒等于** `dish.attributes` JSON 的键（camelCase）。改名等于换键，会让既有菜品该维度的值**键不匹配而读不出**，故**已投入使用的 `fieldKey` 禁止修改**。

**删除约束**：若已有菜品使用了该维度（`dish.attributes` 含该键），**禁止删除**，返回 `400` 并提示在用菜品数——删维度会让这些值失去维度定义、端上不再渲染，属静默数据丢失。

**排序**：`order` 升序；同时决定学生端详情页属性的展示顺序（与服务端拼装顺序同源）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/dish-dimensions` | 🔑 | 维度列表（按 `order` 升序；**不分页**，量级为个位数） |
| POST | `/admin/dish-dimensions` | 🔑 | 新增维度 |
| PUT | `/admin/dish-dimensions/{id}` | 🔑 | 修改维度（**`fieldKey` 一旦在用不可改**；不存在返回 `4001`） |
| DELETE | `/admin/dish-dimensions/{id}` | 🔑 | 删除维度；已有菜品在用返回 `400` |

## 字段

### 请求 · 新增 / 修改（`DishDimensionSaveReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `fieldKey` | string | 是 | 维度键（camelCase，1~32 字符；与 `dish.attributes` 的键一致；**在用后不可改**） |
| `name` | string | 是 | 维度中文名（1~32 字，如「饮食属性」「食材」「口味」「冷热」） |
| `valueType` | string | 是 | 取值类型：`single` 单值 / `multi` 多值（数组） |
| `order` | number | 否 | 展示顺序（升序，默认 0） |

### 响应 · `DishDimensionVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 维度 ID |
| `fieldKey` | string | 维度键 |
| `name` | string | 维度中文名 |
| `valueType` | string | `single` / `multi` |
| `order` | number | 展示顺序 |
| `dishCount` | number | **使用该维度的菜品数**（供删除前判断） |
| `usedValues` | string[] | **全库已用取值**（去重后的中文值，供 B-05 表单做候选；无为空数组） |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | `fieldKey` / `name` 非法或重名；`valueType` 非 `single` / `multi`；修改在用维度的 `fieldKey`；删除时仍有菜品在用 |
| `4001` | 维度不存在 |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `dish_attribute_dimension` | 增改删查 | 维度定义本体 |
| `dish` | 统计使用该维度的菜品数、去重取出已用中文值 | 删除约束与取值候选 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- 维度管理端增删改：`POST / PUT / DELETE`。
- **`dishCount` / `usedValues` 出参**：下发维度统计与取值候选（取值候选按维度聚合）。
- **`fieldKey` 不可改约束为新增**：现有无该保护，误改会造成静默数据不匹配。
