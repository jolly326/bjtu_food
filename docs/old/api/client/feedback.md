# client/feedback — 意见反馈

**归属**：`FeedbackController` · `OptionDictionaryController`（类型字典）
**通用约定**见 [api/README.md](../README.md)

> 举报与菜品问题反馈**不在本文档**：举报见 [reviews.md](./reviews.md#post-reviewsidreport)，菜品问题反馈见 [dishes.md](./dishes.md#post-dishesidcorrection)。

---

## GET /feedback-options

**鉴权**：🔓 公开 ｜ **用途**：反馈类型字典

### 请求参数
无。

### 响应 `data` = `OptionVO[]`
| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | 类型 ID（提交反馈时作为 `optionId` 上送） |
| `name` | string | 否 | 类型中文名（表单直接渲染） |

> **恰好 2 个字段**，按 **`id` 升序**下发（**仅启用项**），端上**零硬编码**、不排序（展示顺序即数组顺序）。
> 值域由 `feedback_option` 字典驱动（管理端 [web/feedback-options.md](../web/feedback-options.md) 维护，新增类型免发版；种子见 [schema/feedback_option.md](../../schema/feedback_option.md)）。

---

## POST /feedback

**鉴权**：🔑 需登录 ｜ **用途**：提交意见反馈

### 请求体 `FeedbackReq`
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `optionId` | number | **是** | 反馈类型 ID；值域 = `GET /feedback-options` 下发项的 `id`，非法 / 缺少 / 已停用 → `400` |
| `content` | string | **是** | 反馈正文；端上 ≤600 字，服务端 ≤1000 字；纯空白视为未填 → `400` |
| `images` | string[] | 否 | 配图 ≤3 张；须为经 `POST /upload/cloud-image` 转存的 COS 地址 |

> 请求体**无 `type` 字符串枚举、无关联对象字段** —— 类型由字典 ID 表达，反馈不携带关联目标。

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 |
|---|---|
| 400 | `optionId` 缺失 / 非法 / 非启用项 ｜ 内容空白或超长 ｜ 配图超限或含非 COS 地址 ｜ 文本安检 `risky` |
| **429** | 限频（同 IP 或同账号超限） |

> 内容安检：`pass` / `review` 放行，`risky` 拦截且**不落库**；游客无 openid 时**跳过文本机审**放行（由本地词库兜底）。

### 限频（同 IP 与同账号**双维度**，任一超限即拒）
| 维度 | 阈值 |
|---|---|
| 每分钟 | ≤2 条 |
| 每小时 | ≤10 条 |

> 超限返回 **429**，`message` 含剩余等待秒数（端上据此退避倒计时）。

### 副作用
| 目标 | 变化 |
|---|---|
| `feedback` | INSERT（**`user_id` = 提交人（恒为真实用户 ID）**；类型 = `option_id`） |
| `moderation_task` | **同一事务**预建一行（`type='feedback'`、`status='pending'`）—— 「未处置」是显式行 |
| `notification` | 管理员处置后投递回执（**投递判据 = `user_id` 有效** —— 提交恒带登录态，故**恒投递**；投递与处置同一事务） |

### 备注
- **不收集联系方式**。
- **不要求邮箱认证**：静默登录的游客即可提交（`user_id` 恒真实，故回执同样可达）。
- 端上可缓存「类型 + 描述文本」为本地草稿；**图片不入草稿**（COS 地址重进可能失效，缓存只会导致提交时报地址非法）。
