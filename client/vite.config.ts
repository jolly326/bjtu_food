import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";
import { fileURLToPath, URL } from "node:url";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
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
});
