# user — 学生

**用途**：学生身份 —— 由微信 openid 静默识别，用于归属点赞与纠错，**不在任何界面展示**。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 用户 ID |
| `openid` | VARCHAR(64) | NO | 无 | UK | 微信 openid（**唯一身份凭据**） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 首次识别时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `uk_user_openid` | `openid` | **唯一** | 静默识别时按 openid 建 / 取行 |

## 关键设计

### 无账号体系

- **不做**注册 / 登录 / 认证 / 注销 / 密码 —— 产品是工具，用户无感使用。
- 冷启动 `wx.login` 静默换取 openid ⇒ 首次 `INSERT`，此后 `SELECT`（见 [`func/client/C1`](../func/client/C-用户身份/C1-openid静默识别.md)）。
- **不存**昵称 / 头像 / 邮箱 / 手机号 / 状态 —— 无消费方；亦**不提供封禁能力**。

### openid 的定位

- `openid` **属个人信息** —— 须与微信 MP 后台的《用户隐私保护指引》声明保持一致（见 [`func/client/README.md` 合规与不做的事](../func/client/README.md)）。
- 仅供服务端归属数据与频控使用；**端上不持有、不传递**。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| 1→n | `dish_rating.user_id` | 该用户的点赞记录 | 应用层归属（见 [dish_rating.md](./dish_rating.md)） |

## 注意事项

- 库表**不含**任何凭据类列（口令 / token），凭据只存在请求头。
