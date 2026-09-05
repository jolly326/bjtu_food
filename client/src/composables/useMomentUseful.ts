import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'

/**
 * 动态「有用」toggle 复用逻辑（抽取自 pages/dynamic 与 pages/my-published 两份 MomentCard 的近全量重复脚本）。
 * 乐观更新 + 失败回滚 + pending 锁防连点；调用方在 toggle 成功后自行 emit('useful', moment)。
 */
export function useMomentUseful(moment: Moment) {
  const userStore = useUserStore()

  // 初始与回显均取 moment.useful（api 层已归一当前用户点赞态）
  const usefulActive = ref(!!moment.useful)
  // 列表刷新/详情返回后 moment 对象更新时，同步点赞态回显（避免跨页状态丢失）
  watch(() => moment.useful, (v) => {
    usefulActive.value = !!v
  })

  /** pending 锁防连点（P0 防重复请求 / 计数漂移） */
  const pendingUseful = ref(false)
  async function onUseful() {
    if (!userStore.requireAuth(() => onUseful())) return
    if (pendingUseful.value) return
    pendingUseful.value = true
    const prevActive = usefulActive.value
    const prevCount = moment.usefulCount || 0
    usefulActive.value = !prevActive
    moment.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
    try {
      const res = await momentApi.toggleUseful(moment.id)
      usefulActive.value = res.useful
      moment.usefulCount = res.usefulCount
      moment.useful = res.useful
    } catch {
      usefulActive.value = prevActive
      moment.usefulCount = prevCount
      uni.showToast({ title: '操作失败', icon: 'none' })
    } finally {
      pendingUseful.value = false
    }
  }

  return { usefulActive, onUseful }
}
