import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'

/**
 * 列表拖拽排序：统一管理 `dragIndex` / `onDragStart` / `onDrop`，落位后调用 `sortApi` 全量保存并 `load` 刷新。
 * 视图 / 举报原因 / Banner / 维度四个管理视图的 onDrop 算法完全一致，仅 `sortApi` 不同，收口至此。
 */
export function useReorder<T extends { id: number }>(
  items: Ref<T[]>,
  sortApi: (payload: { items: { id: number; order: number }[] }) => Promise<unknown>,
  load: () => Promise<unknown>,
  successMessage = '顺序已保存',
) {
  const dragIndex = ref<number | null>(null)

  function onDragStart(index: number): void {
    dragIndex.value = index
  }

  async function onDrop(index: number): Promise<void> {
    const from = dragIndex.value
    dragIndex.value = null
    if (from === null || from === index) return
    const next = [...items.value]
    const [moved] = next.splice(from, 1)
    if (!moved) return
    next.splice(index, 0, moved)
    items.value = next
    try {
      await sortApi({ items: next.map((it, i) => ({ id: it.id, order: i + 1 })) })
      if (successMessage) ElMessage.success(successMessage)
      await load()
    } catch (e) {
      fail(e, '排序保存失败')
      await load()
    }
  }

  return { dragIndex, onDragStart, onDrop }
}
