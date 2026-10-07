import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

/**
 * 管理端 Vitest 配置（与学生端 `client/vitest.config.ts` 同口径）。
 *
 * <p>`@` → `src` 的 alias 必须与主 `vite.config.ts` 保持一致，否则 `import '@/utils/x'` 无法解析。
 * 这里重复声明而非 import 主配置：主配置带 devtools / auto-import 等插件，测试进程不需要。
 *
 * <p>**测试统一放 `tests/`**（与 `src/` 分离，对齐后端 `src/main` vs `src/test` 范式）；
 * 用例内一律用 `@/` 别名引用被测模块，不写与源码的相对路径。
 *
 * <p>**默认 node 环境、不挂 vue 插件**：当前覆盖的是纯逻辑与「以 DOM 为载体的状态机」，
 * 后者在用例内以最小 `window` / `document` 存根承载（见 `tests/support/dom-stub.ts`）。
 * ⚠️ 不挂 `@vitejs/plugin-vue` 除省开销外还有一个硬原因：vitest 自带一份嵌套 vite，
 * 与本仓 vite 的插件类型（rolldown / rollup 两套 `Plugin` 声明）不兼容，挂上会让 `vue-tsc` 报
 * `Plugin<Api> is not assignable to PluginOption`。
 * 将来要做组件渲染测试时，需先统一 vite 版本，再按文件头 `// @vitest-environment happy-dom` 切换。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.test.ts'],
  },
})