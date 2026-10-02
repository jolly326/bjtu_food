/**
 * 图片规格收敛（尺寸 / 大小 / 画质阶梯）—— `ImagePicker` 的可测内核。
 *
 * <p><b>为何抽出</b>：原为 `components/ImagePicker.vue` 内的 `normalizeMp`，
 * 与 200 行模板、平台 API Promise 化封装（`pick` / `compressImage` / `getImageInfo` /
 * `getFileSystemManager`）混在一个文件里。它恰恰是整个组件**最该被测**的部分 ——
 * 收敛策略错了会直接导致「图片传不上去」或「传了张违规图」，但因位置而无法覆盖。
 *
 * <p><b>为何用适配器注入</b>：平台 API（`wx.compressImage` 等）是条件编译 + 句柄对象，
 * node 环境无法构造。本模块只定义**策略**（压几轮、缩到多少、何时判失败），
 * 具体的平台调用由 `ImagePicker.vue` 注入 —— 于是策略可被完整单测，而组件侧零改动。
 */

/** 校验常量（后端契约：最长边 ≤1334px；文件 ≤1MB） */
export const MAX_EDGE = 1334
export const MAX_SIZE = 1024 * 1024
/** 首压画质 */
export const FIRST_QUALITY = 80
/**
 * 降质再压的画质阶梯（60 → 40）。
 * ⚠️ 逐级降质而非一次到底：图片内容复杂度差异大，一次压到 40 可能糊得看不清。
 * 阶梯收敛即停（压缩不再变小即 break），最多两轮。
 */
export const RETRY_QUALITIES: readonly number[] = [60, 40]

/** 是否超出最长边限制（宽高任一超限即算超；非正数视为读取失败，交由上层放行） */
export function exceedsMaxEdge(width: number, height: number): boolean {
  if (!(width > 0) || !(height > 0)) return false
  return Math.max(width, height) > MAX_EDGE
}

/**
 * 等比缩边目标尺寸（最长边收敛到 `MAX_EDGE`）。
 * @returns 未超限时返回 `null`（无需缩放）
 */
export function computeScaledSize(
  width: number,
  height: number,
): { width: number; height: number } | null {
  if (!exceedsMaxEdge(width, height)) return null
  const scale = MAX_EDGE / Math.max(width, height)
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

/** 平台能力适配器（由调用方注入真实实现） */
export interface ImageNormalizeAdapters {
  /** 压缩；`opts` 为画质或目标尺寸。返回新临时路径，空串表示平台未产出 */
  compress: (
    src: string,
    opts: { quality?: number; compressedWidth?: number; compressedHeight?: number },
  ) => Promise<string>
  /** 读宽高；失败时由调用方抛出，本模块不阻断 */
  getSize: (src: string) => Promise<{ width: number; height: number }>
  /** 读文件字节数 */
  getFileSize: (src: string) => Promise<number>
}

/** 选图产物的最小形状（只需路径与初始大小） */
export interface PickedImage {
  path: string
  size: number
}

/**
 * 单张图收敛至规格内：首压 → 尺寸超限按比例缩边 → 大小超限按阶梯降质再压。
 * 收敛失败（如仍 >1MB）抛错，由调用方 toast 并跳过该张。
 *
 * <p><b>容错口径</b>（各步失败均**不阻断**，只放弃该步优化）：
 * 首压失败 ⇒ 用原图继续；尺寸读取失败 ⇒ 交大小校验兜底；单轮压缩无产出 ⇒ 停止降质。
 * 理由：宁可上传一张偏大的图让后端裁决，也不该让用户在正常网络波动下选不了图。
 */
export async function normalizeImage(
  input: PickedImage,
  adapters: ImageNormalizeAdapters,
): Promise<string> {
  const { compress, getSize, getFileSize } = adapters

  // ① 首次压缩（quality 80）：压缩失败回退原图（仍可走后续校验 / 上传）
  let path = input.path
  try {
    const first = await compress(input.path, { quality: FIRST_QUALITY })
    if (first) path = first
  } catch {
    path = input.path
  }

  // ② 最长边 ≤1334：超出按比例等比缩边（低基础库不支持 compressedWidth 时保持当前结果）
  try {
    const info = await getSize(path)
    const target = computeScaledSize(info.width, info.height)
    if (target) {
      const scaled = await compress(path, {
        compressedWidth: target.width,
        compressedHeight: target.height,
      })
      if (scaled) path = scaled
    }
  } catch {
    /* 尺寸读取失败不阻断：交由大小校验兜底 */
  }

  // ③ 文件 ≤1MB：按阶梯降质再压，压缩不再收敛即停，仍超限抛错跳过
  let size = await getFileSize(path)
  for (const quality of RETRY_QUALITIES) {
    if (size <= MAX_SIZE) break
    const next = await compress(path, { quality }).catch(() => '')
    if (!next) break
    // 读大小失败视为「无进展」：宁可不换图，也别把路径换成一张读不出大小的
    const nextSize = await getFileSize(next).catch(() => Number.MAX_SAFE_INTEGER)
    if (nextSize >= size) break
    path = next
    size = nextSize
  }
  if (size > MAX_SIZE) throw new Error('图片过大（超1MB），已跳过')
  return path
}

/** H5 端无 wx 压缩链路：仅做大小门禁后直传 */
export function assertSizeWithinLimit(size: number): void {
  if (size > MAX_SIZE) throw new Error('图片过大（超1MB），已跳过')
}