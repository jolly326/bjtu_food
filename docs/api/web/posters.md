# 运营端接口 · 海报

> **契约真源**：端点 / 入参 / 出参 / 错误码。通用约定见 [`api/README.md`](../README.md)。
> 列定义见 [`schema/poster.md`](../../schema/poster.md)。

## `GET /admin/posters`

| 项 | 口径 |
|---|---|
| 入参 | `canteenId?` · `stallId?` · `status?`（`on` / `off`）· `page` / `pageSize`（默认 20，上限 50） |
| 出参 | `AdminPageResult<PosterAdminVO>` |
| 排序 | **按 `sort` 升序、同值按 `id` 升序** —— 与学生端橱窗同序（**所见即学生所见**） |

**`PosterAdminVO`**

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 海报 ID |
| `stallId` | number \| null | 否 | 所属档口；🔴 **可空** = 未关联档口（纯运营内容） |
| `stallName` | string | 否 | 档口名 |
| `canteenName` | string | 否 | 食堂名（位置链路展示） |
| `imageUrl` | string | 否 | 海报绝对 URL（**必填**） |
| `sort` | number | 否 | 排序值（全局序，升序、小者靠前） |
| `status` | string | 否 | `on` 上橱窗 / `off` 不展示 |
| `updatedAt` | string | 否 | 更新时间 |

## `POST /admin/posters`

| 项 | 口径 |
|---|---|
| 入参 | `{ stallId, imageUrl, sort?, status? }` —— `status` 缺省 `on`；`sort` 缺省 = **当前全局最大值 + 1**（排到最后，空表首张为 `1`） |
| 出参 | `PosterAdminVO` |
| 错误码 | `400` `imageUrl` 缺失或非法 ｜ `status` 非法 ｜ `4001` 档口不存在（仅当传入 `stallId`） |

## `PUT /admin/posters/{id}`

| 项 | 口径 |
|---|---|
| 入参 | 同 `POST`（`sort` **不传 = 保持原排序值**，不重算） |
| 出参 | `PosterAdminVO` |
| 错误码 | `400` `imageUrl` 缺失或非法 ｜ `status` 非法 ｜ `4001` 海报不存在 / 档口不存在（仅当传入 `stallId`） |

## `DELETE /admin/posters/{id}`

| 项 | 口径 |
|---|---|
| 行为 | 物理删除（只删库记录；**COS 图片文件不回收**） |
| 错误码 | `4001` 海报不存在 |

> **图片上传复用 [`POST /admin/images`](./images.md)** —— 得绝对 URL 后经本接口入库转**相对路径**；`imageUrl` **不可传 `null` 清除**（无图海报无意义：暂不上橱窗用 `off`，要移除就删除）。
> **`sort` 是全局序** —— 学生端橱窗为一条序列，不按档口分组；改序后学生端**下一次拉取即生效**。
> **删除档口时其海报随外键 `ON DELETE CASCADE` 一并清理**（不经过本接口，也不阻塞档口删除）。