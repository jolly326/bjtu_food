/**
 * useDishPage —— 菜品详情页（pages/detail/dish/index.vue）编排逻辑
 *
 * 页面私有编排（仅本页使用，就近置于页面包，不驻留 composables/）：
 * 页面仅保留模板贴片组装与包内子件（ImageSwiper / ReviewComposer / DishInfoCard（含评分行）/
 * DishReviewSection）引用。
 *
 * 职责拆分（职责内聚、无跨文件可变状态）：
 * - `useDishHeroScroll`：顶部大图滚动模型（sticky 两阶段定格的全部几何量）。
 * - `useDishReviewCore`：评价分页（触底加载）、删除本人评价、失败重试。
 * - `useDishReviewComposer`：写评价弹层（恒空表单）、纠错入口。
 * - `useDishReviewMenu`：评价三点菜单（本人删除 / 他人举报路由）。
 * - 本文件持有跨子模块共享的可变态（dishId / dish）与生命周期钩子
 *   （onLoad / onShow / onShareAppMessage），并把 store 取数、上报浏览、分享态写回编排起来。
 *
 * ⚠️ 全部逻辑在函数体内同步执行：由页面在 <script setup> 中同步调用 useDishPage()，
 * 使 store 获取与 onLoad/onShareAppMessage/onShow 均在组件实例上下文中注册
 * （模块顶层注册会报 "no active component instance"）。各子 composable 亦由本函数同步调用，
 * 故其内部的 onMounted / 回调同样在组件上下文中注册。
 */
import { ref, computed } from 'vue'
import type { ComputedRef } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import type { DishDetail } from '@/types/dish'
import { sharedDish } from '@/utils/share-state'
import { backToHome } from '@/utils/back'
import { toastInfo } from '@/utils/error'
import { dishDetailUrl } from '@/utils/routes'
import { useReport } from './useReport'
import { useDishHeroScroll } from './useDishHeroScroll'
import { useDishReviewCore } from './useDishReviewCore'
import { useDishReviewComposer } from './useDishReviewComposer'
import { useDishReviewMenu } from './useDishReviewMenu'

export function useDishPage() {
  const dishStore = useDishStore()
  const userStore = useUserStore()
  const authStore = useAuthStore()

  /**
   * 认证页返回续跑（§5.y）：requireAuth 记录的待办（写评价 / 删除评价）
   * 在认证成功返回本页的 onShow 中续接；无待办时为空操作。首次进入（onLoad 后）pending 恒为空，无副作用。
   */
  onShow(() => {
    authStore.consumePending()
  })

  const dishId = ref(0)
  const dish = computed<DishDetail | null | undefined>(() => dishStore.currentDish)
  const detailFailed = computed(() => dishStore.detailError)
  /**
   * 菜品不存在态（后端 `4001`，§7.40 R8，**不可重试**）：与失败态互斥。
   * 区别对待的缘由：不存在（含已下架）重试也还是不存在，「重新加载」是无效安慰 ——
   * 故只给「返回」，文案明确指出菜品不可见，避免用户反复点重试。
   */
  const detailNotFound = computed(() => dishStore.detailNotFound)
  /** onLoad 缺少 / 非法菜品 id：同样按失败态呈现（不留纯空白页） */
  const missingDishId = ref(false)

  const hero = useDishHeroScroll(dish)
  const reviewCore = useDishReviewCore({ dishId })
  const composer = useDishReviewComposer({
    dish,
    dishId,
    fetchReviewsReset: reviewCore.fetchReviewsReset,
    resetReviewPaging: reviewCore.resetReviewPaging,
  })
  const { reportOpen, reportSubmitting, openReport, submitReport } = useReport()
  const menu = useDishReviewMenu({
    onDeleteReview: reviewCore.onDeleteReview,
    onReport: (rv) => openReport(rv.id),
  })

  /** 位置文案：食堂 · 楼层 · 档口名 */
  const locationText = computed(() => {
    const d = dish.value
    if (!d) return ''
    const nodes: string[] = []
    if (d.canteen) nodes.push(d.canteen)
    if (d.floor) nodes.push(String(d.floor))
    if (d.stallName) nodes.push(d.stallName)
    return nodes.join(' · ') || '未知位置'
  })

  onLoad((query) => {
    const id = Number(query?.id)
    if (!id) {
      // 缺 ID：同「不存在」按失败态呈现（明确文案 + 返回），不留纯空白页
      missingDishId.value = true
      toastInfo('缺少菜品ID')
      return
    }
    missingDishId.value = false
    dishId.value = id
    dishStore.resetDishDetail()
    void loadDishData()
  })

  /** 进入页面并行取数：详情 + 公开评价 + 我的评价（判定底栏态）；浏览计数由详情接口在服务端完成 */
  async function loadDishData() {
    if (!dishId.value) return
    reviewCore.resetReviewPaging()
    // 详情页首屏零用户态请求：仅详情 + 公开评价并行（游客与登录行为完全一致）；
    // 「我是否已评价」的判定推迟到用户点击「写评价」时（见 onOpenReviewComposer）
    const tasks: Promise<unknown>[] = [
      dishStore.fetchDetail(dishId.value),
      reviewCore.fetchReviewsReset(),
    ]
    await Promise.all(tasks)
    syncSharedDish()
  }

  /** 详情请求失败后重试（与进入页面同路径，仅重拉详情）
   *  ⚠️ **返回该 Promise**，供页面等待真实落地后关闭「重新加载」的在途转圈。 */
  function onRetryDetail() {
    if (!dishId.value) return
    return dishStore.fetchDetail(dishId.value)
  }

  /** 写回分享态（供 onShareAppMessage 读取菜名 + 现价） */
  function syncSharedDish() {
    const d = dish.value
    if (d) {
      sharedDish.value = { id: d.id, name: d.name, price: d.price, stallName: d.stallName }
    } else {
      sharedDish.value = null
    }
  }

  onShareAppMessage(() => ({
    title: dish.value ? `${dish.value.name} ¥${dish.value.price}` : '菜品详情',
    path: dishDetailUrl(dishId.value),
  }))

  /** 供页面模板/模板回调使用的全部编排绑定（名称与抽取前 <script setup> 顶层保持一致） */
  return {
    dish,
    dishId,
    dishName: hero.dishName,
    heroImages: hero.heroImages,
    heroHeightPx: hero.heroHeightPx,
    navOpacity: hero.navOpacity,
    onScroll: hero.onScroll,
    locationText,
    reviewList: reviewCore.reviewList,
    reviewFailed: reviewCore.reviewFailed,
    reviewPending: reviewCore.reviewPending,
    detailFailed,
    detailNotFound,
    missingDishId,
    composerOpen: composer.composerOpen,
    reportOpen,
    reportSubmitting,
    reviewMoreOpen: menu.reviewMoreOpen,
    reviewMoreItems: menu.reviewMoreItems,
    backToHome,
    onRetryDetail,
    onReviewMore: menu.onReviewMore,
    onReviewMoreSelect: menu.onReviewMoreSelect,
    onCorrectDishInfo: composer.onCorrectDishInfo,
    onOpenReviewComposer: composer.onOpenReviewComposer,
    onReviewSubmitted: composer.onReviewSubmitted,
    onRetryReviews: reviewCore.onRetryReviews,
    onReviewsReachBottom: reviewCore.onReviewsReachBottom,
    submitReport,
  }
}
