# 意见反馈（A-11）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免认证；游客可提交）
> 返回：[功能总览](./README.md)

## 介绍

学生诉求提交入口，页面顶部**分段控件切换双模式**，两类提交走**两个独立端点、落两张独立表**：

| 模式 | 分段文案 | 端点 | 提交内容 |
|---|---|---|---|
| 我要反馈问题 | 反馈问题 | `POST /feedback` | 文字内容（≤1000 字）+ 配图 ≤3 张 |
| 我要更新信息 | 更新信息 | `POST /dishes/{id}/correction` | 选定菜品 → 七字段结构化表单（名称 / 价格 / 食堂名 / 档口名 / 口味标签 / 食材 / 图片），**无文字说明字段** |

入口：

- 「我的」页宫格「意见反馈」→ 缺省 `issue` 模式；
- 菜品详情页信息卡「信息有误？」→ `update` 模式并携带当前菜品 ID，进页即预选菜品、跳过搜索。

流程：`issue` 填内容即提交；`update` 搜索选菜（底部弹层）→ 详情预填 → 用户只改差异项 → 提交，由管理员核实后更新菜品信息（页面文案不暗示提交即生效）。提交门禁 `canSubmit`，置灰点击 toast 缺失项；成功 Toast「已提交，感谢反馈」+ 自动返回；两模式切换互不清空、各自保留草稿。处理结果经站内通知回执（仅已认证提交人）。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-意见反馈.md（docs/ui）](../ui/client-意见反馈.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/feedback` | 🔓 公开 | 提交问题反馈（`type=issue`）；公开匿名，IP 限频 |
| POST | `/dishes/{id}/correction` | 🔓 公开 | 提交菜品信息纠错（菜品子资源，`dishId` 在路径）；公开匿名，IP 限频 2 条/分钟、10 条/小时 |
| GET | `/dishes/{id}` | 🔓 公开 | 菜品详情（`update` 模式预填数据源；字段口径见 [client-菜品详情](./client-菜品详情.md)） |

## 字段

### 请求 · `POST /feedback`（`FeedbackReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `type` | string | **是** | 模式类型，写入口径 **`issue`**（写入白名单，非法值 → 400）；`report` 经同一端点提交，归属[举报评价（A-09）](./client-举报评价.md)，不在本页范围内 |
| `content` | string | **是** | 反馈正文，最长 **1000 字**（纯空白视为未填 → 400）；服务端敏感词过滤，命中词替换后入库 |
| `images` | string[] | 否 | 配图 URL 数组，**≤3 张**（经 `POST /upload/cloud-image` 安检转存的 COS 地址） |

### 请求 · `POST /dishes/{id}/correction`（`CorrectionReq`，七字段平铺）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `name` | string | **是** | 菜品名称，非空 ≤**64 字**；服务端敏感词过滤（命中 → 400） |
| `price` | number | **是** | 现价，**单位「分」**、整数 >0（端上以元填写，提交前经 `utils/money` 的 `yuanToFen` 转分） |
| `canteenName` | string | **是** | 食堂名称（文本），非空 ≤**64 字** |
| `stallName` | string | **是** | 档口名称（文本），非空 ≤**64 字** |
| `flavorTags` | string[] | 否 | 口味标签机器值数组（端上预填详情值 + 用户自由输入项） |
| `ingredients` | string[] | 否 | 食材机器值数组 |
| `images` | string[] | 否 | 菜品图片 URL 数组（COS 绝对地址，**≤9 张**，经 `POST /upload/cloud-image` 安检转存；与 `issue` 配图 ≤3 张口径区分） |

> `dishId` 为路径参数，不在请求体；表单提交即全部内容（无文字说明字段）。

### 校验规则（严格模式，一律不静默降级）

**`POST /feedback`（`issue`）**：

| 规则 | 结果 | 中文解释 |
|---|---|---|
| `content` 空白或 >1000 字 | 400 | 内容必填 |
| `images` >3 张或含非 COS 地址 | 400 | 配图上限与白名单 |
| 同 IP >2 条/分钟 或 >10 条/小时 | 400 | 限频，提示剩余秒数 |
| 文本安检 `risky`（含未知 / 缺失态） | 400 | 拦截、不落库；`pass`/`review` 放行（游客无 openid 时跳过文本安检放行） |

**`POST /dishes/{id}/correction`（`update`）**：

| 规则 | 结果 | 中文解释 |
|---|---|---|
| 菜品不存在或已下架 | **4001** | 资源不存在码 |
| `name` / `canteenName` / `stallName` 空或 >64 字 | 400 | 文本字段必填与长度 |
| `name` 含敏感词 | 400 | 名称校验 |
| `price` 空 / ≤0 | 400 | 价格必须为 >0 整数（分） |
| `images` >9 张或含非 COS 地址 | 400 | 快照图片上限与白名单 |
| 同 IP >2 条/分钟 或 >10 条/小时 | 400 | 限频，提示剩余秒数 |

### 响应 · 两个提交端点

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

> **不收集联系方式**；游客提交 `user_id` 为 null，后台显示为游客。

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `user_feedback` | INSERT（`issue`） | `type='issue'`、`content`、`images`（JSON 串）、`status='pending'` |
| `dish_correction` | INSERT（`update`） | 七字段拆列快照（`name` / `price`（分）/ `canteen_name` / `stall_name` / `flavor_tags` JSON / `ingredients` JSON / `images` JSON）+ `dish_id` + `user_id`（游客 null，匿名提交）+ `status='pending'` |
| `dish` | 读 | `GET /dishes/{id}` 详情预填表单（出参含 `canteenName` / `stallName` 名称文本；字段口径见 [client-菜品详情](./client-菜品详情.md)） |
| `dish` | 写 | 仅管理端采纳纠错时由服务端七字段写回；学生端提交纠错不直接改动菜品 |
| `notification` | 管理员处理后异步 INSERT | 已认证提交人收 `correction_handle`（标题「菜品信息更新」）回执；游客不投递（提交页文案已明示「无法单独通知你」） |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
