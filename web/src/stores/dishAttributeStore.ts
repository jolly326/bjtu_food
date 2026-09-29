import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { dishApi } from '@/api'
import { parseCsv } from '@/constants'
import type { DishAttributeDef } from '@/api/dish'

/** 维度字段名常量（**恒等于菜品出参字段名**，R13） */
export const ATTR_DIET_TYPE = 'dietType'
export const ATTR_INGREDIENTS = 'ingredients'
export const ATTR_FLAVOR_TAGS = 'flavorTags'
export const ATTR_SERVE_TEMP = 'serveTemp'

export const useDishAttributeStore = defineStore('dishAttribute', () => {
  const list = ref<DishAttributeDef[]>([])
  const loading = ref(false)
  const error = ref('')

  async function loadAll() {
    loading.value = true
    error.value = ''
    try {
      list.value = await dishApi.listDishAttributes()
    } catch (e: unknown) {
      error.value = (e as Error)?.message || '加载菜品描述属性字典失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function ensureLoaded() {
    if (list.value.length) return
    await loadAll()
  }

  /**
   * 取值索引 `${fieldKey}:${value}` → value（恒等映射，保留接口形态）。
   *
   * 2026-09-29：`GET /dishes/attributes` 的 `options` 是**「该维度全库已用值」去重后的中文串**，
   * 值即中文、无独立机器值字典（后端无取值字典表，加值零登记）。
   * 故此处不再做「机器值 → 中文」翻译，端上零翻译即 R4 的直接体现。
   */
  const index = computed(() => {
    const map = new Map<string, string>()
    for (const def of list.value) {
      for (const opt of def.options) {
        map.set(`${def.fieldKey}:${opt}`, opt)
      }
    }
    return map
  })

  /** 某维度的定义（保持后端下发的顺序） */
  function defOf(field: string): DishAttributeDef | undefined {
    return list.value.find(d => d.fieldKey === field)
  }

  /** 某维度的参考候选值列表（值即中文） */
  function itemsOf(field: string) {
    return defOf(field)?.options ?? []
  }

  /** 单选维：取值直接渲染（值即中文）；空值回落「—」；字典未命中原样透出 */
  function labelOf(field: string, value?: string | null): string {
    if (!value) return '—'
    return index.value.get(`${field}:${value}`) ?? value
  }

  /** 多选维：数组 / CSV 串 → 中文「 · 」连接；未命中原样透出；空值回落「—」 */
  function labelsText(field: string, raw?: unknown): string {
    const arr = parseCsv(raw)
    return arr.length ? arr.map(v => index.value.get(`${field}:${v}`) ?? v).join(' · ') : '—'
  }

  /**
   * 字典选项（表单下拉 / chips）：`{ value, label }` 列表 —— 展示与录入同源。
   *
   * `options` 是**参考候选**（该维度全库已用值去重、按频次倒序），**不构成约束**：
   * 消费方应把它当「建议项」渲染，同时保留用户自由输入（后端不校验取值域）。
   * 因值即中文，`value` 与 `label` 相同。
   */
  function optionsOf(field: string): { value: string; label: string }[] {
    return itemsOf(field).map(it => ({ value: it, label: it }))
  }

  return {
    list,
    loading,
    error,
    loadAll,
    ensureLoaded,
    defOf,
    itemsOf,
    labelOf,
    labelsText,
    optionsOf,
  }
})
