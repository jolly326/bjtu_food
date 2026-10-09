# api — 接口契约

**范围**：学生端（微信小程序 `client/`）与管理端（`web/`）消费的全部端点。
**契约真源**：本文与各资源文档；端上类型由后端 VO 经 SpringDoc 导出（见「契约同步」）。

> 各 feature 文档只描述**业务规则**（为什么、边界在哪），端点签名与字段一律以本文档为准。

## 资源索引

### 学生端（`client/`）

| 文档 | 覆盖端点 | 消费方 |
|---|---|---|
| [client/dishes.md](./client/dishes.md) | `/dishes*`（8 个） | 首页 · 搜索 · 详情 · 浏览计数 · 菜品问题反馈 · 评价 |
| [client/auth.md](./client/auth.md) | `/auth/*`（6 个） | 静默登录 · 邮箱认证 · 个人资料 · 注销 |
| [client/reviews.md](./client/reviews.md) | `/reviews/*` · `/report-reasons` | 删除本人评价 · 举报评价 |
| [client/my.md](./client/my.md) | `/my/*`（5 个） | 我的评价 · 系统通知 |
| [client/feedback.md](./client/feedback.md) | `/feedback` | 意见反馈 |
| [client/banner.md](./client/banner.md) | `/banners` | 首页轮播 |
| [client/upload.md](./client/upload.md) | `/upload/*` | 评价 / 反馈 / 菜品问题反馈 / 头像 的配图上传 |

### 管理端（`web/`）

| 文档 | 覆盖端点 |
|---|---|
| [web/auth.md](./web/auth.md) | 管理端登录与 `/admin/**` 的 `Authorization: Bearer` 鉴权 |
| [web/stalls.md](./web/stalls.md) | `/admin/canteens*` · `/admin/stalls*` |
| [web/dishes.md](./web/dishes.md) | `/admin/dishes*` |
| [web/dimensions.md](./web/dimensions.md) | `/admin/dish-dimensions*`（含子资源取值） |
| [web/views.md](./web/views.md) | `/admin/dish-views*` |
| [web/categories.md](./web/categories.md) | `/admin/dish-categories*` |
| [web/banners.md](./web/banners.md) | `/admin/banners*` |
| [web/report-reasons.md](./web/report-reasons.md) | `/admin/report-reasons*` |
| [web/reviews.md](./web/reviews.md) | `/admin/reviews*` |
| [web/feedback.md](./web/feedback.md) | `/admin/feedbacks*`（意见反馈 + 举报） |
| [web/corrections.md](./web/corrections.md) | `/admin/corrections*` |
| [web/users.md](./web/users.md) | `/admin/users*` |
| [web/alerts.md](./web/alerts.md) | `/admin/alerts`（安全告警记录，只读） |
| [web/dashboard.md](./web/dashboard.md) | `/admin/dashboard` |
| [web/upload.md](./web/upload.md) | `/admin/upload` |

---

## 通用约定

### 响应信封 `Result<T>`

**所有接口**的响应体统一为：

```json
{ "code": 200, "message": "操作成功", "data": null }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | 业务状态码（取值见下表） |
| `message` | string | 提示信息；失败时为可直接展示给用户的文案 |
| `data` | T \| null | 业务载荷；写操作类接口通常为 `null` |

### 业务状态码

| code | 含义 | 端上处置 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数 / 业务校验失败（含内容安检违规、限频） | Toast 展示 `message` |
| 401 | 未登录 / token 失效 | 静默重登并**自动重试原请求一次** |
| 403 | 无权限 | Toast 展示 `message` |
| **4001** | **资源不存在**（含「已下架」对外等价） | 展示文案 + **给恢复路径**（如返回首页） |
| **4031** | **邮箱未认证**（`bindEmail` 为空） | 跳转身份认证页 `pages/auth/index`，认证后返回原页续接 |
| 500 | 服务器异常 | 展示可重试失败态 |

> `4001` 与 `4031` 是通用**细分码**：让端上**不必解析 `message` 文本**即可给出差异化引导。

### 分页结构 `PageResult<T>`

**分页壳只有 `records` 一个字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | T[] | 当前页数据行（消费方以此为准） |

- **不回传** `total` / `page` / `pageSize` —— 页码信息由请求侧掌握。
- **分页结束判据 = 本页返回条数 < 请求的 `pageSize`**。
- 请求侧 `pageSize` 上限 **50**，超限服务端截断（限制单次拉取量，防「一次拉全量」的爬取方式）。

### 管理端分页结构 `AdminPageResult<T>`

**仅用于 `/admin/**`** —— 管理后台是「核对型」使用（要知道总数、要能翻页），因此比学生端多一个 `total`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | T[] | 当前页数据行 |
| `total` | number | 满足条件的**总条数**（用于「共 N 条」与总页数） |

- 管理端 `pageSize` 默认 **20**、上限 **50**（与学生端同一上限，统一由 `PageUtil` 归一化）；总页数 = `ceil(total / pageSize)`。
- **学生端不变**：`PageResult` 仍只回 `records`（沿用「加载更多」）。

### 管理端鉴权

管理端采用**独立管理员账号 + 账密登录**：`POST /admin/auth/login` 换取**管理端 JWT**（独立 secret 签名），随后请求带 `Authorization: Bearer <jwt>`；缺失 / 失效 → `401`（端上清 token 跳登录页）。前端**不持有口令**。详见 [web/auth.md](./web/auth.md)。

### 拖拽排序提交

六个 `PUT .../sort`（[web/dimensions.md](./web/dimensions.md) 维度 / 取值、[web/views.md](./web/views.md) 视图、[web/categories.md](./web/categories.md) 分类（种类字典）、[web/banners.md](./web/banners.md) Banner、[web/report-reasons.md](./web/report-reasons.md) 举报原因）**统一口径**：`items` 仍是该列表的**全量行**（缺行 ⇒ 顺序不完整 → `400`），服务端**整体替换** `order`；`items` 为空 / 含未知 `id` / 含重复 `id` / 同一 `order` 重复 → 一律 `400`「排序提交非法」。

### 金额

**所有金额一律以「分」为单位传输**（`number`）。

分 ↔ 元转换只允许在端上 `utils/money`（`fenToYuan` / `yuanToFen`）进行，**禁止在页面 / 组件内裸算**。

### 时间

所有时间字段为字符串，格式 `yyyy-MM-dd HH:mm:ss`（时区 Asia/Shanghai）。

端上按需格式化为展示形态（如 `YYYY-MM-DD`），**不得改名**。

### 可空性约定

| 约定 | 说明 |
|---|---|
| 字符串字段**恒非空** | 可空列由服务端 `COALESCE(..., '')` 兜底，端上**无需判空** |
| 数组字段**恒为数组** | 无数据时返回 `[]`，**不为 null** |
| 有意可空的字段 | 逐字段在资源文档中标注「可空 = 是」并说明 `null` 的语义 |

**有意可空字段（逐条登记）**：`originalPrice`（`null` = 无折扣）、`avatar`（`null` = 无头像）、`bindEmail`（`null` = 游客态）—— 与「值为 0 / 空串」语义不同，端上按各资源文档的「可空 = 是」判定。

> ⚠️ **`avatar` / `bindEmail` 的「空」有两种形态，按端区分**：**管理端 VO**（`/admin/**`）走「字符串恒非空」约定 ⇒ **一律 `COALESCE` 成空串**（见 [web/users.md](./web/users.md)：`bindEmail` 空串 = 未认证）；**登录态 VO**（`GET /auth/profile` 等）保留 **`null`**。前端**按所在端的约定判空**，不得跨端复用判空逻辑。

### 鉴权标记

| 标记 | 含义 | 未满足时 |
|---|---|---|
| 🔓 公开 | 免登录可调用 | — |
| 🔑 需登录 | 需 JWT 登录（**游客态亦可**，无需邮箱认证） | 401 |
| 🔐 认证 | 需学号邮箱认证（判据 = `bindEmail` 非空） | **4031** |

> 端上统一由 `useUserStore().isVerified()` 单点派生认证态，页面 / 组件不得散写判空。
> 上述标记针对**学生端**；管理端一律走**账密登录 + JWT**（见 [web/auth.md](./web/auth.md)）。

---

## 契约同步

后端 VO 是端上类型的**唯一真源**，链路如下：

```
后端 VO  →  SpringDoc 导出 openapi.json  →  openapi-typescript 生成 api.d.ts
```

**后端字段变更后必须刷新端上产物**，否则会退回弱类型、契约漂移不可感知：

```bash
# 启动后端后执行
cd client && npm run gen:api:fresh
```

**文档与代码的一致性**由测试保证：端上契约产物缺失或非法信封时 `mvn test` 失败。

## 撰写规范

- 每个端点固定六段：**鉴权+用途 → 参数 → 响应字段表 → 错误码 → 副作用 / 备注**
- **一个端点只在一份文档中出现一次**；被多处复用时（如 `GET /dishes/{id}`），在复用方的 feature 文档中**引用**本文档，不复制签名
- 字段表只写**传输形态**；对应数据库列、是否缓存列、能否修改 → 见 [schema/](../schema/)
- 业务规则、PV 口径、限频阈值、覆盖语义 → 见各 [功能文档](../func/client/)
