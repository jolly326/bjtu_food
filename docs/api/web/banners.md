# web/banners — 首页 Banner 管理

**归属**：`BannerAdminController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 维护 client 首页顶部轮播 Banner（[`GET /banners`](../client/banner.md) 的数据源）—— 图片、顺序、启停。公开端 `GET /banners` 契约**完全不变**（仍只出 `{ id, imageUrl }`）。

---

## GET /admin/banners

**用途**：Banner 列表（**含已停用**）

### 请求参数
无（**不分页、不筛选**，数量级极小）。

### 响应 `data` = `BannerAdminVO[]`
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | Banner ID |
| `imageUrl` | string | 轮播图**绝对 URL** |
| `order` | number | 展示顺序（升序） |
| `status` | string | `on` / `off` |
| `updatedAt` | string | 更新时间（`yyyy-MM-dd HH:mm:ss`；**列表不出 `createdAt`**） |

**排序**：按 `order` 升序。

---

## POST /admin/banners

**用途**：新增（默认**启用**、默认排在最后）

### 请求体 `BannerSaveReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `imageUrl` | string | **是** | 轮播图地址（1~500 字符）；经 [`POST /admin/upload`](./upload.md) 上传**原样入库**，出参由服务端统一转绝对 URL |

> **不含 `status` / `order`** —— 启停与排序各有独立端点。

### 响应 `data` = `BannerAdminVO`
新建的 Banner。

---

## PUT /admin/banners/{id}

**用途**：编辑（换图）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | Banner ID |

### 请求体 `BannerSaveReq`
同 `POST`。

### 响应 `data`
`null`。

---

## PUT /admin/banners/{id}/status

**用途**：**启停**（只改 `status`）

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | Banner ID |

### 请求体 `BannerStatusReq`
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | **是** | `on` 启用 / `off` 停用 |

### 响应 `data`
`null`。

---

## PUT /admin/banners/sort

**用途**：**整体提交顺序**（拖拽后一次提交）

### 请求体 `SortItemsReq`
`items`：`[{ id, order }]` 数组，整体替换顺序。**边界（全量行 / 非法提交 → `400`）见 [api/README 的「拖拽排序提交」](../README.md#拖拽排序提交)**。

### 响应 `data`
`null`。

---

## DELETE /admin/banners/{id}

**用途**：删除

### 路径参数
| 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | Banner ID |

### 响应 `data`
`null`。

> **无引用约束** —— Banner 不引用任何业务表，也不被引用。

---

## 错误码

| code | 条件 |
|---|---|
| `400` | `imageUrl` 为空 / 超长 / 非法；`status` 非法；**启用中已达上限 6 张**（新增或启用时）；排序提交非法 |
| `4001` | Banner 不存在（`PUT` / `DELETE` 目标不存在） |
| `401` | 未带 / token 失效 |

### 备注
- **出参字段统一叫 `order`**（库列 `banner.sort_order`，由持久层映射）—— 管理端各页的「展示顺序」出参一律用 `order`，避免同义异名。
- **启用上限 6 张**：`status='on'` 最多 6 条，新增默认启用、启停时校验。
- **不做跳转 / 不定时上下线 / 不配轮播行为**：跳转与时间窗都会扩出 `GET /banners` 出参契约，属演进预留（落地前须先拍板）。
- **停在 `status` 而非删除**：临时不想露出就停用（保留素材与顺序），删除是彻底移除。
