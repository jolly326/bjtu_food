# api — 接口契约

**范围**：学生端（微信小程序 `client/`）与运营端（`web/`）消费的全部端点。
**契约真源**：本文与各资源文档；端上类型由后端 **Pydantic 模型经 FastAPI 导出**（见「契约同步」）。

> 各 feature 文档只描述**业务规则**（为什么、边界在哪），端点签名与字段一律以本目录为准。

## 资源索引

### 学生端（`client/`）

| 文档 | 覆盖端点 | 消费方 |
|---|---|---|
| [client/auth.md](./client/auth.md) | `POST /auth/wechat-login` | openid 静默识别 |
| [client/dishes.md](./client/dishes.md) | `GET /dishes` · `POST /dishes/{id}/likes` | 美食地图页 |
| [client/canteens.md](./client/canteens.md) | `GET /canteens` | （本期无筛选项，端点保留备用） |
| [client/issues.md](./client/issues.md) | `GET /dish-issue-options` · `POST /dishes/{id}/issues` | 菜品纠错 |

### 运营端（`web/`）

| 文档 | 覆盖端点 |
|---|---|
| [web/auth.md](./web/auth.md) | 登录与 `/admin/**` 的 `Authorization: Bearer` 鉴权 |
| [web/canteens.md](./web/canteens.md) | `/admin/canteens*` |
| [web/stalls.md](./web/stalls.md) | `/admin/stalls*` |
| [web/dishes.md](./web/dishes.md) | `/admin/dishes*` |
| [web/issues.md](./web/issues.md) | `/admin/dish-issues*` · `/admin/dish-issue-options*` |
| [web/images.md](./web/images.md) | `POST /admin/images` |

---

## 通用约定

### 服务基路径

后端在**根路径**提供服务（**无版本段**），下文各端点的路径即**相对基址的路径**。基址由端上配置决定，两种通道：

| 端 | 通道 | 配置 |
|---|---|---|
| 小程序 · 本地开发 | 直连 `uni.request` → 本机后端 | `client/.env.development` 的 `VITE_API_BASE_URL`（`http://127.0.0.1:8000`；真机预览用个人 `client/.env.development.local` 覆盖为本机局域网 IP） |
| 小程序 · 云上 / 发布 | **云调用（推荐）** `wx.cloud.callContainer` —— 真机**免域名白名单** | `client/.env.production` 的 `VITE_TCB_ENV`（云托管环境 ID）+ 可选 `VITE_TCB_SERVICE` |
| 小程序 · 云上（回退） | 直连 `uni.request` | `client/.env.production` 的 `VITE_API_BASE_URL`（https 域名；体验版 / 正式版须已 ICP 备案并登记 request 合法域名） |
| 运营端（`web/`） | 浏览器直连 | `web/.env.*` 的 `VITE_API_BASE_URL` |

🔴 小程序端**同时配置**时以云调用为准（`client/src/utils/request.ts::USE_CLOUD`）；同一份 env 内两者都存在时，`VITE_TCB_ENV` 非空即走云调用。

**两套环境怎么切**（环境变量在**构建期内联**，改完必须重新编译，见 `client/vite.config.ts` 的显式 `define`）：

| 场景 | 命令 | Vite mode | 读到的 env |
|---|---|---|---|
| 本地开发（模拟器 / 真机调试，连本机后端） | `npm run dev:mp-weixin` | development | `.env.development`（个人差别值放 `.env.development.local`，不入库） |
| 本地编译 + 云端数据（不改动代码排查云上问题） | `npm run dev:mp-weixin:cloud` | production | `.env.production` |
| 发布构建（体验版 / 正式版） | `npm run build:mp-weixin` | production | `.env.production` |

> 开发者工具本地调试须勾选：详情 → 本地设置 → **「不校验合法域名、web-view、TLS 以及 HTTPS 证书」**，否则 `http://` 直连会被拦。
> 模拟器里 `127.0.0.1` 就是本机；**真机预览 / 真机调试时 `127.0.0.1` 指的是手机自己**，必须把地址换成电脑的局域网 IP，并放行该端口入站。
> 体验版 / 正式版不接受 http 直连，一律走 https 已备案域名或云调用（见 [`secur/S-04`](../secur/S-04-凭据与部署边界.md) §二）。

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
| **429** | **请求过于频繁**（限频，等待后可重试） | Toast 展示 `message` + **退避倒计时**（按文案里的秒数禁用入口，结束后自动恢复） |
| **4001** | **资源不存在**（含「已下架」对外等价） | 展示文案 + **给恢复路径** |
| 500 | 服务器异常 | 展示可重试失败态 |

> `4001` 是通用**细分码**：让端上**不必解析 `message` 文本**即可给出差异化引导。
> **无「邮箱未认证」码** —— 学生端不做认证（见 [`func/client/README.md`](../func/client/README.md) 的「合规与不做的事」）。

### 运维接口

| 端点 | 说明 |
|---|---|
| `GET /healthz` | 健康检查；🔴 **不走响应信封**，直接返回原始对象（探针直读）。不属业务资源，故不在上表登记。 |

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
- 运营端采用**独立管理员账号 + 账密登录**：`POST /admin/sessions` 换取**运营端 JWT**（独立 secret 签名），随后请求带 `Authorization: Bearer <jwt>`；缺失 / 失效 → `401`。前端**不持有口令**。

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

**有意可空字段（逐条登记）**：本阶段无「有意可空」展示字段 —— 点赞数 `likeCount` 恒非空（默认 0），`liked` 由登录态判定（恒非空）。

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
