/// <reference types="vite/client" />

// 构建期由 vite.config.ts 从 manifest.json 的 versionName 注入（小程序运行时读不到 manifest）
declare const __APP_VERSION__: string;

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}
