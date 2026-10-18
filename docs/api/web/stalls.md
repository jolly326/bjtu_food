# 运营端接口 · 档口

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/stall.md`](../../schema/stall.md)。

## `GET /admin/stalls`

| 项 | 口径 |
|---|---|
| 入参 | `canteenId`（可选）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<StallVO>` |
| 排序 | 按 `id` 升序 |

**`StallVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 档口 ID |
| `canteenId` | number | 否 | 所属食堂 |
| `canteenName` | string | 否 | 所属食堂名 |
| `name` | string | 否 | 档口名称 |
| `floor` | string | **是** | 楼层（受控字典） |
| `windowNo` | string | **是** | 窗口号 |
| `dishCount` | number | 否 | 菜品数（只读统计） |
| `posterCount` | number | 否 | 海报数（只读统计，含已下架；增删改在 [posters](./posters.md)） |
| `updatedAt` | string | 否 | 更新时间 |

## `POST /admin/stalls`

| 项 | 口径 |
|---|---|
| 入参 | `{ canteenId, name, floor?, windowNo? }` |
| 出参 | `StallVO` |
| 错误码 | `400` `canteenId` / `name` 缺失 ｜ **同食堂下同名已存在** ｜ `4001` 食堂不存在 |

## `PUT /admin/stalls/{id}`

| 项 | 口径 |
|---|---|
| 入参 | 同 `POST` |
| 出参 | `StallVO` |
| 错误码 | `400` 同食堂下同名已存在 ｜ `4001` 档口不存在 |

## `DELETE /admin/stalls/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除 |
| 🔴 边界 | **该档口下仍有菜品时拒绝** |
| 错误码 | `400` 存在下属菜品 ｜ `4001` 档口不存在 |

> **海报不挂在本接口** —— 独立为 [`posters`](./posters.md)（一档口多张、固定序、上下架）；删档口时其海报随外键 `ON DELETE CASCADE` 一并清理（**不阻塞**删除，仅菜品构成删除阻力）。
> **`floor` 是受控字典**（值即汉字 `负一层`…`四层`），取值由服务端校验。
