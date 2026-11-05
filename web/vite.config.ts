import { fileURLToPath, URL } from 'node:url'

import { defineConfig, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

/** 是否生产构建（`vite build` ⇒ NODE_ENV=production） */
const isProduction = process.env.NODE_ENV === 'production'

/** 后端实例地址：默认本机 8000，可用 `VITE_API_PROXY_TARGET` 指向其它实例 */
const proxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8000'

/** dev 与 preview 共用：浏览器始终同源请求 `/api`，由服务端转发，无跨域 */
const apiProxy: Record<string, ProxyOptions> = {
  '/api': {
    target: proxyTarget,
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, ''),
    configure(proxy) {
      proxy.on('error', (err) => {
        console.error(`[api proxy] 后端 ${proxyTarget} 不可达：${err.message}`)
      })
    },
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // 生产构建剔除 Vue DevTools：仅开发期启用，避免其注入进生产 bundle
    ...(isProduction ? [] : [vueDevTools()]),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: apiProxy,
  },
  preview: {
    port: 4173,
    proxy: apiProxy,
  },
  build: {
    // 生产：源码映射无用途，显式关闭（默认即 false，此处显式声明口径）
    sourcemap: false,
    rollupOptions: {
      output: {
        // 分包（长缓存命中）：vue 生态 / Element Plus 各自独立 chunk，应用代码按路由
        // 懒加载（router 内 `() => import()`）；AppIcon 图标 chunk 随「零消费即删」保持精简。
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          if (/node_modules[\\/]element-plus[\\/]/.test(id)) return 'vendor-element-plus'
          if (/node_modules[\\/](@vue|vue|vue-router|pinia)[\\/]/.test(id)) return 'vendor-vue'
          return undefined
        },
      },
    },
  },
})
