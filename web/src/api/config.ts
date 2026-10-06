/// <reference types="vite/client" />

/**
 * 后端 API 基础路径
 *
 * 读取优先级（从高到低）：
 * 1. 环境变量 VITE_API_BASE_URL（可在 .env.development / .env.production 或命令行注入）
 * 2. 下方 DEFAULT_API_BASE_URL（本地联调默认 127.0.0.1:8080）
 *
 * ⚠️ base **必须含版本段 /api/v1**，与后端 application.yml 的
 *    `server.servlet.context-path = /api/v1` 一致；缺版本段 → 全站 404。
 *
 * 用法示例：
 * - 本地联调后端：VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
 * - 部署上线：VITE_API_BASE_URL=https://<你的域名>/api/v1
 */
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL

/**
 * 🔴 本模块**只放构建期配置**，不放任何凭证。
 *
 * <p>「管理端口令 `ADMIN_TOKEN`（`VITE_ADMIN_TOKEN`）」由 TD-23 删除 —— 口令一旦被
 * 构建期注入，就会被 Vite 硬编码进 bundle，产物离开本机即永久失守且无法吊销。
 * 现口径（真源 [C1](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)）：
 * **前端不持有口令**，只持有登录后签发的 JWT（见 `api/session.ts`）。
 */
