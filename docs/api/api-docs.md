# 校园食堂信息系统 API 接口文档

> 基础地址：`http://localhost:8080/api`
>
> Knife4j 地址：`http://localhost:8080/api/doc.html`
>
> 统一响应格式：`{ "code": 200, "message": "操作成功", "data": ... }`
>
> 登录鉴权：需要登录的接口在 Header 中携带 `Authorization: Bearer <token>`。

## 1. Knife4j 人工测试流程

1. 启动后端。
2. 浏览器打开 `http://localhost:8080/api/doc.html`。
3. 调用 `POST /auth/login`，使用示例账号 `20240001` 和密码 `123456`。
4. 复制返回的 `data.token`。
5. 点击 Knife4j 右上角 `Authorize`，填入 token。
6. 测试收藏、评价、清单、个人资料、上传、后台等需要登录的接口。

如果后端不在本机，把基础地址中的 `localhost` 替换为后端设备 IP，例如：

```text
http://192.168.1.23:8080/api
```

## 2. 当前接口实现状态总览

| 模块 | 接口 | 方法 | 是否需要登录 | 当前状态 | 用途 |
|---|---|---|---|---|---|
| 认证 | `/auth/email-code` | POST | 否 | 已实现 | 获取邮箱验证码 |
| 认证 | `/auth/login` | POST | 否 | 已实现 | 密码登录/邮箱验证码登录 |
| 认证 | `/auth/register` | POST | 否 | 已实现 | 邮箱验证并注册用户 |
| 认证 | `/auth/profile` | GET | 是 | 已实现 | 获取当前用户资料 |
| 认证 | `/auth/profile` | PUT | 是 | 已实现 | 修改昵称/头像 |
| 认证 | `/auth/stats` | GET | 是 | 已实现 | 获取收藏数、评价数 |
| 食堂 | `/canteens/banners` | GET | 否 | 已实现 | 首页轮播图 |
| 食堂 | `/canteens` | GET | 否 | 已实现 | 食堂列表 |
| 食堂 | `/canteens/images` | GET | 否 | 已实现 | 食堂名到图片列表映射 |
| 食堂 | `/canteens/stallDetail` | GET | 否 | 已实现 | 档口详情 |
| 食堂 | `/canteens/all` | GET | 否 | 已实现 | 食堂列表，包含下属档口 |
| 食堂 | `/stalls` | GET | 否 | 已实现 | 按食堂 ID 查询档口 |
| 菜品 | `/dishes/hot` | GET | 否 | 已实现 | 热门菜品 |
| 菜品 | `/dishes` | GET | 否 | 已实现 | 菜品分页/搜索/筛选 |
| 菜品 | `/dishes/{id}` | GET | 否 | 已实现 | 菜品详情 |
| 菜品 | `/dishes/{id}/view` | POST | 是 | 已实现 | 增加浏览量 |
| 收藏 | `/favorites` | GET | 是 | 已实现 | 我的收藏列表 |
| 收藏 | `/favorites/toggle` | POST | 是 | 已实现 | 收藏/取消收藏 |
| 收藏 | `/favorites/batch` | POST | 是 | 已实现 | 批量收藏 |
| 评价 | `/dishes/{dishId}/reviews` | GET | 否 | 已实现 | 菜品评价列表 |
| 评价 | `/reviews` | POST | 是 | 已实现 | 提交评价 |
| 评价 | `/reviews/{id}` | PUT | 是 | 已实现 | 修改自己的评价 |
| 评价 | `/reviews/{id}` | DELETE | 是 | 已实现 | 删除自己的评价 |
| 清单 | `/lists` | POST | 是 | 已实现 | 创建美食清单 |
| 清单 | `/lists` | GET | 是 | 已实现 | 我的清单列表 |
| 清单 | `/lists/{id}` | GET | 是 | 已实现 | 清单详情 |
| 清单 | `/lists/{id}` | DELETE | 是 | 已实现 | 删除清单 |
| 清单 | `/lists/share/{token}` | GET | 否 | 已实现 | 通过分享 token 查看清单 |
| 清单 | `/lists/{id}/collect-all` | POST | 是 | 已实现 | 清单一键收藏 |
| 上传 | `/upload/image` | POST | 是 | 已实现 | 上传图片 |
| 后台食堂 | `/admin/canteens` | POST | 管理员 | 已实现 | 新增食堂 |
| 后台食堂 | `/admin/canteens/{id}` | PUT | 管理员 | 已实现 | 编辑食堂 |
| 后台食堂 | `/admin/canteens/{id}` | DELETE | 管理员 | 已实现 | 删除食堂 |
| 后台档口 | `/admin/stalls` | POST | 管理员 | 已实现 | 新增档口 |
| 后台档口 | `/admin/stalls/{id}` | PUT | 管理员 | 已实现 | 编辑档口 |
| 后台档口 | `/admin/stalls/{id}` | DELETE | 管理员 | 已实现 | 删除档口 |
| 后台用户 | `/admin/users` | GET | 管理员 | 已实现 | 用户列表 |
| 后台用户 | `/admin/users/{id}/status` | PUT | 管理员 | 已实现 | 启用/禁用用户 |
| 后台用户 | `/admin/users/{id}/role` | PUT | 管理员 | 已实现 | 修改角色 |
| 后台评价 | `/admin/reviews` | GET | 管理员 | 已实现 | 全部评价列表 |
| 后台评价 | `/admin/reviews/{id}/hide` | PUT | 管理员 | 已实现 | 隐藏/显示评价 |
| 后台评价 | `/admin/reviews/{id}` | DELETE | 管理员 | 已实现 | 管理员删除评价 |
| 后台菜品 | `/admin/dishes` | GET | 管理员 | 已实现 | 后台全部菜品 |
| 后台菜品 | `/admin/dishes` | POST | 管理员 | 已实现 | 新增菜品 |
| 后台菜品 | `/admin/dishes/{id}` | PUT | 管理员 | 已实现 | 编辑菜品 |
| 后台菜品 | `/admin/dishes/{id}` | DELETE | 管理员 | 已实现 | 删除菜品 |
| 后台统计 | `/admin/stats/overview` | GET | 管理员 | 待完善 | 当前仍为占位返回 |
| 后台统计 | `/admin/stats/trend` | GET | 管理员 | 待完善 | 当前仍为占位返回 |
| 后台统计 | `/admin/stats/rank` | GET | 管理员 | 待完善 | 当前仍为占位返回 |

## 3. 认证与用户接口

### POST `/auth/email-code`

用途：向 `@bjtu.edu.cn` 校园邮箱发送 6 位验证码。同一邮箱同一用途 60 秒内不能重复发送，验证码 10 分钟有效。

请求体：

```json
{
  "email": "20240001@bjtu.edu.cn",
  "purpose": "login"
}
```

响应重点：

```json
{
  "message": "验证码已发送"
}
```

### POST `/auth/login`

用途：登录并获取 JWT Token。支持两种方式：密码登录、邮箱验证码登录。用户必须已经注册。

密码登录请求体：

```json
{
  "account": "20240001",
  "password": "123456"
}
```

`account` 可以填写用户名、学号、工号或邮箱。

验证码登录请求体：

```json
{
  "email": "20240001@bjtu.edu.cn",
  "code": "123456"
}
```

响应重点：

```json
{
  "token": "jwt-token",
  "userId": 1,
  "username": "20240001",
  "email": "20240001@bjtu.edu.cn",
  "nickname": "交大学子",
  "avatar": "http://localhost:8080/api/images/seed/dishes/tomato-egg.jpg",
  "role": "user"
}
```

### POST `/auth/register`

用途：使用校园邮箱验证码注册普通用户，并设置登录密码。注册前先调用 `/auth/email-code`，`purpose` 填 `register`。

```json
{
  "username": "20240002",
  "email": "20240002@bjtu.edu.cn",
  "code": "123456",
  "password": "123456",
  "nickname": "交大学子"
}
```

### GET `/auth/profile`

用途：获取当前登录用户资料。需要 token。

### PUT `/auth/profile`

用途：修改昵称或头像。需要 token。

```json
{
  "nickname": "新的昵称",
  "avatar": "/images/seed/dishes/tomato-egg.jpg"
}
```

### GET `/auth/stats`

用途：个人中心统计。

响应：

```json
{
  "favoriteCount": 6,
  "reviewCount": 3
}
```

## 4. 食堂与档口接口

### GET `/canteens/banners`

用途：首页轮播图。

### GET `/canteens`

用途：食堂列表。

### GET `/canteens/images`

用途：返回食堂名到图片 URL 列表的映射。

### GET `/canteens/stallDetail`

用途：查询档口详情。

测试示例：

```text
/canteens/stallDetail?canteenName=明湖餐厅&stallName=明湖一层基本伙食窗口
```

兼容参数：`canteen` 与 `canteenName` 都可以传食堂名，推荐使用 `canteenName`。

### GET `/canteens/all`

用途：返回食堂和下属档口，适合前端一次性渲染食堂结构。

### GET `/stalls`

用途：按食堂 ID 查询档口。

```text
/stalls?canteenId=1
```

## 5. 菜品接口

### GET `/dishes/hot`

用途：首页热门菜品推荐。

### GET `/dishes`

用途：菜品列表、搜索、筛选、排序。

测试示例：

```text
/dishes?page=1&pageSize=10&keyword=牛肉
/dishes?canteenId=1&sortBy=rating&sortOrder=desc
/dishes?minPrice=1000&maxPrice=2000&tag=recommended
```

常用参数：

| 参数 | 说明 |
|---|---|
| `page` | 页码，默认 1 |
| `pageSize` | 每页条数，默认 10 |
| `keyword` | 关键词，匹配菜品名或档口名 |
| `canteenId` | 食堂 ID |
| `stallId` | 档口 ID |
| `tag` | 标签，如 recommended、signature、halal |
| `minPrice` | 最低价格，单位分 |
| `maxPrice` | 最高价格，单位分 |
| `sortBy` | rating、collects、price、created_at |
| `sortOrder` | asc、desc |

### GET `/dishes/{id}`

用途：菜品详情。未登录可访问；登录后会额外返回 `isFavorited`、`hasReviewed`。

```text
/dishes/1
```

### POST `/dishes/{id}/view`

用途：进入详情页时增加浏览量。需要 token。

```text
/dishes/1/view
```

## 6. 收藏接口

### GET `/favorites`

用途：查看我的收藏菜品。需要 token。

```text
/favorites?page=1&pageSize=50
```

### POST `/favorites/toggle`

用途：收藏/取消收藏。需要 token。

```json
{
  "dishId": 1
}
```

响应：

```json
{
  "favorited": true
}
```

### POST `/favorites/batch`

用途：批量收藏，主要给清单“一键收藏”使用。需要 token。

```json
{
  "dishIds": [1, 2, 3]
}
```

## 7. 评价接口

### GET `/dishes/{dishId}/reviews`

用途：查看菜品评价。

```text
/dishes/1/reviews?page=1&pageSize=20
```

### POST `/reviews`

用途：提交评价。需要 token。同一用户对同一菜品只能评价一次。

```json
{
  "dishId": 1,
  "rating": 5,
  "content": "味道不错，分量也足。",
  "images": ["/images/seed/dishes/tomato-egg.jpg"]
}
```

### PUT `/reviews/{id}`

用途：修改自己的评价。需要 token。

```json
{
  "dishId": 1,
  "rating": 4,
  "content": "重新评价：整体不错。",
  "images": []
}
```

### DELETE `/reviews/{id}`

用途：删除自己的评价。需要 token。

## 8. 美食清单接口

### POST `/lists`

用途：创建美食清单。需要 token。

```json
{
  "name": "明湖餐厅必吃",
  "description": "适合第一次来明湖餐厅的同学",
  "dishIds": [1, 2, 6]
}
```

### GET `/lists`

用途：查看我的清单。需要 token。

### GET `/lists/{id}`

用途：查看清单详情。需要 token。

### DELETE `/lists/{id}`

用途：删除自己的清单。需要 token。

### GET `/lists/share/{token}`

用途：通过分享 token 查看清单。无需登录。

### POST `/lists/{id}/collect-all`

用途：将清单内全部菜品加入我的收藏。需要 token。

## 9. 图片上传接口

### POST `/upload/image`

用途：上传头像、评价图、菜品图。需要 token。

请求类型：`multipart/form-data`

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | 图片文件，支持 jpg/jpeg/png/webp |

响应：

```json
{
  "url": "http://localhost:8080/api/images/2026/06/uuid.jpg",
  "relativeUrl": "/images/2026/06/uuid.jpg"
}
```

建议将 `relativeUrl` 保存到数据库，后续通过 `ImageUrlUtil` 自动转换为完整 URL。`url` 可直接用于前端展示。

## 10. 后台管理接口

后台接口需要管理员 token。当前 Security 对 `/admin/**` 要求角色为 `admin`。

### 食堂/档口管理

新增食堂：`POST /admin/canteens`

```json
{
  "name": "测试食堂",
  "images": "[\"/images/seed/canteens/canteen-dining-hall.jpg\"]",
  "location": "主校区",
  "description": "Knife4j 测试新增食堂",
  "sortOrder": 99,
  "status": "open"
}
```

新增档口：`POST /admin/stalls`

```json
{
  "canteenId": 1,
  "name": "测试档口",
  "images": "[\"/images/seed/canteens/canteen-food-counter.jpg\"]",
  "location": "一层",
  "description": "Knife4j 测试新增档口",
  "avgRating": 0,
  "sortOrder": 99,
  "status": "open"
}
```

### 用户管理

用户列表：

```text
/admin/users?page=1&pageSize=10&role=user&status=active
```

禁用用户：

```json
{
  "status": "disabled"
}
```

修改角色：

```json
{
  "role": "admin"
}
```

### 菜品管理

新增菜品：`POST /admin/dishes`

```json
{
  "stallId": 1,
  "name": "测试菜品",
  "price": 1200,
  "description": "Knife4j 测试新增菜品",
  "images": ["/images/seed/dishes/tomato-egg.jpg"],
  "tags": "daily,recommended",
  "status": "on"
}
```

注意：当前编辑/删除菜品尚未严格校验“菜品是否属于当前管理员绑定档口”，后续应补权限边界。

### 评价审核

全部评价：

```text
/admin/reviews?page=1&pageSize=10&isHidden=0
```

隐藏/显示评价：

```text
PUT /admin/reviews/{id}/hide
```

删除评价：

```text
DELETE /admin/reviews/{id}
```

### 统计接口

以下接口当前仍为占位返回，后续需要接入真实统计 Service：

```text
GET /admin/stats/overview
GET /admin/stats/trend?days=7
GET /admin/stats/rank
```

## 11. 图片与价格约定

图片：

```text
数据库 images 字段保存 JSON 字符串，例如 ["/images/seed/dishes/tomato-egg.jpg"]。
后端响应时通过 ImageUrlUtil 拼接完整 URL。
```

价格：

```text
所有 price 均以“分”为单位。1200 表示 12 元。
```

## 12. 常见测试问题

### 401 / 403

说明接口需要 token 或角色权限不够。先登录，再在 Knife4j Authorize 中填入 token。

### 图片还是相对路径

检查 `app.public-base-url` 是否配置正确，例如：

```yaml
app:
  public-base-url: http://localhost:8080/api
```

### PowerShell 不能用 `<` 导入 SQL

进入 mysql 后使用：

```sql
source D:/Github/bjtu_food/bjtu_food/docs/bjtu_food_base.sql;
source D:/Github/bjtu_food/bjtu_food/docs/bjtu-food-seed.sql;
```
