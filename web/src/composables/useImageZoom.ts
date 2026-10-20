/**
 * 图片点击放大预览（web 端）。
 *
 * 单一共享状态 + open / close：`ImageZoomOverlay` 消费它渲染全屏预览，
 * `v-zoomimg` 指令在任意 <img> 上挂「点击 / 回车 / 空格」放大入口。
 * 三者解耦：状态（本文件）→ 触发（指令）→ 呈现（覆盖层组件）。
 */
import { reactive } from 'vue'

export const imageZoom = reactive({
  open: false,
  src: '',
  alt: '',
  scale: 1,
  tx: 0,
  ty: 0,
})

const MIN_SCALE = 1
const MAX_SCALE = 4

/** 打开预览：载入目标图并重置缩放 / 平移 */
export function openImageZoom(src: string, alt = ''): void {
  imageZoom.src = src
  imageZoom.alt = alt
  imageZoom.scale = 1
  imageZoom.tx = 0
  imageZoom.ty = 0
  imageZoom.open = true
}

export function closeImageZoom(): void {
  imageZoom.open = false
}

/** 滚轮缩放：步进 1.15，夹在 [1, 4]；回到 1x 时重置平移 */
export function zoomImageZoom(deltaY: number): void {
  const next = imageZoom.scale * (deltaY < 0 ? 1.15 : 1 / 1.15)
  imageZoom.scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, next))
  if (imageZoom.scale === MIN_SCALE) {
    imageZoom.tx = 0
    imageZoom.ty = 0
  }
}

/** 点击图片：在「适应屏幕(1x)」与「放大(2x)」间切换 */
export function toggleImageZoom(): void {
  if (imageZoom.scale > MIN_SCALE) {
    imageZoom.scale = MIN_SCALE
    imageZoom.tx = 0
    imageZoom.ty = 0
  } else {
    imageZoom.scale = 2
  }
}
