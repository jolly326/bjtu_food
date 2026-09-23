import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getDishAttributes, type DishAttribute } from '@/api/dish-attribute'

/**
 * 菜品描述四维字典 store（2026-09-23 `project_spec.md` §7.40 R4）。
 *
 * 职责：一次性拉取后端字典并**会话级缓存**，供视图层把菜品出参的**机器值**翻译为中文展示值。
 * 端上因此**零硬编码映射表**（原 `api/dish.ts` 的 4 张 `*_MAP` 已退役）。
 *
 * 失败策略：字典拉取失败**静默**（`items` 保持空）—— 四维整块按「缺项不占位」口径不渲染，
 * 不阻塞详情页主体；下次进入页面会重试（`loaded` 仅在成功后置真）。
 */
export const useDishAttributeStore = defineStore('dish-attribute', () => {
  const items = ref<DishAttribute[]>([])
  const loaded = ref(false)

  /** `${field}:${value}` → label（渲染期查询用，避免每次线性扫描） */
  const index = computed(() => {
    const map = new Map<string, string>()
    for (const it of items.value) {
      map.set(`${it.field}:${it.value}`, it.label)
    }
    return map
  })

  /** 确保字典已就绪（幂等；失败静默、可重试） */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) return
    try {
      items.value = await getDishAttributes()
      loaded.value = true
    } catch {
      /* 静默：四维不渲染，不阻塞页面 */
    }
  }

  /** 单值维：机器值 → 中文标签（空值 / 未命中返回空串，由消费方按「缺项不渲染」处理） */
  function labelOf(field: string, value?: string): string {
    if (!value) return ''
    return index.value.get(`${field}:${value}`) || ''
  }

  /**
   * 多值维：机器值数组 → 中文标签数组。
   * 按**字典 order 升序**输出（展示顺序由后端定义，端上不再自定义排序）；未命中的值剔除。
   */
  function labelsOf(field: string, values?: string[]): string[] {
    if (!values || !values.length) return []
    const orderOf = new Map(
      items.value.filter(it => it.field === field).map(it => [it.value, it.order]),
    )
    return values
      .map(v => ({ label: index.value.get(`${field}:${v}`), order: orderOf.get(v) ?? Number.MAX_SAFE_INTEGER }))
      .filter((x): x is { label: string; order: number } => !!x.label)
      .sort((a, b) => a.order - b.order)
      .map(x => x.label)
  }

  return { items, loaded, ensureLoaded, labelOf, labelsOf }
})
