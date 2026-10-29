/**
 * 打印某个 mode 下**会被内联进产物**的端上环境变量 —— 回答「我现在编译出来的包连哪个后端」。
 *
 * <p><b>为何要它</b>：环境变量是构建期内联的（`vite.config.ts` 的 `define`），编译后就不可改。
 * 一旦两套 env 配错（例如本地包里内联了云端地址、或地址干脆为空），端上只表现为「请求失败」，
 * 而错误信息指不到 env —— 编译前跑一次本脚本，先把这事钉死。
 *
 * <p><b>用法</b>：
 * <pre>
 *   node scripts/check-env.mjs            # 缺省 development
 *   node scripts/check-env.mjs production
 * </pre>
 */
import { loadEnv } from "vite";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const mode = process.argv[2] ?? "development";

const env = loadEnv(mode, ROOT, "VITE_");
const apiBase = (env.VITE_API_BASE_URL ?? "").trim();
const tcbEnv = (env.VITE_TCB_ENV ?? "").trim();
const tcbService = (env.VITE_TCB_SERVICE ?? "").trim();
const thumb = (env.VITE_USE_COS_THUMB ?? "").trim();

console.log(`\n=== client env（mode = ${mode}）===`);
console.log(`VITE_API_BASE_URL   = ${apiBase || "(未配置)"}`);
console.log(`VITE_TCB_ENV        = ${tcbEnv || "(未配置)"}`);
console.log(`VITE_TCB_SERVICE    = ${tcbService || "(未配置)"}`);
console.log(`VITE_USE_COS_THUMB  = ${thumb || "(false)"}`);

if (tcbEnv) {
  console.log(`\n⇒ 通道：云调用 wx.cloud.callContainer（env=${tcbEnv}，服务=${tcbService || "默认"}）`);
} else if (apiBase) {
  const scheme = apiBase.startsWith("http://") ? "http（仅调试可用）" : "https";
  console.log(`\n⇒ 通道：直连 ${apiBase}（${scheme}）`);
  if (apiBase.startsWith("http://")) {
    console.log("  ⚠ http 直连：只能用于开发者工具（勾「不校验合法域名」）与真机调试，不可作为体验版 / 正式版");
  }
} else {
  console.log("\n✗ 两个通道都没配：请求无法发出 —— 检查 client/.env.development 与 client/.env.production");
  process.exitCode = 1;
}
