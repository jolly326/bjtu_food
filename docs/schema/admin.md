# admin — 运营端账号

**用途**：运营端（管理后台）登录账号。与学生的 `user` 体系**完全隔离**。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 账号 ID |
| `username` | VARCHAR(32) | NO | 无 | UK | 登录名 |
| `password` | VARCHAR(100) | NO | 无 | | 口令 **BCrypt 哈希**（不存明文、不可逆） |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | — |
| `uk_admin_username` | `username` | **唯一** | 登录按名取行 |

## 关键设计

- **与 `user` 无任何关系** —— 两套身份体系不交叉；运营端凭证不进学生端契约。
- 口令只存 **BCrypt 哈希**；比对在服务端完成。
- 单管理员运营场景：账号行由数据库维护，无自助注册。

## 关联

| 方向 | 目标表 | 关系 | 级联 |
|---|---|---|---|
| — | — | 本表**不被任何业务表引用**（后台只做录入与查看，不留操作痕迹） | — |

## 注意事项

- 库内**不设操作日志 / 审计表** —— 单管理员运营，写操作可按业务表自身的 `updated_at` 定位。
- 🔴 **改密没有页面入口**（见 [`func/web/C1`](../func/web/C-账号与访问/C1-管理员登录.md)）—— 唯一做法是**直连数据库**更新 `password`：
  1. 离线用 **BCrypt** 生成哈希（如 `python -c "import bcrypt;print(bcrypt.hashpw(b'新口令', bcrypt.gensalt()).decode())"`）；
  2. `UPDATE admin SET password = '<哈希>' WHERE username = '<账号>';`
  - 🔴 **严禁写入明文** —— 比对走 BCrypt，明文存进去将**永远无法登录**。
- **无吊销通道**：改密**不影响已签发 token**，其最长可用至 7 天自然过期。
