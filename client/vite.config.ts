import { defineConfig, loadEnv } from "vite";
import uni from "@dcloudio/vite-plugin-uni";
import { fileURLToPath, URL } from "node:url";

// https://vitejs.dev/config/

/** 项目根（`client/`）—— uni 会把 Vite root 指到 `src/`，故环境与别名都显式锚定此处 */
const ROOT = fileURLToPath(new URL("./", import.meta.url));

/**
 * 端上用到的环境变量（构建期内联，两端 separates：见 docs/api/README.md「服务基路径」）。
 * 逐个列出是刻意的：uni 的 dev（watch）管线不替换 `import.meta.env` 的成员取值，
 * 统一在 Vite 层显式 define ⇒ **开发构建与发布构建拿到同一份值时机制一致**。
 */
const ENV_KEYS = [
  "VITE_API_BASE_URL",
  "VITE_TCB_ENV",
  "VITE_TCB_SERVICE",
  "VITE_USE_COS_THUMB",
] as const;

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ROOT, "VITE_");
  const define = Object.fromEntries(
    ENV_KEYS.map((key) => [`import.meta.env.${key}`, JSON.stringify(env[key] ?? "")]),
  );

  return {
    plugins: [uni()],
    envDir: ROOT,
    define,
    build: {
      // 小程序生产构建：源码映射在真机无用途，显式关闭省体积；
      // 压缩 / 组件懒加载 / 分包优化由 manifest.json（minified / lazyCodeLoading /
      // optimization.subPackages）与 uni 管线负责，此处不重复覆盖 minify / target。
      sourcemap: false,
      // 放宽 Rollup 体积告警阈值：mp-weixin 产物含 uni 运行时，单块天然偏大，属正常
      chunkSizeWarningLimit: 2000,
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 屏蔽 sass 1.80+ 对 legacy JS API 的弃用警告（uni 插件内部仍使用旧 API）
          silenceDeprecations: ["legacy-js-api"],
        },
      },
    },
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
  };
});
