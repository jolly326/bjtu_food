# 食在交大 · 管理后台 API 接口文档

> 基础地址：`http://localhost:8080/api`
>
> 统一响应格式：
> ```json
> { "code": 200, "message": "success", "data": <T> }
> ```
>
> 鉴权方式：`Authorization: Bearer <token>`（登录后自动携带）
>
> 降级策略：后端未启动时自动降级为 Mock 数据（每个接口 5 秒超时），控制台输出 `[模块名] 降级到 Mock`

---

## 目录

| 模块 | 方法 | 路径 | 用途 |
|------|------|------|------|
| 认证 | POST | `/auth/login` | 管理员登录 |
| 食堂 | GET | `/canteens` | 食堂列表 |
| 食堂 | POST | `/canteens` | 新增食堂 |
| 食堂 | PUT | `/canteens/:id` | 更新食堂 |
| 食堂 | DELETE | `/canteens/:id` | 删除食堂 |
| 档口 | GET | `/stalls` | 档口列表 |
| 档口 | POST | `/stalls` | 新增档口 |
| 档口 | PUT | `/stalls/:id` | 更新档口 |
| 档口 | DELETE | `/stalls/:id` | 删除档口 |
| 菜品 | GET | `/dishes` | 菜品列表 |
| 菜品 | POST | `/dishes` | 新增菜品 |
| 菜品 | PUT | `/dishes/:id` | 更新菜品 |
| 菜品 | DELETE | `/dishes/:id` | 删除菜品 |
| 评论 | GET | `/reviews` | 评论列表 |
| 评论 | POST | `/reviews` | 新增评论 |
| 评论 | PUT | `/reviews/:id` | 更新评论 |
| 评论 | DELETE | `/reviews/:id` | 删除评论 |
| 用户 | GET | `/users` | 用户列表 |
| 用户 | POST | `/users` | 新增用户 |
| 用户 | PUT | `/users/:id` | 更新用户资料 |
| 用户 | DELETE | `/users/:id` | 删除用户 |
| 用户 | PUT | `/users/:id/toggle-status` | 切换用户状态 |
| Banner | GET | `/banners` | Banner 列表 |
| Banner | POST | `/banners` | 新增 Banner |
| Banner | PUT | `/banners/:id` | 更新 Banner |
| Banner | DELETE | `/banners/:id` | 删除 Banner |

---

## 1. 认证模块

### 1.1 管理员登录

```
POST /auth/login
```

**Request Body:**

```json
{
  "username": "admin",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 是 | 管理员用户名 |
| `password` | string | 是 | 管理员密码 |

**Response `data`:**

```json
{
  "token": "jwt_token_string",
  "username": "admin"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | string | JWT token，后续接口通过 `Authorization: Bearer <token>` 传递 |
| `username` | string | 管理员用户名 |

---

## 2. 食堂模块

### 2.1 食堂列表

```
GET /canteens
```

无参数。

**Response `data`:** `Canteen[]`

```json
[
  {
    "id": 1,
    "name": "学苑食堂",
    "image": "http://.../canteen.jpg",
    "location": "主校区东侧",
    "description": "两层大型食堂，品种丰富",
    "sort_order": 1,
    "status": "active",
    "created_at": "2024-01-01T00:00:00.000Z",
    "updated_at": "2024-01-01T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 食堂 ID |
| `name` | string | 食堂名称 |
| `image` | string | 图片 URL（可能为空） |
| `location` | string | 位置 |
| `description` | string | 描述 |
| `sort_order` | number | 排序号 |
| `status` | `"active" \| "inactive"` | 状态 |

---

### 2.2 新增食堂

```
POST /canteens
```

**Request Body:**

```json
{
  "name": "学苑食堂",
  "image": "",
  "location": "主校区东侧",
  "description": "两层大型食堂",
  "sort_order": 1,
  "status": "active"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 食堂名称 |
| `image` | string | 否 | 图片，多张用 `\|\|\|` 拼接 |
| `location` | string | 否 | 位置 |
| `description` | string | 否 | 描述 |
| `sort_order` | number | 否 | 排序号 |
| `status` | string | 否 | 默认 `active` |

**Response `data`:** 创建的 `Canteen` 对象

---

### 2.3 更新食堂

```
PUT /canteens/:id
```

**Request Body:** 部分字段，同新增

**Response `data`:** 更新后的 `Canteen` 对象

---

### 2.4 删除食堂

```
DELETE /canteens/:id
```

无 Request Body。**Response `data`:** 无

---

## 3. 档口模块

### 3.1 档口列表

```
GET /stalls
```

无参数。

**Response `data`:** `Stall[]`

```json
[
  {
    "id": 1,
    "canteen_id": 1,
    "name": "麻辣香锅",
    "image": "",
    "location": "",
    "description": "自选麻辣香锅",
    "avg_rating": 4.5,
    "sort_order": 1,
    "status": "active",
    "created_at": "2024-01-10T00:00:00.000Z",
    "updated_at": "2024-01-10T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 档口 ID |
| `canteen_id` | number | 所属食堂 ID |
| `name` | string | 档口名称 |
| `image` | string | 图片 URL |
| `location` | string | 位置描述 |
| `description` | string | 描述 |
| `avg_rating` | number | 平均评分 |
| `sort_order` | number | 排序号 |
| `status` | `"active" \| "inactive"` | 状态 |

### 3.2 新增 / 更新 / 删除

```
POST   /stalls         新增（同食堂新增模式）
PUT    /stalls/:id     更新
DELETE /stalls/:id     删除
```

---

## 4. 菜品模块

### 4.1 菜品列表

```
GET /dishes
```

无参数。

**Response `data`:** `Dish[]`

```json
[
  {
    "id": 1,
    "stall_id": 1,
    "name": "牛肉拉面",
    "image": "",
    "price": 15,
    "tags": "[\"招牌菜\",\"必吃推荐\"]",
    "description": "正宗兰州牛肉拉面",
    "avg_rating": 4.8,
    "rating_count": 50,
    "favorite_count": 40,
    "view_count": 500,
    "status": "active",
    "created_at": "2024-01-10T00:00:00.000Z",
    "updated_at": "2024-01-10T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 菜品 ID |
| `stall_id` | number | 所属档口 ID |
| `name` | string | 菜品名称 |
| `image` | string | 图片 URL |
| `price` | number | 价格（单位：元） |
| `tags` | string | JSON 字符串数组 |
| `description` | string | 描述 |
| `avg_rating` | number | 平均评分 |
| `rating_count` | number | 评价数 |
| `favorite_count` | number | 收藏数 |
| `view_count` | number | 浏览数 |
| `status` | `"active" \| "inactive"` | 状态 |

### 4.2 新增 / 更新 / 删除

```
POST   /dishes        新增
PUT    /dishes/:id    更新
DELETE /dishes/:id    删除
```

---

## 5. 评论模块

### 5.1 评论列表

```
GET /reviews
```

无参数。

**Response `data`:** `Review[]`

```json
[
  {
    "id": 1,
    "user_id": 1,
    "dish_id": 3,
    "rating": 5,
    "content": "味道正宗，牛肉很大块！",
    "images": "",
    "is_hidden": 0,
    "created_at": "2024-03-01T00:00:00.000Z",
    "updated_at": "2024-03-01T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 评论 ID |
| `user_id` | number | 用户 ID |
| `dish_id` | number | 菜品 ID |
| `rating` | number | 评分（1-5） |
| `content` | string | 内容 |
| `images` | string | 图片 URL，多张用 `\|\|\|` 拼接 |
| `is_hidden` | number | 是否隐藏（0/1） |

### 5.2 新增 / 更新 / 删除

```
POST   /reviews        新增
PUT    /reviews/:id    更新
DELETE /reviews/:id    删除
```

---

## 6. 用户模块

### 6.1 用户列表

```
GET /users
```

无参数。

**Response `data`:** `User[]`

```json
[
  {
    "id": 1,
    "username": "zhangsan",
    "password": "123456",
    "nickname": "张三",
    "avatar": "",
    "role": "user",
    "status": "active",
    "created_at": "2024-01-01T00:00:00.000Z",
    "updated_at": "2024-01-01T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 用户 ID |
| `username` | string | 用户名 |
| `password` | string | 密码（明文，仅供管理后台使用） |
| `nickname` | string | 昵称 |
| `avatar` | string | 头像 URL |
| `role` | `"admin" \| "user"` | 角色 |
| `status` | `"active" \| "disabled"` | 状态 |

### 6.2 新增 / 更新 / 删除 / 切换状态

```
POST   /users                 新增
PUT    /users/:id             更新用户资料
DELETE /users/:id             删除
PUT    /users/:id/toggle-status  切换启用/禁用

PUT /users/:id 的 Request Body:
{
  "nickname": "新昵称",
  "username": "new_username",
  "password": "new_password",
  "avatar": "http://.../avatar.jpg"
}
```

---

## 7. Banner 模块

### 7.1 Banner 列表

```
GET /banners
```

无参数。

**Response `data`:** `Banner[]`

```json
[
  {
    "id": 1,
    "title": "开学季优惠",
    "image": "http://.../banner.jpg",
    "type": "carousel",
    "target_id": null,
    "target_type": null,
    "canteen_id": null,
    "sort_order": 1,
    "status": "active",
    "created_at": "2024-09-01T00:00:00.000Z",
    "updated_at": "2024-09-01T00:00:00.000Z"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | Banner ID |
| `title` | string | 标题 |
| `image` | string | 图片 URL |
| `type` | string | 类型（如 `carousel`） |
| `sort_order` | number | 排序号 |
| `status` | `"active" \| "inactive"` | 状态 |

### 7.2 新增 / 更新 / 删除

```
POST   /banners        新增
PUT    /banners/:id    更新
DELETE /banners/:id    删除
```

---

## 附录

### A. 通用响应错误码

| `code` | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 / token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### B. Mock 数据降级流程

```
请求发起
  ↓
fetch(`{API_BASE_URL}{path}`)
  ↓ 成功 ↙        ↘ 5秒超时/网络错误
返回真实数据        输出 [模块名] 降级到 Mock
                    ↓
                 返回本地 Mock 数据（按引用修改，支持 CRUD）
```

Mock 数据在页面刷新后重置为初始状态。后端启动后自动切换为真实数据。
