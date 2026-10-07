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
 * <p>**两档环境**（用 `// @vitest-environment` 逐文件切换，默认 node）：
 * <ul>
 *   <li><b>node</b>（默认）：纯逻辑（utils / composables / stores / 纯 TS 模块），零 DOM 成本；</li>
 *   <li><b>happy-dom</b>：组件渲染测试。**仅在文件顶部标注切换**，避免把全部单测拖进
 *       DOM 环境（实测慢一个数量级）。小程序标签（`view` / `text` / `image`）在 happy-dom 下
 *       会被当作未知自定义元素正常渲染与挂载，无需额外 polyfill。</li>
 * </ul>
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    // 默认 node：组件测试文件自行用 `// @vitest-environment happy-dom` 覆盖
    environment: "node",
    include: ["tests/**/*.test.ts"],
  },
});