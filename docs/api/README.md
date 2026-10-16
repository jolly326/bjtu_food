# api — 接口契约

**范围**：学生端（微信小程序 `client/`）与运营端（`web/`）消费的全部端点。
**契约真源**：本文与各资源文档；端上类型由后端 **Pydantic 模型经 FastAPI 导出**（见「契约同步」）。

> 各 feature 文档只描述**业务规则**（为什么、边界在哪），端点签名与字段一律以本目录为准。

## 资源索引

### 学生端（`client/`）

| 文档 | 覆盖端点 | 消费方 |
|---|---|---|
| [client/auth.md](./client/auth.md) | `POST /auth/wechat-login` | openid 静默识别 |
| [client/posters.md](./client/posters.md) | `GET /posters` | 海报页 |
| [client/dishes.md](./client/dishes.md) | `GET /dishes` | 推荐页 |
| [client/canteens.md](./client/canteens.md) | `GET /canteens` | 食堂筛选 |
| [client/records.md](./client/records.md) | `GET /my/records` · `POST /my/records` · `PATCH /my/records/{id}` · `DELETE /my/records/{id}` | 我的食谱 · 去品尝 · 评分 · 取消品尝 |
| [client/issues.md](./client/issues.md) | `GET /dish-issue-options` · `POST /dishes/{id}/issues` | 菜品纠错 |

### 运营端（`web/`）

| 文档 | 覆盖端点 |
|---|---|
| [web/auth.md](./web/auth.md) | 登录与 `/admin/**` 的 `Authorization: Bearer` 鉴权 |
| [web/canteens.md](./web/canteens.md) | `/admin/canteens*` |
| [web/stalls.md](./web/stalls.md) | `/admin/stalls*`（含海报） |
| [web/dishes.md](./web/dishes.md) | `/admin/dishes*` |
| [web/records.md](./web/records.md) | `/admin/dish-records*`（评分数据） |
| [web/issues.md](./web/issues.md) | `/admin/dish-issues*` · `/admin/dish-issue-options*` |
| [web/images.md](./web/images.md) | `POST /admin/images` |

---

## 通用约定

### 服务基路径

后端在**根路径**提供服务（**无版本段**）：端上 base = 服务域名本身（`client/.env.*` 与 `web/.env.*` 的 `VITE_API_BASE_URL`），下文各端点的路径即**相对 base 的路径**。部署口径见 [`secur/S-04`](../secur/S-04-凭据与部署边界.md) §二。

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
| 400 | 参数 / 业务校验失败 | Toast 展示 `message` |
| 401 | 未登录 / token 失效 | 静默重登并**自动重试原请求一次** |
| 403 | 无权限（**管理端专用**） | Toast 展示 `message` |
| **429** | **请求过于频繁**（限频，等待后可重试） | Toast 展示 `message` + **退避倒计时**（按文案里的秒数禁用入口，结束后自动恢复） |
| **4001** | **资源不存在**（含「已下架」对外等价） | 展示文案 + **给恢复路径** |
| 500 | 服务器异常 | 展示可重试失败态 |

> `4001` 是通用**细分码**：让端上**不必解析 `message` 文本**即可给出差异化引导。
> **无「邮箱未认证」码** —— 学生端不做认证（见 [`func/client/README.md`](../func/client/README.md) 的「合规与不做的事」）。

### 分页结构 `PageResult<T>`

**学生端分页壳只有 `records` 一个字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | T[] | 当前页数据行 |

- **不回传** `total` / `page` / `pageSize` —— 页码信息由请求侧掌握。
- **分页结束判据 = 本页返回条数 < 请求的 `pageSize`**。
- 请求侧 `pageSize` 上限 **50**，超限服务端截断。

### 运营端分页结构 `AdminPageResult<T>`

**仅用于 `/admin/**`** —— 后台是「核对型」使用（要知道总数、要能翻页）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | T[] | 当前页数据行 |
| `total` | number | 满足条件的**总条数** |

- 运营端 `pageSize` 默认 **20**、上限 **50**。

### 鉴权

| 标记 | 含义 | 未满足时 |
|---|---|---|
| 🔓 公开 | **仅 `POST /auth/wechat-login`** —— 换取凭证本身不能要求凭证 | — |
| 🔑 需登录 | **其余全部端点**（学生端与运营端皆然）；请求须带 `Authorization: Bearer <token>` | `401` |

- 🔴 **学生端所有业务端点统一要求凭证** —— 端上冷启动 `wx.login` → `POST /auth/wechat-login` 换取 token（**端上缓存 7 天**），此后每个请求都携带它。**不再有「公开读端点」这一态**。
- 学生端**无权限分级**（无认证态）：凭证据 openid 静默识别签发，端上**不持有、不传递 openid**。
- 运营端采用**独立管理员账号 + 账密登录**：`POST /admin/auth/login` 换取**运营端 JWT**（独立 secret 签名），随后请求带 `Authorization: Bearer <jwt>`；缺失 / 失效 → `401`。前端**不持有口令**。

### 金额

**所有金额一律以「分」为单位传输**（`number`）。分 ↔ 元转换只允许在端上 `utils/money` 进行，**禁止在页面 / 组件内裸算**。

### 时间

所有时间字段为字符串，格式 `yyyy-MM-dd HH:mm:ss`（时区 Asia/Shanghai）。端上按需格式化为展示形态，**不得改名**。

### 可空性约定

| 约定 | 说明 |
|---|---|
| 字符串字段**恒非空** | 可空列由服务端 `COALESCE(..., '')` 兜底，端上**无需判空** |
| 数组字段**恒为数组** | 无数据时返回 `[]`，**不为 null** |
| 有意可空的字段 | 逐字段在资源文档中标「可空 = 是」并说明 `null` 的语义 |

**有意可空字段（逐条登记）**：`avgReaction`（`null` = **零评分**，与「评分为 0」语义不同，端上显示「暂无评价」）。

---

## 契约同步

后端模型（**Pydantic**）是端上类型的**唯一真源**，链路如下：

```
后端 Pydantic 模型  →  FastAPI 导出 openapi.json  →  openapi-typescript 生成 api.d.ts
```

🔴 **字段命名必须 camelCase** —— Pydantic 模型须配置 alias generator（`to_camel`），与本目录各资源文档的字段名**逐字一致**；`check:contract` 即该一致性的判据。

**后端字段变更后必须刷新端上产物**（由 agent 本地启动后端执行，见 [`docs/README.md`](../README.md) 变更流程）：

```bash
cd client && npm run gen:api:fresh
```

## 撰写规范

- 每个端点固定段落：**鉴权 + 用途 → 参数 → 响应字段表 → 错误码 → 副作用 / 备注**
- **一个端点只在一份文档中出现一次**；被多处复用时在消费方文档中**引用**，不复制签名
- 字段表只写**传输形态**；对应数据库列、是否缓存列 → 见 [`schema/`](../schema/)
- 业务规则、限频阈值、覆盖语义 → 见各[功能文档](../func/client/)
