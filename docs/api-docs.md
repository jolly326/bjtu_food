# 食在交大 — 前端 API 接口文档

> 基础地址：`http://localhost:8080/api`
>
> 统一响应格式：
> ```json
> { "code": 200, "message": "success", "data": <T> }
> ```
>
> 鉴权方式：`Authorization: Bearer <token>`（登录后所有接口自动携带）

---

## 目录

| 模块 | 接口 | 方法 | 用途 |
|------|------|------|------|
| 认证 | `/auth/login` | POST | 登录/注册 |
| 认证 | `/auth/profile` | PUT | 修改昵称/头像 |
| 认证 | `/auth/stats` | GET | 获取用户统计 |
| 菜品 | `/dishes/hot` | GET | 首页推荐菜品 |
| 菜品 | `/dishes` | GET | 搜索/筛选菜品 |
| 菜品 | `/dishes/:id` | GET | 菜品详情 |
| 食堂 | `/canteens/banners` | GET | 首页轮播图 |
| 食堂 | `/canteens` | GET | 食堂列表 |
| 食堂 | `/canteens/images` | GET | 食堂背景图片 |
| 食堂 | `/canteens/stallDetail` | GET | 档口详情 |
| 评价 | `/dishes/:id/reviews` | GET | 菜品评价列表 |
| 评价 | `/reviews` | POST | 提交评价 |
| 收藏 | `/favorites` | GET | 收藏列表 |
| 收藏 | `/favorites/toggle` | POST | 切换收藏（添加/取消） |
| 上传 | `/upload/image` | POST | 上传图片 |

---

## 1. 认证模块

### 1.1 登录 / 自动注册

```
POST /auth/login
```

**Request Body:**

```json
{
  "username": "20240001",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 是 | 学号 |
| `password` | string | 是 | 验证码（首次登录自动创建账号） |

**Response `data`:**

```json
{
  "token": "jwt_token_string",
  "userId": 1,
  "nickname": "交大学子",
  "avatar": "http://.../avatar.jpg",
  "role": "student"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | string | JWT token，后续接口通过 `Authorization: Bearer <token>` 传递 |
| `userId` | number | 用户 ID |
| `nickname` | string | 昵称 |
| `avatar` | string | 头像 URL（可能为空） |
| `role` | `"student" \| "visitor"` | 角色 |

---

### 1.2 修改资料

```
PUT /auth/profile
```

**Request Body**（两个字段至少传一个）：

```json
{
  "nickname": "新昵称",
  "avatar": "http://.../new_avatar.jpg"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nickname` | string | 否 | 新昵称，最多 20 字 |
| `avatar` | string | 否 | 头像图片 URL（**必须先通过上传接口获取 URL**，不可直接传本地临时路径） |

> ⚠️ **前端调用顺序**：`uni.chooseImage` → `POST /upload/image`（获取 URL）→ `PUT /auth/profile`（传入 URL）

**Response `data`:** 更新后的 `UserInfo` 对象

```json
{
  "id": 1,
  "nickname": "新昵称",
  "avatar": "http://.../new_avatar.jpg",
  "role": "student"
}
```

**前端类型 `UserInfo`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 用户 ID |
| `nickname` | string | 昵称 |
| `avatar` | string | 头像 URL |
| `role` | `"student" \| "visitor"` | 角色 |

---

### 1.3 获取用户统计

```
GET /auth/stats
```

**Response `data`:**

```json
{
  "favoriteCount": 12,
  "reviewCount": 8
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `favoriteCount` | number | 收藏数 |
| `reviewCount` | number | 评价数 |

**前端类型 `UserStats`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `favoriteCount` | number | 收藏数 |
| `reviewCount` | number | 评价数 |

---

## 2. 菜品模块

### 2.1 首页推荐菜品

```
GET /dishes/hot
```

无参数。

**Response `data`:** `Dish[]`

```json
[
  {
    "id": 1,
    "name": "红烧牛肉面",
    "price": 1500,
    "image": "http://.../dish1.jpg",
    "avgRating": 4.8,
    "ratingCount": 256,
    "tags": ["recommended", "signature"],
    "description": "浓汤慢炖，牛肉酥烂",
    "canteenName": "第一食堂",
    "stallName": "面面俱到"
  }
]
```

---

### 2.2 搜索 / 筛选菜品

```
GET /dishes
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | string | 否 | 关键词搜索（匹配菜品名、档口名） |
| `minPrice` | number | 否 | 最低价格（**分**） |
| `maxPrice` | number | 否 | 最高价格（**分**） |
| `sortBy` | `"rating" \| "price"` | 否 | 排序方式 |
| `page` | number | 否 | 分页页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |

> 前端当前版本暂未传 `page` / `pageSize`，后端可先默认返回所有匹配结果或分页。

**Response `data`:** 分页或数组

分页格式（若支持分页）：

```json
{
  "records": [ ...Dish[] ],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

若不分页则直接返回 `Dish[]`。

---

### 2.3 菜品详情

```
GET /dishes/:id
```

**Response `data`:**

```json
{
  "id": 1,
  "name": "红烧牛肉面",
  "price": 1500,
  "image": "http://.../dish1.jpg",
  "images": ["http://.../img1.jpg", "http://.../img2.jpg"],
  "avgRating": 4.8,
  "ratingCount": 256,
  "collectCount": 120,
  "tags": ["recommended", "signature"],
  "description": "浓汤慢炖，牛肉酥烂",
  "canteenName": "第一食堂",
  "stallName": "面面俱到",
  "ratingDistribution": [
    { "star": 5, "count": 156 },
    { "star": 4, "count": 68 },
    { "star": 3, "count": 22 },
    { "star": 2, "count": 8 },
    { "star": 1, "count": 2 }
  ]
}
```

**前端类型 `Dish`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 菜品 ID |
| `name` | string | 菜品名 |
| `price` | number | **单位：分**，前端显示时 /100 转为元 |
| `image` | string | 主图 URL |
| `images` | string[] | 多图（可选） |
| `avgRating` / `rating` | number | 评分（前端优先取 `avgRating`） |
| `ratingCount` | number | 评价数 |
| `collectCount` / `favoriteCount` | number | 收藏数 |
| `tags` | string[] | 标签数组，后端传英文 key（见下方映射表） |
| `description` | string | 描述 |
| `canteenName` / `canteen` | string | 所属食堂 |
| `stallName` | string | 所属档口 |

**标签映射表（后端 → 前端显示）：**

| 后端值 | 前端显示 |
|--------|----------|
| `recommended` | 必吃推荐 |
| `signature` | 招牌菜 |

**详情额外字段 `DishDetail`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `ratingDistribution` | `{ star: number, count: number }[]` | 各星级人数（1-5 星各一个） |

---

## 3. 食堂模块

### 3.1 首页轮播图

```
GET /canteens/banners
```

**Response `data`:** `BannerItem[]`

```json
[
  {
    "title": "🍜 交大美食季",
    "subtitle": "发现校园里的每一道美味",
    "image": "http://.../banner1.jpg"
  }
]
```

**前端类型 `BannerItem`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `title` | string | 标题 |
| `subtitle` | string | 副标题 |
| `image` | string | 背景图片 URL（可为空，前端用 SVG 占位） |

---

### 3.2 食堂列表

```
GET /canteens
```

**Response `data`:** `CanteenInfo[]`

```json
[
  {
    "name": "第一食堂",
    "description": "一食堂一层",
    "icon": "http://.../canteen_icon.svg"
  }
]
```

**前端类型 `CanteenInfo`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 食堂名称 |
| `location` / `description` | string | 位置描述 |
| `icon` | string | 图标 URL（可为空） |

> 前端优先取 `description`，取不到则取 `location`。

---

### 3.3 食堂背景图片

```
GET /canteens/images
```

**Response `data`:** `Record<string, string>` — key 为食堂名，value 为图片 URL

```json
{
  "第一食堂": "http://.../canteen1_bg.jpg",
  "第二食堂": "http://.../canteen2_bg.jpg"
}
```

> 前端用此接口获取食堂页的背景图片，若无返回则使用默认占位。

---

### 3.4 档口详情

```
GET /canteens/stallDetail
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `canteen` | string | 是 | 食堂名称 |
| `stallName` | string | 是 | 档口名称 |

**Response `data`:** `StallDetail`

```json
{
  "name": "面面俱到",
  "images": ["http://.../stall1.jpg", "http://.../stall2.jpg"],
  "location": "第一食堂",
  "description": "第一食堂·面面俱到，为您提供美味的校园餐饮体验。"
}
```

**前端类型 `StallDetail`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 档口名称 |
| `images` | string[] | 档口展示图片（支持多张） |
| `location` | string | 位置 |
| `description` | string | 描述文案 |

---

## 4. 评价模块

### 4.1 获取菜品评价列表

```
GET /dishes/:dishId/reviews
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 20 |

**Response `data`:**

分页格式：

```json
{
  "records": [ ...Review[] ],
  "total": 10,
  "page": 1,
  "pageSize": 20
}
```

或不分页直接返回 `Review[]`。

**前端类型 `Review`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 评价 ID |
| `userId` | number | 用户 ID |
| `userNickname` | string | 用户昵称（前端 fallback 为"匿名用户"） |
| `userAvatar` | string | 用户头像 URL（可为空） |
| `dishId` | number | 关联菜品 ID |
| `rating` | number | 评分（1-5） |
| `content` | string | 评价内容 |
| `images` | string[] | 评价图片 URL 列表 |
| `createdAt` | string | 创建时间（ISO 8601 格式） |

---

### 4.2 提交评价

```
POST /reviews
```

**Request Body:**

```json
{
  "dishId": 1,
  "rating": 5,
  "content": "超级好吃！",
  "images": ["http://.../review_img1.jpg"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dishId` | number | 是 | 菜品 ID |
| `rating` | number | 是 | 评分（1-5） |
| `content` | string | 是 | 正文，最长 500 字 |
| `images` | string[] | 否 | 图片 URL 列表（最多 3 张） |

**Response `data`:** 无 `data`，仅返回 `{ code: 200, message: "success" }`。

> ⚠️ **前端调用顺序**：`uni.chooseImage` → `POST /upload/image`（逐一上传获取 URL）→ `POST /reviews`（传入 URL 数组）


---

## 5. 收藏模块

### 5.1 获取收藏列表

```
GET /favorites
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 50 |

**Response `data`:** `Dish[]`（与菜品模块的 `Dish` 类型一致）

```json
[
  {
    "id": 1,
    "name": "红烧牛肉面",
    "price": 1500,
    "image": "http://.../dish1.jpg",
    ...
  }
]
```

---

### 5.2 切换收藏

```
POST /favorites/toggle
```

**Request Body:**

```json
{
  "dishId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dishId` | number | 是 | 菜品 ID |

**行为说明：** 服务端做 toggle（若已收藏则取消，未收藏则添加）。前端调用后重新拉取收藏列表。

**Response `data`:** 无 `data`，仅返回 `{ code: 200, message: "success" }`。

> ⚠️ **前端调用顺序**：`uni.chooseImage` → `POST /upload/image`（逐一上传获取 URL）→ `POST /reviews`（传入 URL 数组）


---

## 6. 上传模块

### 6.1 上传图片

```
POST /upload/image
Content-Type: multipart/form-data
```

**Form Data:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | 图片文件 |

**Response `data`:**

```json
{
  "url": "http://.../uploads/2024/01/xxx.jpg"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | string | 上传后可访问的完整图片 URL |

---

## 附录

### A. 价格单位约定

所有接口中价格字段 **以「分」为单位**，前端在显示时 `/100` 转换为「元」。

| 接口字段 | 单位 | 示例值 | 前端显示 |
|---------|------|--------|---------|
| `Dish.price` | 分 | 1500 | ¥15 |
| 搜索参数 `minPrice` / `maxPrice` | 分 | 1000 | 前端先转为分再传参 |

### B. 通用响应错误码

| `code` | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 / token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务端内部错误 |

### C. 标签枚举

请后端按以下枚举值返回 `tags` 字段：

| 值 | 含义 |
|----|------|
| `recommended` | 必吃推荐 |
| `signature` | 招牌菜 |
