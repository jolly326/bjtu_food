/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基址。缺省 `/api`（同源反代）；可设为完整域名直连后端 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
