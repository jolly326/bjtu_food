# client/feedback — 意见反馈

**归属**：`FeedbackController`
**通用约定**见 [api/README.md](../README.md)

> 举报与菜品问题反馈**不在本文档**：举报见 [reviews.md](./reviews.md#post-reviewsidreport)，菜品问题反馈见 [dishes.md](./dishes.md#post-dishesidcorrection)。

---

## POST /feedback

**鉴权**：🔑 需登录 ｜ **用途**：提交意见反馈

### 请求体 `FeedbackReq`
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `type` | string | **是** | 三选一：`bug` 程序功能Bug / `suggestion` 产品功能建议 / `other` 其他相关问题；非法值 → 400 |
| `content` | string | **是** | 反馈正文；端上 ≤600 字，服务端 ≤1000 字；纯空白视为未填 → 400 |
| `images` | string[] | 否 | 配图 ≤3 张；须为经 `POST /upload/cloud-image` 转存的 COS 地址 |

> 请求体**无 `sub`、无关联对象字段** —— 反馈不携带关联目标。
> **写入类型白名单仅上述三类**：非白名单类型（`issue` / `add` / `error` / `report`）→ 400，不静默落库。

### 响应 `data`
`null`（成功即 `code=200`）

### 错误码
| code | 条件 |
|---|---|
| 400 | 类型非法 / 内容空白或超长 / 配图超限或含非 COS 地址 / 文本安检 `risky` / IP 限频 |

> 内容安检：`pass` / `review` 放行，`risky` 拦截且**不落库**；游客无 openid 时**跳过文本机审**放行（由本地词库兜底）。

### 限频（同 IP）
| 维度 | 阈值 |
|---|---|
| 每分钟 | ≤2 条 |
| 每小时 | ≤10 条 |

> 超限返回 400，`message` 含剩余等待秒数。

### 副作用
| 目标 | 变化 |
|---|---|
| `user_feedback` | INSERT（`status='pending'`；**匿名提交 `user_id = 0`**，后台显示为游客） |
| `notification` | 管理员处理后异步 INSERT 回执（**投递判据 = `user_id > 0`** —— 静默登录的游客**有 userId，同样收到**；**匿名提交（`user_id = 0`）不投递**） |

### 备注
- **不收集联系方式**。
- **不要求邮箱认证** —— 反馈主路径刻意匿名。
- 端上可缓存「类型 + 描述文本」为本地草稿；**图片不入草稿**（COS 地址重进可能失效，缓存只会导致提交时报地址非法）。
