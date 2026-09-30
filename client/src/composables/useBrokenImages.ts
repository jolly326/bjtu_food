import { ref } from 'vue'

/**
 * 破图下标集合：image 加载失败（`@error`）后切中性占位；消费方在 images 变化（重拉 / 重设）时调 `clear()` 重置。
 * 评价配图、图片选择等多处共用同一套「index → 是否破图」逻辑，统一收敛于此，避免各组件重复实现。
 */
export function useBrokenImages() {
  const broken = ref<Set<number>>(new Set())

  /** 记录第 i 张图加载失败（不可变更新，保持响应式） */
  function markBroken(i: number) {
    if (broken.value.has(i)) return
    const next = new Set(broken.value)
    next.add(i)
    broken.value = next
  }

  /** images 变化（重拉 / 外部重设）时重置 */
  function clear() {
    broken.value = new Set()
  }

  return { broken, markBroken, clear }
}
