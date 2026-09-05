<template>
  <view class="page dish-page">
    <!-- dish-detail-visual-polish：页面内覆盖导航（顶部透明 → 滚动渐显菜名与底白），大图全幅出血 -->
    <view
      class="dish-nav"
      :class="{ solid: navSolid }"
      :style="{ paddingTop: topPad, '--nav-h': navBarHeight + 'px', '--nav-pad-right': navPadRight }"
    >
      <view class="dish-nav-row">
        <view class="dish-nav-back" role="button" aria-label="返回" @tap="backToHome">
          <!-- 返回钮＝显式 chip + 黑箭头（微信原生同款配色）：不再用 ::before 伪元素画底，
               避免伪元素层级盖住箭头导致“看不到 icon” -->
          <view class="dish-back-chip">
            <IconSvg
              name="arrow-left"
              :size="'22px'"
              color="#1A1A1A"
              class="dish-back-icon"
            />
          </view>
        </view>
        <text class="dish-nav-title" :style="{ opacity: dish ? navOpacity : 1 }">{{ dishName }}</text>
      </view>
    </view>

    <!-- 空态/加载/不存在承接条：dish 缺失时固定实底承接返回钮/标题（z 介于滚动内容与覆盖导航之间）；
         有图态无需此条——大图自身持续盖住承接区，定格后由内容流内 .hero-carry 平滑承接 -->
    <view v-if="!dish" class="no-dish-bar" :style="{ height: `${pinLine}px` }" />

    <!-- 大图（.hero-slot）：内容流首块，页面级滚动 + CSS position:sticky 原生实现"两阶段定格"。
         当页面滚动量达到 pinStart 后，浏览器/微信把大图钉在 top:-(heroBase-pinLine)（其底边恰落承接线 pinLine），
         不再逐帧改写 transform——消除"实时计算"造成的偶发闪帧；此后仅下方卡片继续上滑。
         内容未溢出剩余区域时页面本身不滚动，也就没有多余滚动区。 -->
    <view
      v-if="dish"
      class="hero-slot"
      :style="{ height: `${heroBase}px`, top: `-${pinStart}px` }"
    >
      <ImageSwiper
        :images="heroImages"
        :height="`${heroBase}px`"
        :placeholder-size="96"
        placeholder-background="var(--bg-card)"
      />
      <view class="hero-carry" :style="{ height: `${pinLine}px`, opacity: carryOpacity }" />
    </view>

    <template v-if="dish">
      <!-- 私有组件编排：基本信息 / 综合评分（只读）/ 评价（卡内触底加载）。
           内容块最小高度（dishBodyMin）保证：即使内容不足一屏，页面也可滚动 ≥ pinStart，
           「无论如何」都能把顶部大图滑到 header 定格位 -->
      <view class="dish-body" :style="{ minHeight: dishBodyMin + 'px' }">
        <DishInfoCard
          :dish="dish"
          :location-text="locationText"
          :dist-text="distText"
          :dist-active="dishDistance != null"
          @dish-longpress="onDishLongPress"
          @dist-tap="onDistTap"
        />
        <DishSummaryCard
          :rating="dish.rating || 0"
          :rating-count="dish.ratingCount || 0"
          :distribution="ratingDistribution"
        />
        <DishReviewSection
          :reviews="reviewList"
          :total="reviewTotal"
          :current-user-id="currentUserId"
          @delete="onDeleteReview"
          @report="onReviewReport"
          @more="onReviewMore"
        />
      </view>
    </template>

    <!-- 底部固定操作栏：左「写评价」（打开提交弹层）右「去分享」（open-type=share），等宽双按钮 -->
    <view class="action-bar" v-if="dish">
      <button class="bar-btn bar-btn--write" aria-label="写评价" @tap="onOpenReviewComposer">
        <text class="bar-btn-text">写评价</text>
      </button>
      <button class="bar-btn bar-btn--share" open-type="share" aria-label="去分享">
        <text class="bar-btn-text">去分享</text>
      </button>
    </view>

    <!-- 写评价底部抽屉（挂 scroll-view 外；BaseSheet 受控显隐，close 回写关闭；提交成功后重拉评价 + 综合评分） -->
    <ReviewComposer
      v-if="dish"
      :visible="composerOpen"
      :dish-id="dishId"
      :dish-name="dish.name"
      @close="composerOpen = false"
      @submitted="onReviewSubmitted"
    />

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报评价"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 评价三点菜单：删除/举报（通用 ActionSheet） -->
    <ActionSheet
      :open="reviewMoreOpen"
      :items="reviewMoreItems"
      @close="reviewMoreOpen = false"
      @select="onReviewMoreSelect"
    />

    <!-- 认证弹层：点赞等需认证入口统一底部弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage, onPageScroll, onReachBottom } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, getUserLocation } from '@/utils/location'
import { addView, deleteDish } from '@/api/dish'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { useReport } from '@/composables/useReport'
import { sharedDish } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import { getNavBarHeight } from '@/utils/navMetrics'
import IconSvg from '@/components/IconSvg.vue'
import ReportModal from '@/components/ReportModal.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import ImageSwiper from './ImageSwiper.vue'
import ReviewComposer from './ReviewComposer.vue'
import DishInfoCard from './DishInfoCard.vue'
import DishSummaryCard from './DishSummaryCard.vue'
import DishReviewSection from './DishReviewSection.vue'

const dishStore = useDishStore()
const userStore = useUserStore()
const locationStore = useLocationStore()

const dishId = ref(0)
const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const currentDishId = computed(() => dishId.value)
const currentUserId = computed(() => userStore.userInfo?.id)

/** detail-modular-review-cleanup：评价卡内触底分页（不再跳转独立全部评价页） */
const reviewPage = ref(1)
const reviewFinished = ref(false)
const reviewLoadingMore = ref(false)
function resetReviewPaging() {
  reviewPage.value = 1
  reviewFinished.value = false
  reviewLoadingMore.value = false
}
async function onReviewsReachBottom() {
  if (!dish.value || reviewLoadingMore.value || reviewFinished.value) return
  const pageSize = 10
  reviewLoadingMore.value = true
  try {
    const res = await dishStore.fetchReviews(dishId.value, {
      sort: 'latest',
      isWithImage: false,
      page: reviewPage.value + 1,
      pageSize,
      append: true,
    })
    reviewPage.value += 1
    if ((res?.list || []).length < pageSize) reviewFinished.value = true
  } catch { /* 底部加载失败静默，后续滚动可重试 */ } finally { reviewLoadingMore.value = false }
}

// N07 修复：删除后延迟返回定时器句柄，离开页面时清理
let navTimer: ReturnType<typeof setTimeout> | null = null
onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  navTimer = null
})

/** 大图列表：优先 images，回退单图 */
const heroImages = computed(() => {
  const d = dish.value
  if (!d) return []
  return (d.images && d.images.length > 0) ? d.images : [d.image]
})

/* ===== dish-detail-visual-polish：覆盖导航 + 滚动渐显菜名 ===== */
const statusBarHeight = ref(20)
const navBarHeight = ref(56)
const scrollTop = ref(0)
/** 右上角原生胶囊避让：与 AppHeader 同款计算（screenW − menuBtn.left + 8px），仅微信端生效 */
const rightPad = ref(0)
const topPad = computed(() => `max(${statusBarHeight.value}px, env(safe-area-inset-top))`)
/** 导航行右侧安全留白：避让微信胶囊，长菜名省略于胶囊左侧 */
const navPadRight = computed(() =>
  rightPad.value > 0 ? `calc(env(safe-area-inset-right, 0px) + ${rightPad.value}px)` : '0px',
)
/** 视口尺寸（px，onMounted 取真值；缺省兜底） */
const windowHeight = ref(800)
const windowWidth = ref(375)

/* ===== 顶部大图（dish-hero-scroll-model + fix：两阶段定格 = 页面滚动 + CSS sticky） =====
   大图仍是页面内容流首块（.hero-slot）并设 position:sticky、top:-pinStart：
   - 阶段 A：滚动量 ≤ pinStart，大图随页面自然上移，其底边与下方卡片顶边始终重合、承接区无空窗；
   - 阶段 B：滚动量 > pinStart，由滚动引擎把大图钉在 top:-pinStart（底边恰落承接线 pinLine），
     此后仅下方卡片继续上滑；反向回滚自动脱离并恢复满高贴顶。全程由原生 sticky 承担，
     不逐帧改写 transform → 无实时计算造成的偶发闪帧。
   - 承接：大图自身持续盖住顶部（无空窗），定格后 .hero-carry 在 pinLine 高平滑淡入 --bg-card 实底；
     菜品不存在（数据为空）时顶部承接强制实底，返回始终可用。 */
/** AppHeader 底部留白 --spacing-sm（16rpx）折算 px：承接线口径含它，与其它二级页 AppHeader 底边对齐 */
const spacingSmPx = ref(8)
/** 承接线 = 状态栏 + 导航行 + AppHeader 底部留白（与其他二级页 AppHeader 底边同高的水平线） */
const pinLine = computed(() => statusBarHeight.value + navBarHeight.value + spacingSmPx.value)
/** 大图满高：≈ 屏高 1/4（沿用 26vh 口径，px 与滚动量同单位）；恒大于承接线，保证有定格区间 */
const heroBase = computed(() => Math.max(Math.round(windowHeight.value * 0.26), pinLine.value + 20))
/** 大图底边到达承接线所需滚动量 = 满高 − 承接线（sticky top 取 -pinStart，阶段 A/B 分界） */
const pinStart = computed(() => Math.max(heroBase.value - pinLine.value, 1))
/** 内容块最小高度（px）：即使菜品内容不足一屏，也让页面可滚动量 ≥ pinStart，
 *  保证"无论如何"都能把顶部大图滑到 header 定格位（内容较多时该下限自动失效）。 */
const dishBodyMin = computed(() => Math.max(0, windowHeight.value + pinStart.value - heroBase.value))
/** 承接条淡入位移：越过承接线后 48px 内淡入完成（先于标题渐显，避免浅条下出现标题） */
const CARRY_FADE_PX = 48
/** 承接条不透明度：大图定格后 .hero-carry 在 pinLine 高淡入实底（连续映射，无阶跃/空窗）；dish==null 由固定条承接 */
const carryOpacity = computed(() => {
  if (dish.value == null) return 1
  const overscroll = scrollTop.value - pinStart.value
  return Math.min(1, Math.max(0, overscroll / CARRY_FADE_PX))
})
/** 实底态：驱动返回图标色（实底黑 / 图片态白）与 .dish-nav-back::before 深色圆底显隐 */
const navSolid = computed(() => dish.value == null || carryOpacity.value >= 1)

/* ===== D1e 标题渐显公式：卡片菜名滚出导航条下沿后才渐显，避免同屏两份菜名 ===== */
/** ≈ 信息卡上内边距(32rpx≈16px) + 菜名行高，按 8 基网格就近取整；
 *  若真机仍有短瞬同屏，此常量为唯一调节点（只增不减）。 */
const NAME_EXIT_SLACK = 40
/** 沿用既有 96px 淡入区间 */
const TITLE_FADE_SPAN = 96
/** 渐显起点：大图底边定格于承接线后，信息卡菜名滚出承接线才渐显 */
const titleFadeStart = computed(() => pinStart.value + NAME_EXIT_SLACK)
const navOpacity = computed(() => {
  if (dish.value == null) return 1
  const p = (scrollTop.value - titleFadeStart.value) / TITLE_FADE_SPAN
  return Math.min(1, Math.max(0, p))
})
const dishName = computed(() => (dish.value ? dish.value.name : '菜品详情'))
/** 页面级滚动同步（原内层 scroll-view @scroll 移除）：只驱动承接条/标题的 opacity，不再参与布局/位移 */
onPageScroll((e: any) => {
  scrollTop.value = e?.scrollTop || 0
})
/** 页面滚动到底（原 scroll-view @scrolltolower）：评价触底加载下一页 */
onReachBottom(() => {
  onReviewsReachBottom()
})
onMounted(() => {
  // 与 AppHeader 同款导航尺寸计算（自定义导航 + 右上角胶囊避让）
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const w: any = (globalThis as any).wx
  const win = w ? (w.getWindowInfo ? w.getWindowInfo() : (w.getSystemInfoSync ? w.getSystemInfoSync() : null)) : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarHeight.value = sb
  // dish-hero-scroll-model：大图满高取视口高度 26%（px），与滚动量同单位
  windowHeight.value = (win && win.windowHeight) || 800
  windowWidth.value = (win && win.windowWidth) || 375
  // --spacing-sm（16rpx）折算 px：承接线需与其它页 AppHeader 底部留白对齐
  spacingSmPx.value = (16 * windowWidth.value) / 750
  const mb = w && w.getMenuButtonBoundingClientRect ? w.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = getNavBarHeight(sb, mb)
    // 胶囊避让：screenW − 胶囊.left + 8px（px，不随屏宽缩放），与 AppHeader 一致
    const screenW = (win && win.windowWidth) || 375
    rightPad.value = Math.max(screenW - mb.left + 8, 0)
  }
})

/** 位置文案：食堂 › 楼层 › 档口 › 窗口 */
const locationText = computed(() => {
  const d = dish.value
  if (!d) return ''
  const nodes: string[] = []
  if (d.canteen) nodes.push(d.canteen)
  if (d.floor) nodes.push(String(d.floor))
  if (d.stallName) nodes.push(d.stallName)
  if (d.windowNo) nodes.push(`窗口 ${d.windowNo}`)
  return nodes.join(' › ') || '未知位置'
})

/** 距你距离（米）：前端本地计算；未定位为 null */
const dishDistance = computed(() => {
  const d = dish.value
  if (!d) return null
  const loc = locationStore.location
  if (!loc || typeof d.latitude !== 'number' || typeof d.longitude !== 'number') return null
  return haversineMeters(loc, { lat: d.latitude, lng: d.longitude })
})

/** 距你文案 */
const distText = computed(() => {
  const m = dishDistance.value
  if (m == null) return '未定位'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`
})

/** 评分分布：按星级 5→1 排序（供综合评分卡） */
const ratingDistribution = computed(() => {
  const list = (dish.value?.ratingDistribution || []).slice()
  list.sort((a, b) => b.star - a.star)
  return list
})

onLoad((query) => {
  const id = Number(query?.id)
  if (!id) {
    uni.showToast({ title: '缺少菜品ID', icon: 'none' })
    return
  }
  dishId.value = id
  dishStore.resetDishDetail()
  ensureLocation()
  loadDishData()
})

/** 从二级页返回时：脏标记置位才重拉（评价列表 + 综合评分） */
onShow(() => {
  if (!dishId.value || !dish.value) return
  if (dishStore.reviewsDirty) {
    dishStore.reviewsDirty = false
    resetReviewPaging()
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 })
    dishStore.fetchDetail(dishId.value)
  }
})

/** 确保拿到用户坐标（会话级缓存，避免重复授权）；失败静默降级 */
async function ensureLocation() {
  if (locationStore.location) return
  try {
    const loc = await getUserLocation()
    if (loc) locationStore.setLocation(loc)
  } catch (e) {
    // 静默
  }
}

/** 进入页面加载详情（addView 埋点失败静默） */
async function loadDishData() {
  if (!dishId.value) return
  resetReviewPaging()
  addView(dishId.value)
  await Promise.all([
    dishStore.fetchDetail(dishId.value),
    dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 }),
  ])
  const d = dish.value
  if (d) {
    sharedDish.value = {
      id: d.id,
      name: d.name,
      price: d.price,
      stallId: d.stallId,
      canteen: d.canteen,
      stallName: d.stallName,
    }
  } else {
    sharedDish.value = null
  }
}

onShareAppMessage(() => ({
  title: dish.value ? `${dish.value.name} ¥${dish.value.price}` : '菜品详情',
  path: `/pages/dish/index?id=${dishId.value}`,
}))

/** 距你未定位时点击：主动引导开启定位 */
async function onDistTap() {
  if (dishDistance.value != null) return
  const before = locationStore.location
  await ensureLocation()
  if (!before && locationStore.location) {
    uni.showToast({ title: '已开启定位', icon: 'none' })
  } else if (!locationStore.location) {
    uni.showToast({ title: '定位未开启', icon: 'none' })
  }
}

/** 删除本人菜品（长按菜名触发） */
function onDishLongPress() {
  const d = dish.value
  if (!d) return
  if (!userStore.userInfo || (d.createdBy != null && d.createdBy !== userStore.userInfo.id)) return
  uni.showModal({
    title: '删除菜品',
    content: '确定删除你发布的这道菜品吗？删除后不可恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteDish(d.id)
          uni.showToast({ title: '已删除', icon: 'none' })
          if (navTimer) clearTimeout(navTimer)
          navTimer = setTimeout(() => uni.navigateBack(), 600)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

/** 删除本人评价：成功后重拉列表 + 刷新综合评分 */
function onDeleteReview(rv: Review) {
  if (!userStore.requireAuth(() => onDeleteReview(rv))) return
  if (userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: '#FF3B30',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(rv.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        await dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 })
        resetReviewPaging()
        dishStore.fetchDetail(dishId.value)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/* ===== 写评价（底栏左钮 → ReviewComposer 底部抽屉；提交成功后重拉评价 + 综合评分） ===== */
const composerOpen = ref(false)
function openComposer() {
  composerOpen.value = true
}
function onOpenReviewComposer() {
  if (!dish.value) return
  // 与删除/举报同款：未认证先弹认证（AuthSheet），认证完成后回调重进本函数
  if (!userStore.requireAuth(() => onOpenReviewComposer())) return
  openComposer()
}
/** 提交成功：重置分页并重拉最新评价（sort=latest 新评置顶）+ 刷新综合评分分布 */
function onReviewSubmitted() {
  resetReviewPaging()
  dishStore.fetchReviews(dishId.value, { sort: 'latest', isWithImage: false, pageSize: 10 })
  dishStore.fetchDetail(dishId.value)
}

/* ===== 评价三点菜单（ReviewItem @more → 页面级通用 ActionSheet） ===== */
const reviewMoreOpen = ref(false)
const reviewMoreTarget = ref<Review | null>(null)
const reviewMoreIsOwn = computed(() => {
  const rv = reviewMoreTarget.value
  return rv != null && userStore.userInfo?.id != null && rv.userId === userStore.userInfo.id
})

function onReviewMore(rv: Review) {
  reviewMoreTarget.value = rv
  reviewMoreOpen.value = true
}

/** 动作项：本人删除 / 他人举报（危险操作警示色） */
const reviewMoreItems = computed(() => {
  if (!reviewMoreTarget.value) return []
  return reviewMoreIsOwn.value
    ? [{ key: 'delete', label: '删除评价', icon: 'delete', iconColor: 'var(--color-error)', textColor: 'var(--color-error)' }]
    : [{ key: 'report', label: '举报评价', icon: 'report', iconColor: 'var(--color-error)', textColor: 'var(--color-error)' }]
})

function onReviewMoreSelect(key: string) {
  const rv = reviewMoreTarget.value
  if (!rv) return
  if (key === 'delete') onDeleteReview(rv)
  else if (key === 'report') onReviewReport(rv)
}

/* ===== 评价举报（收敛到 useReport hook） ===== */
const { reportOpen, reportSubmitting, openReport, submitReport } =
  useReport({ type: 'review', title: '举报评价', placeholder: '请描述举报原因…' })

function onReviewReport(rv: Review) {
  openReport(rv.id)
}
</script>

<style scoped>
/* 页面级滚动（fix）：根节点不设固定高度、不放内层 scroll-view——内容未溢出剩余区域时页面不产生滚动区；
   内容溢出时由微信原生页面滚动承接（配合下方 hero 的 position:sticky）。
   底部只预留操作栏高度，防止固定操作栏遮挡最后内容。 */
.dish-page { min-height: 100vh; background: var(--bg-page); padding-bottom: calc(160rpx + env(safe-area-inset-bottom)); }

/* ===== dish-detail-visual-polish：覆盖导航 ===== */
/* 覆盖导航层全程透明、不自持实底/描边/阴影——导航区背景：有图态由大图本身覆盖承接区（无空窗），
   定格后由内容流内 .hero-carry 实底承接；dish 缺失态由 .no-dish-bar 固定实底承接。
   返回钮使用恒定高对比圆形浮层（见 .dish-nav-back::before），不随背景切换而“隐身”。 */
.dish-nav {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  z-index: 80;
}
/* 顶部渐变 scrim 已移除（方案 C→常驻固定 hero header 替代） */
.dish-nav-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: var(--nav-h);
}
.dish-nav-back {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
/* 返回钮：底色对齐微信右上角自带胶囊（中性浅灰透底 + #1A1A1A 黑箭头、细边），
   icon 依赖显式 import 的 IconSvg 渲染（此前未 import 导致页面全部图标不可见） */
.dish-back-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.08);
  box-shadow: inset 0 0 0 1rpx rgba(0, 0, 0, 0.1);
}
.dish-back-icon { line-height: 1; }
.dish-nav-title {
  position: absolute;
  /* 渐渐显现的标题：以视口水平居中（原生导航标题一致），非在 [返回钮, 胶囊] 之间偏移居中；
     长菜名由 max-width + 省略号收口，避免伸入返回钮 / 右侧胶囊区 */
  left: 50%;
  transform: translateX(-50%);
  max-width: 60%;
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: opacity var(--duration-base) var(--ease-out);
}

/* 空态/加载/不存在承接条：固定于屏幕顶端、高 pinLine（内联）、实底 --bg-card，保证返回钮可读可用；
   pointer-events:none 不拦手势；底部圆角与卡片一致（16px）。 */
.no-dish-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 70;
  width: 100%;
  background: var(--bg-card);
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
  pointer-events: none;
}
/* 大图容器：内容流首块 + position:sticky（top 由内联 -pinStart 动态给定）。
   滚动越过 pinStart 后由滚动引擎把大图钉在 top:-pinStart（其底边恰落承接线 pinLine），
   原生接管“定格”，不逐帧改写 transform → 消除实时计算导致的偶发闪帧；
   此后仅下方卡片继续上滑并被 z-index:2 的大图盖于其上。
   底部圆角全程恒定由 overflow 裁切，任何态不丢圆角。 */
.hero-slot {
  position: sticky;
  z-index: 2;
  width: 100%;
  overflow: hidden;
  line-height: 0;
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
}
/* 承接条：锚于大图容器底部 pinLine 高，定格后按 carryOpacity 连续淡入 --bg-card 实底（无空窗/阶跃）；
   opacity 由滚动量驱动，非 CSS transition */
.hero-carry {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-card);
  opacity: 0;
  pointer-events: none;
}

/* 底部固定操作栏：左写评价（主色实底）+ 右分享给同学（白底主色描边次按钮），等宽双按钮，与全局主按钮同高/圆角/字重 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
/* 按钮基线（圆角 12px=24rpx 就近落地、600 字重、88rpx 高；图标 + 文字同行居中） */
.bar-btn { flex: 1; min-width: 0; height: 88rpx; display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); border-radius: 24rpx; border: none; padding: 0; line-height: 1; -webkit-tap-highlight-color: transparent; }
.bar-btn::after { border: none; }
.bar-btn-icon { flex-shrink: 0; }
.bar-btn-text { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 写评价 = 主操作（主色实底 + 白字 + 极淡下投影） */
.bar-btn--write { background: var(--color-primary); box-shadow: var(--shadow-float); }
.bar-btn--write .bar-btn-text { color: var(--color-on-primary); }
/* 分享 = 次操作（白底 + 主色细边/文字，弱于实底主钮） */
.bar-btn--share { background: var(--bg-card); border: 2rpx solid var(--color-primary); }
.bar-btn--share .bar-btn-text { color: var(--color-primary); }
</style>
