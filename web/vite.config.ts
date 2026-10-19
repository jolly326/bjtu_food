import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // WEB-116：Element Plus 组件与函数式 API 自动导入。
    // 基础样式与主题变量统一由 main.ts 引入 `element-plus/dist/index.css`
    // （原生 HTML + var(--el-*) 的页面依赖它拿到完整变量，否则弹窗/输入框会透明）。
    // 🔴 importStyle: false —— 禁止按需注入组件样式：按需样式链（base）自带
    // `:root { --el-color-primary: #409eff }` 默认变量，会在懒加载时晚于 theme.css
    // 注入 <head>，把暖橙主题覆盖回 Element Plus 默认蓝（主题变蓝根因）。
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
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
