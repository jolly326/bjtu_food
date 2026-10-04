<template>
  <view class="page my-reviews-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="我的主页" @back="backToHome" />

    <scroll-view class="scroll-wrap v-scroll" scroll-y @scroll="onScroll" @scrolltolower="loadMore">
      <!-- 用户信息卡：主页的身份版面（头像 / 主行 / 副行），内容与排版真源见 docs/client/ui/client-公共组件与形态基线.md §三：
           认证态副行 = 校园邮箱 + 右侧「编辑个人信息」→ 独立个人信息编辑页；
           游客态副行 = 「未完成校园认证」且**不渲染动作位**（编辑身份信息是认证态才具备的能力）。
           ⚠️ 块间距**必须**落在页面自己的节点上 —— mp-weixin 下给自定义组件传的 class 落进**组件宿主节点**
           （宿主非块级盒 ⇒ `margin` 被静默忽略）；故由外层 `.strip-section` 承担块间距，
           卡壳内距归零经 `:deep(.card-section)` 打到根节点（同 `find/index.vue` 的 `discover-card` 落地方式）。 -->
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
      <!-- 评价列表：单张白卡收纳全部评价行，行间 1rpx 分隔线（最上 / 最下无线）。
           虚拟列表：仅渲染可视窗口条目，首尾占位撑起整段高度（列表在身份卡之下的滚动内容中 ⇒ 动态量偏移）。 -->
      <view v-if="list.length" class="review-card">
        <view :style="{ height: topPad + 'px' }" />
        <ReviewItem
          v-for="r in visible"
          :key="r.id"
          class="v-item"
          :review="r"
          mine
          flat
          :dish-name="r.dishName"
          @more="onMore(r)"
        />
        <view :style="{ height: bottomPad + 'px' }" />
      </view>

      <!-- 失败态优先于空态：首屏请求失败 ≠ 无评价，避免网络失败被误读为「暂无评价」 -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <!-- 空态：白卡内垂直居中（`comment` 图标 64rpx + 两级文案），两态均渲染。
           游客 = 认证引导；认证态「删除最后一条」清空 = 轻提示。
           认证态**首次进入**无评价 ⇒ 两态均不命中，保持静默（不渲染标题与空态）。 -->
      <EmptyState
        v-else-if="isGuest || emptiedByDelete"
        card
        icon="comment"
        :icon-size="64"
        title="暂无评价"
        :desc="emptyDesc"
      />
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
 * - 空态两态：游客 = 认证引导；认证态「删除清空」= 轻提示；认证态首次进入静默
 * - 空态两态：游客 = 认证引导；认证态「删除清空」= 轻提示；认证态首次进入静默
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
import { listMyReviews, deleteReview } from '@/api/review'
import { isResourceNotFound } from '@/api/errors'
import type { Review, MyReview } from '@/types/review'
import { backToHome } from '@/utils/back'
import { PATH } from '@/utils/routes'
import { usePagedList, useVirtualList } from '@/composables/usePagedList'
import { MAX_LIST_PAGES } from '@/constants/paging'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
// 图标色须传实色（IconSvg 的 color 不解析 var()）
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import { CONFIRM_DELETE_REVIEW, REVIEW_GONE_TEXT, TOAST_REVIEW_DELETED } from '@/constants/copy'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userInfo.value?.bindEmail || '')
/** 游客态（列表空态分支 + 跳过需登录请求）；认证成功返回本页时 onShow 重拉自动切换为真实列表 */
const isGuest = computed(() => !userStore.isVerified())
/** 空态说明文案：游客 = 认证引导；认证态（删除清空）= 写评价引导 */
const emptyDesc = computed(() => (isGuest.value ? '完成身份认证后可发表评价' : '去菜品详情写一条吧'))

/** 编辑个人信息：独立页（pages/profile/index），保存后自动返回本页 */
function goProfileEdit() {
  uni.navigateTo({ url: PATH.profile })
}

/** 仅「删除导致列表清空」时为 true，驱动空态轻提示 */
const emptiedByDelete = ref(false)

/**
 * 分页列表（公共 composable，UI 统一 Loop Round 17 抽取）：
 * 第 1 页重拉 / 触底加载更多 / 去重追加 / 失败回退页码 / `loading` 重入守卫 —— 全站一套语义。
 * 本页差异经选项注入：游客跳过（端点需登录，调必 401）、成功后复位「删除导致空列表」标记。
 */
const { list, loading, loadFailed, load, loadMore } = usePagedList<MyReview>({
  fetchPage: async (page, pageSize) => (await listMyReviews({ page, pageSize })).list,
  canLoad: () => userStore.isVerified(),
  maxPages: MAX_LIST_PAGES,
  onLoadSuccess: () => { emptiedByDelete.value = false },
})

/** 虚拟列表（评价卡在身份卡+标题之下 ⇒ 用 `offsetSelector` 动态量偏移）：仅渲染可视窗口，节点数 O(窗口) */
const { onScroll, visible, topPad, bottomPad } = useVirtualList<MyReview>({
  items: list,
  estimateHeight: 220,
  offsetSelector: '.review-card',
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
 * ⚠️ `iconColor` 必须传实色 `COLOR_MAP['error']`（ActionSheet 契约：IconSvg 不解析 var()）；
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

/** 本地移除一条评价；删空时置位空态标记（删除成功与「已不存在」两条路径共用） */
function removeLocal(id: number) {
  list.value = list.value.filter(item => item.id !== id)
  emptiedByDelete.value = list.value.length === 0
}

/**
 * 删除本人评价：二次确认 → 删除 → 本地移除（删空后给轻提示）。
 *
 * **4001（评价不存在）**：该评价已在别处删除 ⇒ 重试无意义，按「已不存在」收尾
 * （本地移除 + 提示），不落到通用「删除失败」误导用户重试。
 */
function onDelete(r: MyReview) {
  uni.showModal({
    ...CONFIRM_DELETE_REVIEW,
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: async (res) => {
      if (!res.confirm) return
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
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-lg) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 评价列表：单张白卡收纳全部评价行（与系统通知页同语言）；行间 1rpx 浅分隔线，最上 / 最下无线 */
.review-card {
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
/* 行内距 + 分隔线（经 :deep 穿透到公共 ReviewItem 根；覆写其 flat 的 padding:0） */
.review-card :deep(.review-item) { padding: var(--spacing-lg); }
.review-card :deep(.review-item + .review-item) { border-top: 1rpx solid var(--border-color); }

/* 区块标题（公共 `SectionTitle`）：边距按 UI 稿取 `--spacing-md`（覆盖其默认 `--spacing-sm`）；
   前缀页面根抬高特异度，不动公共组件 */
.my-reviews-page .section-title { margin-bottom: var(--spacing-md); }
/* ===== 用户信息卡 =====
   ⚠️ mp-weixin 下传给自定义组件的 class 落进**组件宿主节点**，宿主**非块级盒**
   ⇒ 在其上写 `margin` / `padding` / `overflow` 全部**不产生布局效果**。故：
   · 块间距 → 页面自己的外层节点 `.strip-section` 承担；
   · 卡壳内距归零 + 圆角裁切 → 只能经 `:deep` 打到卡壳**根节点**。 */
.strip-section { margin-bottom: var(--spacing-xl); }
.strip-section :deep(.card-section) { padding: 0; overflow: hidden; }
/* 身份版面（条纹 / 头像 / 主副行 / 「编辑个人信息」胶囊）**已抽为公共组件 `IdentityCard`**（基线 §三）：
   本页只保留卡壳的圆角裁切与本段块间距。 */

/* 空态样式由公共组件 EmptyState 承担 */
</style>
