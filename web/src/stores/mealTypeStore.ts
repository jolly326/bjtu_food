import { ref } from 'vue'
import { defineStore } from 'pinia'
import { dishApi } from '@/api'
import type { MealTypeDictItem } from '@/api/dish'

/**
 * 菜品大类字典 store（2026-09-21 §7.34 / change `home-ui-refresh`）。
 *
 * 单一真源：标签文案 / 顺序 / 集合由后端 `GET /dishes/meal-types`（`MealTypeConst`）下发，
 * **本 store 与任何视图都不得维护 key → 中文 的映射或标签清单**。
 *
 * 覆盖范围口径（重要）：该端点**只含当前有在售菜品**的大类（空类自动隐藏，是首页标签栏的
 * 设计意图）。管理端菜品列表含已下架菜品，因此：
 *  - 展示 / 下拉：字典未覆盖的枚举键按「原样透出」处理（`labelOf` 回落键本身，不吞不译）；
 *  - 筛选选项：由消费页在字典项之后追加「列表实际出现但字典未覆盖」的键，避免该类无法筛选。
 *
 * **不在 setup 顶层自动加载（WEB-02）**：由需要页面 / 弹窗显式调用 loadAll()；
 * 已加载则命中缓存不重复请求（字典为低频静态数据，仅失败态需重试）。
 */
export const useMealTypeStore = defineStore('mealType', () => {
  const list = ref<MealTypeDictItem[]>([])
  const loading = ref(false)
  const error = ref('')

  async function loadAll() {
    loading.value = true
    error.value = ''
    try {
      list.value = await dishApi.listMealTypes()
    } catch (e: any) {
      // 失败不落库脏数据：保留上一次的列表（若有），仅置错误态供调用方展示与重试
      error.value = e?.message || '加载菜品大类失败'
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

  /** 大类枚举键 → 展示文案（字典命中用后端标签；未覆盖的键原样透出；空值回落「—」） */
  function labelOf(key?: string | null): string {
    if (!key) return '—'
    return list.value.find(t => t.value === key)?.label || key
  }

  return { list, loading, error, loadAll, ensureLoaded, labelOf }
})
