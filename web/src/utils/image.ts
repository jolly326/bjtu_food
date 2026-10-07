/**
 * 图片地址归一（**web端唯一实现**，与 client `utils/image.ts::getImageUrl` 同口径）。
 *
 * <p>**存在理由**：管理端后端返回的图片地址有三种形态 ——
 * ① COS 绝对 URL（`https://xxx.myqcloud.com/...`）；② **相对路径**（`POST /admin/upload` 的返回，
 * 见 [UI 基线 §1.11](../../../docs/ui/web/公共组件与形态基线.md)，如 `/images/xxx.jpg` 或裸文件名）；
 * ③ `data:`（本地上传预览）。裸 `<img src="相对路径">` 会打到 **web 域名根路径**而不是 API 基址
 * ⇒ 缩略图裂图，且与上传组件内的预览表现不一致。故**所有** `<img :src>` 必经本函数。
 *
 * <p>**归一规则**（顺序敏感，不可调换）：
 *  1. 空值 → `''`（调用方据此走占位）；
 *  2. `data:` / `blob:` 原样（伪协议不可拼接）；
 *  3. `new URL()` 可解析 ⇒ 视为绝对地址原样返回（避免「绝对 URL 但路径段像相对路径」被误改写）；
 *  4. `/static/` 开头的**站点绝对路径**原样返回（不由 API 基址承载）；
 *  5. 其余（相对路径 / 裸文件名）⇒ 先剥掉可能已存在的 `/api` 前缀（后端返回值可能已带），
 *     再拼 `API_BASE_URL`（该常量本身含 `/api/v1`，不去重会出现 `/api/api` 双重前缀 → 404）。
 */
import { API_BASE_URL } from '@/api/config'

/** 图片地址归一：后端返回的相对路径 / 裸文件名 → 可加载的绝对 URL */
export function resolveImageUrl(path?: string | null): string {
  if (!path) return ''
  // 伪协议：不可拼接基址
  if (/^(data:|blob:)/i.test(path)) return path
  // 能被 URL 解析即视为绝对地址（纯相对路径会抛错，走下方归一化分支）
  try {
    new URL(path)
    return path
  } catch {
    // 解析失败 = 相对路径，继续归一化
  }
  // 站点绝对路径：由 web 站点自身承载，不拼 API 基址
  if (path.startsWith('/static/')) return path
  const normalized = path.replace(/^\/api/, '')
  return `${API_BASE_URL}${normalized.startsWith('/') ? '' : '/'}${normalized}`
}
