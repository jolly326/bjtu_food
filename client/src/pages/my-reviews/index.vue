<template>
  <view class="page my-reviews-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="我的主页" @back="backToHome" />

    <scroll-view class="scroll-wrap v-scroll" scroll-y @scroll="onScroll" @scrolltolower="loadMore">
      <!-- 用户信息卡：主页的身份版面（头像 / 主行 / 副行），内容与排版真源见 docs/ui/client/公共组件与形态基线.md §三：
           认证态副行 = 校园邮箱 + 右侧「编辑个人信息」→ 独立个人信息编辑页；
           游客态副行 = 「未完成校园认证」且**不渲染动作位**（编辑身份信息是认证态才具备的能力）。
           ⚠️ 块间距**必须**落在页面自己的节点上 —— mp-weixin 下给自定义组件传的 class 落进**组件宿主节点**
           （宿主非块级盒 ⇒ `margin` 被静默忽略）；故由外层 `.strip-section` 承担块间距，
           卡壳内距归零经 `:deep(.card-section)` 打到根节点。 -->
      <view class="strip-section">
        <CardSection flush>
          <!-- 身份版面（条纹 / 头像 / 主副行 / 「编辑个人信息」胶囊）全部由公共组件承担 -->
          <IdentityCard
            mode="edit"
            :nickname="userInfo?.nickname"
            :bind-email="bindEmail"
            :avatar="userInfo?.avatar"
            :verified="isVerified"
            @edit="goProfileEdit"
          />
        </CardSection>
      </view>

      <!-- 评价区：区块标题「我的评价」与列表卡**仅在列表有数据时渲染**（空榜不渲染标题） -->
      <SectionTitle v-if="list.length" title="我的评价" />
      <!-- 评价列表：单张模块收纳全部评价行，行间 1rpx 分隔线（最上 / 最下无线）。
           虚拟列表：仅渲染可视窗口条目，首尾占位撑起整段高度（列表在身份卡之下的滚动内容中 ⇒ 动态量偏移）。 -->
      <CardSection v-if="list.length" bare flush class="review-card">
        <view :style="{ height: topPad + 'px' }" />
        <ReviewItem
          v-for="r in visible"
          :key="r.id"
          class="v-item"
          :review="r"
          mine
          flat
          row-padding="lg"
          divided
          :dish-name="r.dishName"
          @more="onMore(r)"
        />
        <view :style="{ height: bottomPad + 'px' }" />
      </CardSection>

      <!-- 三态判断序固定 **失败 > 在途 > 空**（有错不显示空）：
           ① 失败 —— 首屏请求失败 ≠ 无评价，先于在途与空态渲染，避免网络失败被误读为「暂无评价」；
           ② 在途 —— 只给文字行（全局 `.list-foot`），不给骨架屏（禁的是伪内容与抖动，不是文字）；
           ③ 空 —— 零数据**必须显式**（禁静默）：模块内垂直居中（`comment` 图标 64rpx + 两级文案 + CTA）。
              游客 = 认证引导（CTA「去认证」）；认证态 = 写评价引导（CTA「去找一道菜」→ 切首页 tab）。
              两分支共用同一空态件，CTAs 均走既有路径常量。 -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <view v-else-if="loading && !list.length" class="list-foot">
        <text class="list-foot-text">加载中…</text>
      </view>
      <EmptyState
        v-else-if="!loading && !list.length"
        card
        icon="comment"
        :icon-size="64"
        title="暂无评价"
        :desc="emptyDesc"
        :action-text="isGuest ? '去认证' : '去找一道菜'"
        @action="onEmptyAction"
      />

      <!-- 触底反馈（基线 §1.17「列表底部反馈」）：列表有数据时在列表末尾给出
           「加载更多在途」/「到底」两态 —— 触底加载与封口不再静默无信号。 -->
      <view v-if="loading && list.length" class="list-foot">
        <text class="list-foot-text">正在加载更多…</text>
      </view>
      <view v-else-if="finished && list.length" class="list-foot">
        <text class="list-foot-text">没有更多了</text>
      </view>
    </scroll-view>

    <!-- 三点菜单（与菜品详情评价区同款交互）：「删除评价」危险红动作项 -->
    <ActionSheet :open="moreOpen" :items="moreItems" @close="moreOpen = false" @select="onSelect" />
  </view>
</template>

<script setup lang="ts">
/**
 * 我的主页：个人信息卡 + 名下评价列表的**复合页**（「我的」页用户卡点击进入）。
 * - 「编辑个人信息」入口**仅认证态渲染**；评价卡复用公共 ReviewItem，本人视角经 dishName prop 注入
 * - 数据源 GET /my/reviews，删除入口 = 卡片右上角三点 → ActionSheet → 二次确认（与详情页同链路）
 * - 空态（显式、禁静默）一态两分支：游客 = 认证引导（CTA「去认证」）；
 *   认证态 = 写评价引导（CTA「去找一道菜」→ 切首页 tab）—— 从未发表与「删除最后一条后清空」同为此态
 * - 失败态：首屏失败渲染「加载失败 · 点击重试」块；分页失败保持静默，可再触底重试
 */
import { ref, computed } from 'vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import CardSection from '@/components/CardSection.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import IdentityCard from '@/components/IdentityCard.vue'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import { listMyReviews, deleteReview } from '@/api/review'
import { isResourceNotFound } from '@/api/errors'
import type { Review, MyReview } from '@/types/review'
import { backToHome } from '@/utils/back'
import { PATH } from '@/utils/routes'
import { usePagedList, useVirtualList } from '@/composables/usePagedList'
import { MAX_LIST_PAGES } from '@/constants/paging'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
// 图标色须传实色（AppIcon 的 color 不解析 var()）
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import { CONFIRM_DELETE_REVIEW, REVIEW_GONE_TEXT, TOAST_REVIEW_DELETED } from '@/constants/copy'

const userStore = useUserStore()
const authStore = useAuthStore()
const userInfo = computed(() => userStore.userInfo)
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userInfo.value?.bindEmail || '')
/** 游客态（列表空态分支 + 跳过需登录请求）；认证成功后重进本页（onShow 重拉闸门）自动切换为真实列表 */
const isGuest = computed(() => !userStore.isVerified())
/** 空态说明文案：游客 = 认证引导；认证态 = 写评价引导 */
const emptyDesc = computed(() => (isGuest.value ? '完成身份认证后可发表评价' : '去菜品详情写一条吧'))

/** 编辑个人信息：独立页（pages/profile/index），保存后自动返回本页 */
function goProfileEdit() {
  uni.navigateTo({ url: PATH.profile })
}

/**
 * 空态 CTA（两分支共用一枚上抛事件）：
 * · 游客 → 独立认证页（走 `auth` store 的既有认证入口，与「我的」页认证格同路径）；
 * · 认证态 → 切首页 tab 找菜写评价（主区切换重置页面栈，与底部菜单栏同源）。
 */
function onEmptyAction() {
  if (isGuest.value) {
    authStore.requestAuth()
    return
  }
  uni.reLaunch({ url: PATH.home })
}

/**
 * 分页列表（公共 composable，UI 统一 Loop Round 17 抽取）：
 * 第 1 页重拉 / 触底加载更多 / 去重追加 / 失败回退页码 / `loading` 重入守卫 —— 全站一套语义。
 * 本页差异经选项注入：游客跳过（端点需登录，调必 401）。
 */
const { list, loading, loadFailed, finished, load, loadMore } = usePagedList<MyReview>({
  fetchPage: async (page, pageSize) => (await listMyReviews({ page, pageSize })).list,
  canLoad: () => userStore.isVerified(),
  maxPages: MAX_LIST_PAGES,
})

/** 虚拟列表（评价卡在身份卡+标题之下 ⇒ 用 `offsetSelector` 动态量偏移）：仅渲染可视窗口，节点数 O(窗口) */
const { onScroll, visible, topPad, bottomPad } = useVirtualList<MyReview>({
  items: list,
  estimateHeight: 220,
  offsetSelector: '.card-section',
})

/** 重试块 @tap：从第 1 页重拉（与首屏同一条重拉路径）（MP-012） */
function onRetryLoad() {
  load()
}

/* ===== 三点菜单（ReviewItem @more → 页面级 ActionSheet）：删除本人评价的唯一入口 ===== */
const moreOpen = ref(false)
const moreTarget = ref<MyReview | null>(null)

/** 本页列表恒为本人视角行（含 `dishId`）；公共评价卡按两视角并集上抛，此处收窄回本人视角 */
function onMore(r: Review | MyReview) {
  if (!('dishId' in r)) return
  moreTarget.value = r
  moreOpen.value = true
}

/**
 * 动作项：本人恒为「删除评价」（危险红；本页列表全部为本人评价，无举报项）。
 * ⚠️ `iconColor` 必须传实色 `COLOR_MAP['error']`（ActionSheet 契约：AppIcon 不解析 var()）；
 * `textColor` 走 CSS 绑定、`var()` 合法。
 */
const moreItems = [
  { key: 'delete', label: '删除评价', icon: 'delete', iconColor: COLOR_MAP['error'], textColor: 'var(--color-error)' },
]

function onSelect(key: string) {
  const r = moreTarget.value
  if (!r) return
  if (key === 'delete') onDelete(r)
}

/** 本地移除一条评价（删除成功与「已不存在」两条路径共用）；移除后为空则由空态承担引导 */
function removeLocal(id: number) {
  list.value = list.value.filter(item => item.id !== id)
}

/**
 * 删除本人评价：二次确认 → 删除 → 本地移除。
 *
 * **4001（评价不存在）**：该评价已在别处删除 ⇒ 重试无意义，按「已不存在」收尾
 * （本地移除 + 提示），不落到通用「删除失败」误导用户重试。
 */
/** 删除在途锁（不可逆操作防重复）：非 null 时表示正在删除的评价 id */
const deletingId = ref<number | null>(null)

function onDelete(r: MyReview) {
  uni.showModal({
    ...CONFIRM_DELETE_REVIEW,
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      // 删除不可逆：加并发锁，避免连续点两条时两个确认弹窗回调交错、重复发请求
      if (deletingId.value !== null) return
      deletingId.value = r.id
      try {
        await deleteReview(r.id)
        removeLocal(r.id)
        toastSuccess(TOAST_REVIEW_DELETED)
      } catch (e) {
        if (isResourceNotFound(e)) {
          removeLocal(r.id)
          toastInfo(REVIEW_GONE_TEXT)
          return
        }
        toastError(e, '删除失败')
      } finally {
        deletingId.value = null
      }
    },
  })
}

/**
 * onShow 重拉闸门：首次进入必拉；之后 30s 内返回本页不再全量重拉第 1 页、
 * 也不重置分页（本页无跨页写操作入口，删除已在本地移除条目）。
 */
useOnShowRefresh(load)

/* 触底加载更多由模板 `scroll-view` 的 `@scrolltolower="loadMore"` 触发。
   本页为「顶栏 + `scroll-view`（`flex: 1`）+ 页面 `height: 100vh`」，**页面自身不滚动** ⇒
   页面级 `onReachBottom` 不会触发，分页一律走滚动区事件。 */
</script>

<style scoped lang="scss">
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层。
   结构化收口：页面 = 顶栏 + `scroll-view` 滚动区（`flex: 1`）——
   内容被裁在滚动区内，**不会**从透明的标题带背后经过（与首页 §11 同一结构性原则，零表面）。 */
.my-reviews-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.my-reviews-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-lg) var(--page-gutter) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 评价列表：单张壳收纳全部评价行（壳 = 公共 `CardSection` bare+flush，与系统通知页同语言）；
   底色 / 圆角 / 阴影 / 裁切由壳承担，行内距与分隔线由 `ReviewItem` 的 `row-padding` / `divided` prop 自持 */

/* 区块标题（公共 `SectionTitle`）：边距按 UI 稿取 `--spacing-md`（覆盖其默认 `--spacing-sm`）；
   前缀页面根抬高特异度，不动公共组件 */
.my-reviews-page .section-title { margin-bottom: var(--spacing-md); }
/* ===== 用户信息卡 =====
   ⚠️ mp-weixin 下传给自定义组件的 class 落进**组件宿主节点**，宿主**非块级盒**
   ⇒ 在其上写 `margin` / `padding` / `overflow` 全部**不产生布局效果**。故：
   · 块间距 → 页面自己的外层节点 `.strip-section` 承担；
   · 卡壳内距归零 → 只能经 `:deep` 打到卡壳**根节点**（圆角裁切由卡壳自持，页面不再重复声明）。 */
.strip-section { margin-bottom: var(--spacing-xl); }
.strip-section :deep(.card-section) { padding: 0; }
/* 身份版面（条纹 / 头像 / 主副行 / 「编辑个人信息」胶囊）**已抽为公共组件 `IdentityCard`**（基线 §三）：
   本页只保留本段块间距与卡壳内距归零。 */

/* 空态样式由公共组件 EmptyState 承担 */
</style>
