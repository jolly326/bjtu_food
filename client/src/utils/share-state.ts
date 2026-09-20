/**
 * 分享状态（分享统一走微信原生组件）
 * 卡片/弹层点击分享（open-type=share）时先记录当前内容，
 * 页面级 onShareAppMessage 读取该状态生成分享卡片。
 */
import { ref } from 'vue'
import { PATH, dishDetailUrl } from '@/utils/routes'

/** 待分享的菜品（分享路径见 buildSharePayload；仅承载分享卡片实际读取的字段） */
interface ShareDish {
  id: number
  name: string
  price: number
  stallName?: string
}

export const sharedDish = ref<ShareDish | null>(null)

/** 清空共享分享状态：进入无特定分享上下文页面（首页/我的/搜索）时调用，避免残留上次分享内容 */
export function clearShareState() {
  sharedDish.value = null
}

/**
 * 分享文案/路径构造（onShareAppMessage 使用；优先显式参数，其次共享状态）。
 * 注意：读取共享状态后立即清空（消费即清空）——微信分享面板取消时 onShareAppMessage 不触发，
 * 残留必须由页面 onShow 调用 clearShareState() 兜底；此处清空防止"读旧分享"。
 */
export function buildSharePayload(dish?: ShareDish | null) {
  const d = dish ?? sharedDish.value
  if (dish == null) {
    sharedDish.value = null
  }
  if (d) {
    return {
      title: `推荐「${d.name}」¥${d.price}${d.stallName ? ' · ' + d.stallName : ''}，来自知行食记`,
      path: dishDetailUrl(d.id),
    }
  }
  return { title: '知行食记 · 发现校园美食', path: PATH.home }
}
