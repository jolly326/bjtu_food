/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 直连通道：后端接口基础地址，如 http://127.0.0.1:8000 / https://<云托管域名> */
  readonly VITE_API_BASE_URL: string
  /** 云调用通道：云托管环境 ID；非空即以云调用为准（优先于 VITE_API_BASE_URL） */
  readonly VITE_TCB_ENV: string
  /** 云调用通道：服务名；留空用环境默认服务 */
  readonly VITE_TCB_SERVICE: string
  /** 地图磁贴是否追加 COS 缩略图处理参数（'true' 开启） */
  readonly VITE_USE_COS_THUMB: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
