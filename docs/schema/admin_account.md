# admin_account — 管理员账号

**用途**：管理后台（Web）的**独立管理员账号** —— 账密登录（JWT），与 `user`（学生）**完全隔离**。
**对应实体**：`com.bjtufood.auth.entity.AdminAccount`。设计真源见 [C1 管理员登录与访问控制](../func/web/C-账号与访问/C1-管理员登录与访问控制.md)。

> **不复用 `user`**：`user` 表为学生专用（微信 openid / 学号邮箱认证链路），管理端账号无这些属性；表内**零学生字段**。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 账号 ID |
| `username` | VARCHAR(64) | NO | — | **UNI** | 登录名（唯一） |
| `password_hash` | VARCHAR(100) | NO | — | | **BCrypt** 密码哈希（不可逆；明文不落库、不入文档与仓库） |
| `status` | VARCHAR(8) | NO | `'on'` | | `on` 启用 / `off` 停用 |
| `role` | VARCHAR(16) | NO | `'super'` | | 角色：`super` 全权 / `operator` 读写（**不含删除**）/ `viewer` 只读 |
| `last_login_at` | DATETIME | YES | NULL | | 最近登录时间（登录成功时更新） |
| `credential_version` | INT | NO | `1` | | 凭证版本；**改密即自增** ⇒ 既有 token 全部失效 |
| `password_changed_at` | DATETIME | YES | NULL | | 最近一次改密时刻；NULL = 从未改密（超期提示按建号时间起算） |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | 创建时间 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间 |

## 索引

| 索引名 | 列 | 类型 |
|---|---|---|
| `PRIMARY` | `id` | 主键 |
| `uk_admin_username` | `username` | **唯一** |

## 关键设计

### 独立于学生 JWT 体系
管理端登录签发**独立 secret** 的 JWT（`admin.jwt.secret` ≠ `spring.jwt.secret`）⇒ 学生 token **无法冒充**管理端。

### 密码只存哈希
仅存 `BCryptPasswordEncoder` 生成的哈希；**初始密码经环境变量注入或直连库登记，绝不写入文档 / 仓库**。

### 角色边界（`role`）
三档 —— `super` 全权（含删除类不可逆操作）/ `operator` 读写（**不含删除**）/ `viewer` 只读。由 `AdminAuthFilter` 按「HTTP 方法 + 角色」**服务端强制**判定，`403` 拒绝越权；**未知 / 空值一律按 `viewer` 处理**（fail-closed）。角色由直连库维护（库内仅 2~3 行账号），**无管理入口** —— 引入入口会新增「谁能改角色」的自指权限问题。

### 凭证版本### 凭证版本（`credential_version`）
token 内嵌签发时的版本快照，`AdminAuthFilter` **逐请求**与库中现值比对，不一致即 `401`。
**改密即自增** ⇒ 所有既有 token 立即失效（含被盗用中的那一份），无需另建吊销表 —— 复用了「逐请求回查账号状态」这条既有路径。

## 关联

无外键；与 `user`（学生）**无任何关系**。

## 注意事项

- 表已建；**初始账号已直连云端库登记**（用户名与密码**均不写入本文件**，密码仅以 BCrypt 哈希存储于库）。
- **禁止**在本文件或任何文档 / 代码 / commit 中出现明文密码。
