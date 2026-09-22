# 意见反馈（A-11）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔓 公开**（免认证；游客可提交）
> 返回：[功能总览](./README.md)

## 干什么

学生对菜品与产品的**一切诉求入口**，共三类：

| 类型 | 端上名称 | 含义 |
|---|---|---|
| `suggestion` | 提个想法 | 功能建议 / 问题反馈（二级：`idea` 想法、`problem` 问题） |
| `add` | 推荐菜品 | 投稿：推荐一道菜（菜名 / 价格 / 位置），**管理员处理后才会展示** |
| `error` | 信息不对 | 纠错 / 申请下架（关联到具体菜品） |

> **举报（`report`）不在本页**，它从评价卡三点菜单进入（见 [举报评价](./client-举报评价.md)）。三者写同一张 `user_feedback` 表、同一个后台处理页、同一套处置动作——**已经是同一个板块，不需要再融合**。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-意见反馈.md（docs/ui）](../ui/client-意见反馈.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 选类型 → 切换即清空已填内容（避免串类型提交）。
2. 填结构化字段 → 可加配图（≤3 张，自动压缩）。
3. 点提交 → 门禁校验通过才可提交；成功后 Toast + 自动返回。
4. 投稿类（`add`/`error`）表单与提交区会明示「提交后由管理员处理，确认后才会展示」。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/feedback` | 🔓 公开 | 提交反馈（三类共用） |
| GET | `/canteens?include=stalls` | 🔓 公开 | 食堂含档口树，供「推荐菜品」的位置两级联动（端上只读食堂与档口的 `id` / `name`）。**2026-09-21 决议：原 `GET /canteens/all` 已删除并合入 `GET /canteens`，端上仅改调用方式**（`get('/canteens', { include: 'stalls' })`，映射逻辑不变） |

## 字段

### 请求 · `POST /feedback`（`FeedbackReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `type` | string | **是** | 反馈类型。可写值域 **`suggestion` / `add` / `error` / `report`**；非法值 → 400（`bug`/`other` 为历史遗留枚举，**禁止新增**） |
| `sub` | string | 否 | **二级分类，仅 `type=suggestion` 时有效**：`idea`=想法 / `problem`=问题（「系统 bug」归 `problem`，不升一级类型） |
| `content` | string | **是** | 反馈内容，最长 **1000 字**（纯空白视为未填 → 400）。三类均把结构化字段拼进本字段提交（如菜名/价格/位置/纠错项与正确值） |
| `images` | string[] | 否 | 反馈配图 URL 数组，**≤3 张**（须先经 `POST /upload/images` 转存为 COS 地址） |
| `relatedType` | string | 否 | 关联对象类型：纠错 = `dish`（菜品）；其他可空 |
| `relatedId` | number | 否 | 关联对象 ID：纠错 = 菜品 ID；其他可空 |

> **不收集联系方式**（`contact` 列已删）；游客提交 `user_id` 为 null，后台显示为游客。

### 校验规则（严格模式，一律不静默降级）

| 规则 | 结果 | 中文解释 |
|---|---|---|
| `type` 不在白名单 | 400 | 仅 `suggestion`/`add`/`error`/`report` 可写 |
| `sub` 非空且 `type≠suggestion` | 400 | 二级类型只属于「提个想法」 |
| `sub` 非空但不在 `idea`/`problem` | 400 | 值域校验 |
| `content` 空白或 >1000 字 | 400 | 内容必填 |
| `images` >3 张 | 400 | 配图上限 |
| 同 IP >2 条/分钟 或 >10 条/小时 | 400 | 限频，提示剩余秒数 |
| 文本安检 `risky`（含未知/缺失态） | 400 | 拦截、不落库；`pass`/`review` 放行 |

### 响应 · `POST /feedback`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 响应 · `GET /canteens?include=stalls`（`List<CanteenWithStallsVO>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 食堂 ID（**端上位置选择器消费**） |
| `name` | string | 食堂名称（**端上消费**） |
| `stalls` | object[] | 下属**档口列表**；每项仅 `id`、`name`（端上只读 `name` 做二级联动） |

> **已删除出参（2026-09-21 决议）**：食堂层 `location` / `description` / `images`（全端零消费，端上不透传）；档口层 `location` / `floor` / `windowNo` / `description` / `images` / `avgRating`（端上只读 `id` / `name`）→ 一并收敛，并连带删除 `CanteenServiceImpl.listWithStalls()` 的 `batchAvgRating` 批查（白算）。
> ⚠️ **代码同步状态**：字段仍在 `CanteenWithStallsVO` / `StallDetailVO` 中，随契约变更落地。

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `user_feedback` | INSERT | `user_id`（游客 null）、`type`、`sub`、`content`、`images`、`related_type`、`related_id`、`status='pending'` |
| `notification` | 管理员处理后异步 INSERT | 已认证提交人收 `feedback_handle` 回执；游客不投递（提交页文案已明示「无法单独通知你」） |
