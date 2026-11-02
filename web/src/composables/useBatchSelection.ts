import { computed, ref, type Ref } from 'vue'

/**
 * 批量选择 + 分批提交（菜品 / 档口管理页共用）。
 *
 * 页面只负责「行 → 提交体」的映射与具体 API 调用；选择状态、全选判定与
 * 分批并发（每批 6 条）收敛在此，避免两页各写一份而漂移。
 */
export function useBatchSelection<T extends { id: number; name: string }>(rows: Ref<T[]>) {
  const batchMode = ref(false)
  const selected = ref<Set<number>>(new Set())
  const batchBusy = ref(false)

  const selectedRows = computed(() => rows.value.filter((r) => selected.value.has(r.id)))
  const selectedCount = computed(() => selectedRows.value.length)
  const allSelected = computed(
    () => rows.value.length > 0 && rows.value.every((r) => selected.value.has(r.id)),
  )

  function toggleBatchMode() {
    batchMode.value = !batchMode.value
    selected.value = new Set()
  }

  function clearSelection() {
    selected.value = new Set()
  }

  function toggleSelect(row: T) {
    const next = new Set(selected.value)
    if (next.has(row.id)) next.delete(row.id)
    else next.add(row.id)
    selected.value = next
  }

  function toggleSelectAll() {
    selected.value = allSelected.value ? new Set() : new Set(rows.value.map((r) => r.id))
  }

  /** 逐条提交（每批 6 条并发），返回成功 / 失败统计；无选中时直接返回空结果 */
  async function runBatch(
    update: (row: T) => Promise<unknown>,
  ): Promise<{ ok: number; failed: { name: string }[] }> {
    const targets = selectedRows.value
    if (!targets.length) return { ok: 0, failed: [] }

    batchBusy.value = true
    let ok = 0
    const failed: { name: string }[] = []
    try {
      for (let i = 0; i < targets.length; i += 6) {
        const chunk = targets.slice(i, i + 6)
        const results = await Promise.allSettled(chunk.map(update))
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') ok += 1
          else failed.push({ name: chunk[index]!.name })
        })
      }
    } finally {
      batchBusy.value = false
    }
    return { ok, failed }
  }

  return {
    batchMode,
    selected,
    batchBusy,
    selectedCount,
    allSelected,
    toggleBatchMode,
    clearSelection,
    toggleSelect,
    toggleSelectAll,
    runBatch,
  }
}
