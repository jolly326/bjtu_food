/**
 * v-zoomimg：给 <img> 挂「点击放大预览」入口。
 *
 * - 保留原生 <img> 及其 class / 样式（零视觉回归），仅叠加交互与光标；
 * - 鼠标点击 / 键盘 Enter·空格 均触发；
 * - 键盘事件 stopPropagation，避免冒泡到列表卡片的长按 / 键盘菜单（见 useCardMenu）。
 */
import type { Directive } from 'vue'
import { openImageZoom } from '@/composables/useImageZoom'

export const vZoomImg: Directive<HTMLImageElement> = {
  mounted(el) {
    el.style.cursor = 'zoom-in'
    el.setAttribute('role', 'button')
    el.setAttribute('aria-label', '点击放大查看图片')
    el.tabIndex = 0

    const open = () => openImageZoom(el.currentSrc || el.src, el.alt)
    el.addEventListener('click', open)
    el.addEventListener('keydown', (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        e.stopPropagation()
        open()
      }
    })
  },
}
