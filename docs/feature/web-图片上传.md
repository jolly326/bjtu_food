# 图片上传（表单内）（B-06）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`，`/admin/upload/image` 受口令守卫）
> 返回：[功能总览](./README.md)

## 介绍

提供**菜品图 / 头像**的上传能力，共两条**互不兼容**的链路：

| 链路 | 端点 | 用途 | 是否过内容安检 |
|---|---|---|---|
| UGC 配图链路 | `POST /upload/cloud-image` | 评价 / 反馈配图（小程序端主链路） | **是**（`imgSecCheck`） |
| multipart 直传链路 | `POST /admin/upload/image` | Web 后台菜品图 | 否 |

流程：表单内点选图 → 自动压缩 → 调上传接口取回 URL → 回填到表单字段（菜品 `images[]` / 头像 `avatar`）随表单保存；失败（类型 / 大小 / 安检不通过）→ 提示 `message`，表单不提交。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-图片上传.md（docs/ui）](../ui/web-图片上传.md)（**UI 口径以该文件为唯一真源**）

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/upload/cloud-image` | 需登录（游客亦可） | **UGC 配图主链路**：云存储 fileId → 安检 → 转存 COS，返回 COS URL。**单张调用**（多图由前端逐张调） |
| POST | `/admin/upload/image` | 🔑 口令守卫 | multipart 直传（Web 菜品图） |

## 字段

### 请求 · `POST /upload/cloud-image`（`CloudImageUploadReq`，JSON）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `fileId` | string | **是** | 微信云存储文件标识（`wx.cloud.uploadFile` 返回，形如 `cloud://env.bucket/path`）；后端据此从云开发拉取原图 |

### 响应 · `POST /upload/cloud-image`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `url` | string | 该张图片**转存后的 COS 永久 URL**（端上收集后随评价 / 反馈的 `images` 提交，或写入头像字段） |

**单张校验**：`cloud://` 前缀属本小程序云环境 + 扩展名白名单（jpg/png/webp）+ 大小 ≤1MB（`imgSecCheck` 硬限制）+ `imgSecCheck` 通过才转存；任一不通过 → 该张 400（违规 / 文件获取失败 / 存储未配置），**不影响其他张**。
**≤3 张总量约束不在本接口**：由评价 / 反馈提交载荷（`ReviewReq.images` / 反馈 `images`）兜底校验。

### 请求 · `POST /admin/upload/image`（multipart/form-data）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `file` | file | **是** | 图片文件，支持 jpg / jpeg / png / webp（字段名必须为 `file`） |

### 响应 · `POST /admin/upload/image`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `url` | string | 完整可访问 URL（展示用） |
| `relativeUrl` | string | **落库用地址**：本地存储链路为相对路径（如 `/uploads/202609/xxx.jpg`）；COS 配置时与 `url` 同为绝对 URL |

> ⚠️ **Web 端约定**：上传结果必须同时含 `url` 与 `relativeUrl`，否则前端报「上传接口返回缺少图片地址」。落库统一用 `relativeUrl`，展示时再拼前缀。

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 400 | 文件类型/大小不合法 | 非白名单格式或超限 |
| 400 | 图片包含违规内容，无法上传 | `imgSecCheck` 判定违规（微信码 `87014`） |
| 400 | 图片存储未配置 | COS 未配置（UGC 链路不走本地降级） |
| 403 | 口令缺失或错误 | `/admin/upload/image` 受 `AdminTokenFilter` 守卫 |

## 数据（落库）

| 表 / 列 | 变化 | 中文解释 |
|---|---|---|
| `dish.images` | 写入 URL 数组 | Web 菜品图（用 `relativeUrl`） |
| `user.avatar` | 写入 URL | 头像地址（仅站内路径或 `cloud://`） |
| `review.images` / `user_feedback.images` | 写入 COS URL 数组 | UGC 配图（≤3 张，直接存 COS 绝对地址） |

> **无独立上传表**：本功能不产生业务数据，只产出可落库的图片地址。

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**无（文档与代码一致）**
