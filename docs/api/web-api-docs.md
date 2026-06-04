# 食在交大 · 浏览器管理端 API 接口文档

> 基础地址：`http://localhost:8080/api`
>
> Knife4j 地址：`http://localhost:8080/api/doc.html`
>
> 统一响应格式：`{ "code": 200, "message": "操作成功", "data": ... }`
>
> 鉴权方式：登录后在 Header 中携带 `Authorization: Bearer <token>`。

## 1. 对齐结论

当前浏览器管理端应调用后端真实接口，不再使用旧版 `/canteens`、`/stalls`、`/dishes`、`/users`、`/banners` 作为后台 CRUD 路径。

| 模块 | 浏览器端应使用 | 旧文档/旧前端问题 |
|---|---|---|
| 登录 | `POST /auth/login`，字段 `account/password` | 旧字段 `username/password` 不匹配 |
| 食堂管理 | `/admin/canteens` | 旧 CRUD 路径 `/canteens` 与公开查询冲突 |
| 档口管理 | `/admin/stalls` | 旧 CRUD 路径 `/stalls` 只适合公开查询 |
| 菜品管理 | `/admin/dishes` | 旧 CRUD 路径 `/dishes` 只适合公开查询 |
| 用户管理 | `/admin/users` | 后端只支持列表、状态、角色，不支持后台新增/删除用户 |
| 评价审核 | `/admin/reviews` | 旧路径 `/reviews` 是普通用户评价接口 |
| Banner | `GET /canteens/banners` | 后端暂未实现 Banner 后台 CRUD |

## 2. 登录

### POST `/auth/login`

用途：管理员登录浏览器后台。

请求体：

```json
{
  "account": "admin001",
  "password": "123456"
}
```

响应 `data`：

```json
{
  "token": "jwt-token",
  "userId": 2,
  "username": "admin001",
  "email": "admin001@bjtu.edu.cn",
  "nickname": "管理员",
  "avatar": "http://localhost:8080/api/images/seed/canteens/canteen-food-counter.jpg",
  "role": "admin"
}
```

浏览器端保存 `data.token`，后续所有 `/admin/**` 请求携带 Bearer Token。

## 3. 图片上传

### POST `/upload/image`

请求类型：`multipart/form-data`

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | 图片文件，支持 jpg/jpeg/png/webp |

响应 `data`：

```json
{
  "url": "http://localhost:8080/api/images/2026/06/uuid.jpg",
  "relativeUrl": "/images/2026/06/uuid.jpg"
}
```

建议保存 `relativeUrl` 到数据库；展示时可以直接使用 `url`，或由前端/后端拼接为完整地址。

## 4. 食堂管理

### GET `/admin/canteens`

用途：后台查看全部食堂，包含 `open/closed` 状态。

响应 `data`：`Canteen[]`

核心字段：

| 字段 | 说明 |
|---|---|
| `id` | 食堂 ID |
| `name` | 食堂名称 |
| `images` | 图片 JSON 字符串，例如 `["/images/a.jpg"]` |
| `location` | 食堂位置 |
| `description` | 食堂说明 |
| `sortOrder` | 排序值，越小越靠前 |
| `status` | `open` 营业、`closed` 关闭 |
| `createdAt` / `updatedAt` | 创建/更新时间 |

### POST `/admin/canteens`

```json
{
  "name": "测试食堂",
  "images": "[\"/images/seed/canteens/canteen-dining-hall.jpg\"]",
  "location": "主校区",
  "description": "后台测试新增食堂",
  "sortOrder": 99,
  "status": "open"
}
```

### PUT `/admin/canteens/{id}`

请求体同新增接口，可传完整字段。

### DELETE `/admin/canteens/{id}`

删除食堂。若食堂下仍有档口，后端会拒绝删除。

## 5. 档口管理

### GET `/admin/stalls`

用途：后台查看全部档口，包含 `open/closed` 状态。

核心字段：

| 字段 | 说明 |
|---|---|
| `id` | 档口 ID |
| `canteenId` | 所属食堂 ID |
| `name` | 档口名称 |
| `images` | 图片 JSON 字符串 |
| `location` | 档口位置 |
| `description` | 档口说明 |
| `avgRating` | 平均评分 |
| `sortOrder` | 排序值 |
| `status` | `open` 营业、`closed` 关闭 |

### POST `/admin/stalls`

```json
{
  "canteenId": 1,
  "name": "测试档口",
  "images": "[\"/images/seed/canteens/canteen-food-counter.jpg\"]",
  "location": "一层",
  "description": "后台测试新增档口",
  "avgRating": 0,
  "sortOrder": 99,
  "status": "open"
}
```

### PUT `/admin/stalls/{id}`

请求体同新增接口。

### DELETE `/admin/stalls/{id}`

删除档口。若档口下仍有菜品，后端会拒绝删除。

## 6. 菜品管理

### GET `/admin/dishes`

用途：后台查看全部菜品。

核心字段：

| 字段 | 说明 |
|---|---|
| `id` | 菜品 ID |
| `stallId` | 所属档口 ID |
| `name` | 菜品名称 |
| `price` | 价格，单位分，`1200` 表示 12 元 |
| `images` | 数据库实体返回时为 JSON 字符串；新增/编辑请求中为数组 |
| `tags` | 英文逗号分隔标签，如 `daily,recommended` |
| `status` | `on` 上架、`off` 下架 |
| `avgRating` / `ratingCount` | 平均评分/评价数 |
| `collectCount` | 收藏数，对应数据库 `favorite_count` |
| `viewCount` | 浏览数 |

### POST `/admin/dishes`

```json
{
  "stallId": 1,
  "name": "测试菜品",
  "price": 1200,
  "description": "后台测试新增菜品",
  "images": ["/images/seed/dishes/tomato-egg.jpg"],
  "tags": "daily,recommended",
  "status": "on"
}
```

### PUT `/admin/dishes/{id}`

请求体同新增接口。

### DELETE `/admin/dishes/{id}`

将菜品 `status` 设为 `off`（下架）。不再物理删除，保留评价和收藏数据。

## 7. 用户管理

### GET `/admin/users`

查询参数：

| 参数 | 说明 |
|---|---|
| `page` | 页码，默认 1 |
| `pageSize` | 每页条数，默认 10 |
| `role` | 可选，`user` 或 `admin` |
| `status` | 可选，`active` 或 `disabled` |

示例：

```text
/admin/users?page=1&pageSize=10&role=user&status=active
```

响应 `data` 为分页对象，列表在 `data.records`。

### PUT `/admin/users/{id}/status`

```json
{
  "status": "disabled"
}
```

### PUT `/admin/users/{id}/role`

```json
{
  "role": "admin"
}
```

注意：后端暂不支持后台新增用户、删除用户、修改用户名。用户创建应走注册流程；禁用账号用状态接口完成。密码修改请参考下方「11. 密码修改」。

## 8. 评价审核

### GET `/admin/reviews`

查询参数：

| 参数 | 说明 |
|---|---|
| `page` | 页码 |
| `pageSize` | 每页条数 |
| `isHidden` | 可选，`0` 显示、`1` 隐藏 |
| `isDeleted` | 当前参数保留，现阶段后端未做软删除 |

响应 `data` 为分页对象，列表在 `data.records`。

### PUT `/admin/reviews/{id}/hide`

切换隐藏/显示评价。

### DELETE `/admin/reviews/{id}`

管理员删除评价，并触发菜品评分重算。

## 9. 统计接口

```text
GET /admin/stats/overview
GET /admin/stats/trend?days=7
GET /admin/stats/rank
```

当前统计接口仍为占位返回，浏览器前端可以展示，但不要作为最终业务数据验收依据。

## 10. 轮播图管理（新增）

### GET `/admin/banners`

用途：后台查看全部轮播图，按 sort_order 排序。

响应 `data`：`BannerAdminVO[]`

| 字段 | 说明 |
|---|---|
| `id` | 轮播图 ID |
| `title` | 标题 |
| `subtitle` | 副标题 |
| `images` | 图片 URL 数组（完整可访问地址） |
| `type` | 跳转类型：`dish` / `url` |
| `targetId` | 跳转目标 ID（type=dish时） |
| `targetUrl` | 跳转目标 URL（type=url时） |
| `canteenId` | 关联食堂 ID（可选） |
| `sortOrder` | 排序值，越小越靠前 |
| `status` | `enabled` 启用、`disabled` 禁用 |
| `createdAt` / `updatedAt` | 创建/更新时间 |

### POST `/admin/banners`

```json
{
  "title": "首页推荐",
  "subtitle": "今日热菜推荐",
  "type": "dish",
  "targetId": 1,
  "canteenId": 1,
  "sortOrder": 1,
  "status": "enabled",
  "images": "[\"/images/xxx.jpg\"]"
}
```

### PUT `/admin/banners/{id}`

请求体同新增接口。

### DELETE `/admin/banners/{id}`

删除轮播图。

## 11. 密码修改（新增）

### PUT `/auth/password`

用途：修改当前用户密码。需要登录。

```json
{
  "oldPassword": "123456",
  "newPassword": "654321"
}
```

## 12. 公共查询接口

浏览器端如果需要只读展示，也可以使用公开接口：

```text
GET /canteens
GET /canteens/all
GET /stalls?canteenId=1
GET /dishes?page=1&pageSize=10
GET /dishes/{id}
GET /canteens/banners
```

这些接口会过滤公开状态，只适合学生端或前台展示，不适合后台管理全量数据。
