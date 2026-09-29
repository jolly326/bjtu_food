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

  /** 扁平索引 `${fieldKey}:${valueKey}` → label（渲染期查询用） */
  const index = computed(() => {
    const map = new Map<string, string>()
    for (const def of list.value) {
      for (const opt of def.options) {
        map.set(`${def.fieldKey}:${opt.valueKey}`, opt.label)
      }
    }
    return map
  })

  /** 某维度的定义（保持后端下发的顺序） */
  function defOf(field: string): DishAttributeDef | undefined {
    return list.value.find(d => d.fieldKey === field)
  }

  /** 某维度的取值列表（closed 维度的 options） */
  function itemsOf(field: string) {
    return defOf(field)?.options ?? []
  }

  /** 单选维：机器值 → 中文标签；空值回落「—」；字典未命中原样透出 */
  function labelOf(field: string, value?: string | null): string {
    if (!value) return '—'
    return index.value.get(`${field}:${value}`) ?? value
  }

  /** 多选维：数组 / CSV 串 → 中文「 · 」连接；未命中原样透出；空值回落「—」 */
  function labelsText(field: string, raw?: unknown): string {
    const arr = parseCsv(raw)
    return arr.length ? arr.map(v => index.value.get(`${field}:${v}`) ?? v).join(' · ') : '—'
  }

  /** 字典选项（表单下拉 / chips）：{ value, label } 列表 —— 展示与录入同源 */
  function optionsOf(field: string): { value: string; label: string }[] {
    return itemsOf(field).map(it => ({ value: it.valueKey, label: it.label }))
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
