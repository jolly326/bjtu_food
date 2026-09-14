/**
 * 菜品表单内联新建「食堂 / 档口」的共享逻辑（docs/loop/design/dish-entry-flow.md §1.3 / §3.2-3.3）。
 *
 * 抽出的原因：DishFormDialog（菜品表单叠层小弹窗）与新建档口小弹窗里的「+ 新建食堂」
 * 两条路径都要「新建食堂 → 追加选项 → 自动选中」，共用同一实现可避免两套文案/交互漂移。
 */
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import type { Canteen } from '@/types'

export interface CanteenCreateResult {
  id: number
  name: string
}

/**
 * 新建食堂：仅名称必填（位置 / 描述 / 图片选填，§1.3(B) 注：原独立管理页的 location 必填口径已降级）。
 * 提交沿用 `POST /admin/canteens`（后台录入默认 audit_status=approved，不改后端契约）。
 * 失败时向上抛出，由调用方保留表单数据并提示（§1.4）。
 */
export async function createCanteenInline(payload: {
  name: string
  location?: string
  description?: string
  image?: string
}): Promise<CanteenCreateResult> {
  const store = useAdminStore()
  const toast = useToastStore()
  const name = payload.name.trim()
  if (!name) throw new Error('食堂名称不能为空')
  await store.addCanteen({
    name,
    location: payload.location?.trim() || '',
    description: payload.description?.trim() || '',
    image: payload.image || '',
    sort_order: 0,
    status: 'active',
  })
  // 重名等失败已在上面抛错；成功后 store.list 已刷新，取回新行 id 供级联选中
  const created = [...store.canteens]
    .filter((c: Canteen) => c.name === name)
    .sort((a, b) => Number(b.id) - Number(a.id))[0]
  if (!created) {
    toast.error('食堂已创建，但未能定位新记录，请刷新后重选')
    throw new Error('食堂已创建，但未能定位新记录')
  }
  return { id: Number(created.id), name: created.name }
}

/**
 * 新建档口：食堂 + 档口名必填，楼层 / 窗口号 / 位置 / 图片 / 描述选填。
 * 提交沿用 `POST /admin/stalls`（status=open，即前端 active；sort_order=0）。
 * 失败时向上抛出（重名 / 食堂已被并发删除等），由调用方保留表单数据并提示。
 */
export async function createStallInline(payload: {
  canteenId: number | string
  name: string
  floor?: string
  windowNo?: string
  location?: string
  image?: string
  description?: string
}): Promise<{ id: number; name: string }> {
  const store = useAdminStore()
  const canteenId = Number(payload.canteenId)
  const name = payload.name.trim()
  if (!canteenId) throw new Error('请选择所属食堂')
  if (!name) throw new Error('档口名称不能为空')
  await store.addStall({
    canteen_id: canteenId as unknown as bigint,
    name,
    floor: payload.floor?.trim() || '',
    windowNo: payload.windowNo?.trim() || '',
    location: payload.location?.trim() || '',
    description: payload.description?.trim() || '',
    image: payload.image || '',
    avg_rating: 0,
    sort_order: 0,
    status: 'active',
  })
  const created = [...store.stalls]
    .filter(s => Number(s.canteen_id) === canteenId && s.name === name)
    .sort((a, b) => Number(b.id) - Number(a.id))[0]
  if (!created) throw new Error('档口已创建，但未能定位新记录')
  return { id: Number(created.id), name: created.name }
}
