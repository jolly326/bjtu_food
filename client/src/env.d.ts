/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基础地址，如 http://127.0.0.1:8000 */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
