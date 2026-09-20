# 写评价（A-06）

> 所属端：**学生端（微信小程序）** ｜ 鉴权：**🔐 认证**（需学号邮箱认证）
> 返回：[功能总览](./README.md)

## 干什么

给一道菜打星 + 写文字 + 最多 3 张配图。**先发后审**：文本与图片通过微信内容安全检测即**立即公开**，无人工复核；后续如被举报，由管理员事后处置（见 B-03）。

## UI

- 入口：菜品详情评价区底栏「写评价」按钮。
- 弹层：`ReviewComposer` 底部弹层（统一下拉关闭手势，阈值约 120px），内容为：星级选择 + 文字输入框 + 配图选择器（`ImagePicker`，自动压缩 ≤1MB、≤750×1334）。
- 未认证用户点入口 → 弹 `AuthSheet` 引导认证（入口不置灰）。

## 操作

1. 点「写评价」→ 弹层展开。
2. 选星级（1~5，必填）→ 输文字（可跳过）→ 加图（可选 ≤3 张，每张经压缩后上传）。
3. 点提交 → 端上先逐张上传配图取 COS URL → 再提交评价。
4. 结果：**检测不通过 → 提示错误、不发布**；通过 → 弹层关闭，评价立即出现在评价区顶部。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/upload/images` | 需登录（游客亦可） | **前置调用**：配图逐张转存（微信内容安检 + 转存 COS），见 B-06 |
| POST | `/dishes/{id}/reviews` | **🔐 认证**（`@RequireVerified`；未认证 → 4031） | 提交评价（**菜品归属在路径**）。每人对同一菜品仅一条评价；`risky` 内容 → 400。**2026-09-20 §7.30 RESTful 化** |

## 字段

### 请求 · `POST /dishes/{id}/reviews`（`ReviewReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 被评价的菜品 ID（**路径参数**，body 不再收 `dishId`） |
| `rating` | number | **是** | 评分，**1~5 星**（超出范围 → 400） |
| `content` | string | 否 | 文字评价，最长 **500 字**（可只打星不写文字） |
| `images` | string[] | 否 | 评价配图 URL 数组，**最多 3 张**；元素须为经 `POST /upload/images` 转存后的 COS 绝对地址 |

### 响应

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200`（失败按错误码提示 `message`） |

### 前置调用字段 · `POST /upload/images`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `fileId` | string | **是** | 微信云存储文件标识（`wx.cloud.uploadFile` 返回的 `cloud://...`） |
| → 响应 `url` | string | — | 安检通过并转存后的 **COS 绝对 URL**（收集后随 `images` 提交） |

### 内容安全（提交闸门，不落库）

| 环节 | 判定 | 中文解释 |
|---|---|---|
| 文本 `msgSecCheck` v2（`scene=2`） | `pass` / `review` → **放行**；`risky`（含未知/缺失态）→ **400 拦截、不落库** | 机检即终局，**无人工复核** |
| 图片 `imgSecCheck` | 违规（微信码 `87014`）→ 400 | 单张失败只跳过该张，不中断其余图片上传 |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 参数/业务校验失败 | 含内容安检违规（提示违规文案）、配图超 3 张、评分越界等 |
| **4031** | 邮箱未认证 | 区别于 403：前端据此弹 `AuthSheet` 认证引导 |
| 400 | 一人一菜一评 | 由唯一索引 `uk_review_user_dish` 保证；改评 = 先删再写 |

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `review` | INSERT | 写入 `user_id`（当前登录用户，**禁止信任前端**）、`dish_id`、`rating`、`content`、`images`（JSON 数组，COS URL） |
| `dish.avg_rating` / `dish.rating_count` | **异步** UPDATE | 通过 `ReviewSubmittedEvent` → `RatingUpdateListener`（`@Async` AFTER_COMMIT）重算；**口径：仅计 `is_hidden=0` 的评价** |

## 答疑

### Q：评价先发不用审核，微信内容安全检测通过直接发布即可，之后有人反馈举报再处理

**A：与现状完全一致，无需改动。**
代码已是你要的语义：提交时文本过 `msgSecCheck` v2（`scene=2`）、图片过 `imgSecCheck`；**`pass` 与 `review` 一律放行落库，仅 `risky`（含未知/缺失态 fail-closed）返回 400 拦截**；**无人工复核、无 `sec_state` 落库**；管理端只保留事后处置（B-03）；用户举报走反馈 `report` 类型（A-09）。

**一处已核实、请放心**：管理员隐藏/删除评价后会触发评分重算——`ReviewServiceImpl.setHidden()` 与 `deleteByAdmin()` 均会 `publishEvent(new ReviewSubmittedEvent(...))`，因此聚合口径「仅计 `is_hidden=0`」成立，`dish.avg_rating/rating_count` 不会残留脏值。
