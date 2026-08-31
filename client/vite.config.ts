import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";
import { fileURLToPath, URL } from "node:url";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
// manifest.json 是 JSONC（含 /* */ 注释），标准 JSON.parse 会失败；
// 用正则提取 versionName 作为版本号单一真源，构建期注入 __APP_VERSION__（小程序运行时读不到 manifest）。
const manifestRaw = readFileSync(resolve(__dirname, "src/manifest.json"), "utf-8");
const versionMatch = manifestRaw.match(/"versionName"\s*:\s*"([^"]+)"/);
const APP_VERSION = versionMatch ? versionMatch[1] : "0.0.0";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  define: {
    // 全局常量替换：源码中可直接使用 __APP_VERSION__（见 src/env.d.ts 声明）
    __APP_VERSION__: JSON.stringify(APP_VERSION),
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
