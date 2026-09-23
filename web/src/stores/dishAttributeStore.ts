import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { dishApi } from '@/api'
import { parseCsv } from '@/constants'
import type { DishAttributeDictItem } from '@/api/dish'

/** 维度字段名常量（与菜品出参字段名逐字一致，R13） */
export const ATTR_DIET_TYPE = 'dietType'
export const ATTR_INGREDIENTS = 'ingredients'
export const ATTR_FLAVOR_TAGS = 'flavorTags'
export const ATTR_SERVE_TEMP = 'serveTemp'

/**
 * 菜品描述四维字典 store（2026-09-23 §7.40 R4）。
 *
 * **单一真源**：四维（荤素 / 主料 / 口味 / 冷热）的取值与中文标签由后端
 * `GET /dishes/attributes`（`DishAttributeConst`）下发 —— **本 store 与任何视图都不得维护
 * `value → 中文` 的映射表或选项数组**。改动前 Web 端在 `constants/index.ts` 硬编码了 4 张
 * `*_META` + 4 组 `*_OPTIONS`（同时用于展示与表单），与 client 端 `api/dish.ts` 的 4 张表
 * 构成**两套前端真源**（连同后端常量表共三套，违反 PR-12）。
 *
 * **字段名契约（R13）**：字典项的 `field` **恒等于菜品出参字段名** → 消费方直接按字段名取其分组，
 * SHALL NOT 另建「字典 field → VO 字段」的第二套映射。
 *
 * **与 `mealTypeStore` 的差异**：本字典**下发全部取值、不做在售过滤**（四维是描述属性，
 * 录入表单需要完整选项）→ 消费方**无需**追加「列表中实际出现但字典未覆盖」的兜底项。
 * 但为兼容库中历史脏值，`labelOf` / `labelsText` 仍按「字典未命中 → **原样透出**」处理（不吞不译）。
 *
 * **不在 setup 顶层自动加载（对齐 WEB-02）**：由需要页面 / 弹窗显式调用 `ensureLoaded()`。
 */
export const useDishAttributeStore = defineStore('dishAttribute', () => {
  const list = ref<DishAttributeDictItem[]>([])
  const loading = ref(false)
  const error = ref('')

  async function loadAll() {
    loading.value = true
    error.value = ''
    try {
      list.value = await dishApi.listDishAttributes()
    } catch (e: any) {
      // 失败不落库脏数据：保留上一次的列表（若有），仅置错误态供调用方展示与重试
      error.value = e?.message || '加载菜品描述四维失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 已加载则直接返回（调用方按需用 `ensureLoaded` 避免重复请求） */
  async function ensureLoaded() {
    if (list.value.length) return
    await loadAll()
  }

  /** `${field}:${value}` → label（渲染期查询用，避免每次线性扫描） */
  const index = computed(() => {
    const map = new Map<string, string>()
    for (const it of list.value) map.set(`${it.field}:${it.value}`, it.label)
    return map
  })

  /** 某维度的全部字典项（保持后端下发的组内顺序） */
  function itemsOf(field: string): DishAttributeDictItem[] {
    return list.value.filter(it => it.field === field)
  }

  /**
   * 单选维（`dietType` / `serveTemp`）：机器值 → 中文标签。
   * 空值回落「—」；**字典未覆盖的值原样透出**（历史脏值不吞不译）。
   */
  function labelOf(field: string, value?: string | null): string {
    if (!value) return '—'
    return index.value.get(`${field}:${value}`) ?? value
  }

  /**
   * 多选维（`ingredients` / `flavorTags`）：**数组 / CSV 串** → 中文「 · 」连接（空 = 「—」）。
   * 取值归一复用 `parseCsv`（R4 数组化前后两种形态均可用）；未覆盖的值原样透出。
   */
  function labelsText(field: string, raw?: unknown): string {
    const arr = parseCsv(raw)
    return arr.length ? arr.map(v => index.value.get(`${field}:${v}`) ?? v).join(' · ') : '—'
  }

  /**
   * 字典选项（表单下拉 / chips）：`{ value, label }` 列表 —— **展示与录入同源**。
   * 单选维调用方可自行前置一个空值项（如「未填写」），多选维直接用。
   */
  function optionsOf(field: string): { value: string; label: string }[] {
    return itemsOf(field).map(it => ({ value: it.value, label: it.label }))
  }

  return { list, loading, error, loadAll, ensureLoaded, itemsOf, labelOf, labelsText, optionsOf }
})
