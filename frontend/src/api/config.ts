/// <reference types="vite/client" />

/**
 * 后端 API 基础路径配置
 *
 * 读取优先级（从高到低）：
 * 1. 环境变量 VITE_API_BASE_URL（可在 .env.development / .env.production 或命令行注入）
 * 2. 下方 DEFAULT_API_BASE_URL（本地联调默认 127.0.0.1:8080）
 *
 * 用法示例：
 * - 真机预览（手机与电脑同一 WiFi）：VITE_API_BASE_URL=http://<电脑局域网IP>:8080/api
 * - 部署上线：VITE_API_BASE_URL=https://<你的域名>/api
 */
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api'

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL