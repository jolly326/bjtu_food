/**
 * 分享状态（分享统一走微信原生组件）
 * 卡片/弹层点击分享（open-type=share）时先记录当前内容，
 * 页面级 onShareAppMessage 读取该状态生成分享卡片。
 */
import { ref } from 'vue'
import type { Moment } from '@/types/moment'

/** 待分享的菜品（分享路径见 buildSharePayload） */
interface ShareDish {
  id: number
  name: string
  price: number
  stallId?: number
  canteen?: string
  stallName?: string
}

export const sharedMoment = ref<Moment | null>(null)
export const sharedDish = ref<ShareDish | null>(null)

/** 分享文案/路径构造（onShareAppMessage 使用；优先显式参数，其次共享状态） */
export function buildSharePayload(dish?: ShareDish | null, moment?: Moment | null) {
  const d = dish ?? sharedDish.value
  const m = moment ?? sharedMoment.value
  if (d) {
    return {
      title: `推荐「${d.name}」¥${d.price}${d.stallName ? ' · ' + d.stallName : ''}，来自食在交大`,
      path: `/pages/pages-detail/dish?id=${d.id}`,
    }
  }
  if (m) {
    return {
      title: `${m.userNickname || '食在交大用户'}：${m.content?.slice(0, 30) || '看看这条动态'}`,
      path: `/pages/pages-detail/moment?id=${m.id}`,
    }
  }
  return { title: '食在交大 · 发现校园美食', path: '/pages/home/index' }
}
