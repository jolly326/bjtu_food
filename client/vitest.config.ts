import { defineConfig } from "vitest/config";
import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";

/**
 * Vitest 专用配置（**刻意不复用 `vite.config.ts`**）。
 *
 * <p>为何独立成文件：主配置带 `plugins: [uni()]`。实测让 vitest 读主配置会直接启动失败：
 * `Failed to resolve vue/compiler-sfc`（uni 依赖的 vue 与本项目 vue 不是同一实例），
 * 且会把小程序编译器拖进测试进程。故只按需引入标准 `@vitejs/plugin-vue`。
 *
 * <p>`@` → `src` 的 alias 必须与主配置保持一致，否则 `import '@/utils/x'` 无法解析。
 * 这里重复声明而非 import 主配置：import 即会连带执行 `uni()`。
 *
 * <p>测试统一放 `tests/`（与 `src/` 分离，对齐后端 `src/main` vs `src/test` 范式）；
 * `@` → `src` 的alias 让用例只按模块身份引用被测对象，不依赖与源码的相对位置。
 *
 * <p>**测试环境**：统一 `node`（纯逻辑：utils / composables / stores / 纯 TS 模块），零 DOM 成本。
 * 如需组件渲染测试，再按需引入 DOM 环境依赖并在文件顶部标注 `// @vitest-environment`。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
  },
});