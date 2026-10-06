/**
 * 发现态数据源：猜你喜欢词条。
 *
 * 与结果态的检索、分页、三态无关，故独立成 composable；将来要加「热门词 / 最近浏览」在此扩展。
 *
 * 失败静默：`fetchGuessLike` 内部已兜底为空数组（空 ⇒ 整块不渲染），发现态不该因它拖累首屏。
 */
import { storeToRefs } from 'pinia'
import { useDishStore } from '@/stores/dish'

export function useDiscover() {
  const dishStore = useDishStore()
  // 直接用 storeToRefs 取 getter —— 对 store getter 再包一层 computed 属冗余包装
  const { guessLikeList } = storeToRefs(dishStore)

  /** 拉取发现态数据（发现态目前仅猜你喜欢；无食堂字典端点） */
  function load() {
    return dishStore.fetchGuessLike()
  }

  return { guessLikeList, load }
}
