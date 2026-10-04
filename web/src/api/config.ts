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

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL

/**
 * 管理端口令（**构建期注入**，请求头 `X-Admin-Token`）。
 *
 * <p>口径真源：[C1 管理员登录与访问控制](../../../docs/api/web/auth.md) ——
 * 管理后台**无登录体系**：口令来自构建期环境变量 `VITE_ADMIN_TOKEN`，与后端 `ADMIN_TOKEN` 一致即可通；
 * 不做运行时输入、不做本地持久化（避免把口令写进浏览器存储）。
 *
 * <p>未配置时为空串 ⇒ 后端 `AdminTokenFilter` fail-closed 返回 **403**，前端展示「会话失效态」。
 */
export const ADMIN_TOKEN: string = import.meta.env.VITE_ADMIN_TOKEN || ''
