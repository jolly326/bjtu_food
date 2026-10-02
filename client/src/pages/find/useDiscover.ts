/**
 * 发现态数据源：猜你喜欢词条。
 *
 * <p><b>为何独立成 composable</b>：发现态目前只剩一个请求，但它是**明确的独立关注点**
 * （与结果态的检索、分页、三态无关）。独立后「发现态还有哪些内容」一目了然，
 * 将来要加「热门词 / 最近浏览」等只需在此扩展，不必回到 569 行的页面文件里翻。
 *
 * <p><b>失败静默</b>：猜你喜欢加载失败不呈现任何占位（空数组 ⇒ 整块不渲染），
 * 异常仅记日志 —— 发现态是次要内容，不该因它拖累首屏。
 */
import { storeToRefs } from 'pinia'
import { useDishStore } from '@/stores/dish'

export function useDiscover() {
  const dishStore = useDishStore()

  // 直接用 storeToRefs 取 getter（原先对 store getter 再包一层 computed 属冗余包装）
  const { guessLikeList } = storeToRefs(dishStore)

  /** 拉取发现态数据（发现态目前仅猜你喜欢；无食堂字典端点） */
  async function load() {
    try {
      await dishStore.fetchGuessLike()
    } catch (e) {
      console.error('[find] 发现页加载失败', e)
    }
  }

  return { guessLikeList, load }
}
