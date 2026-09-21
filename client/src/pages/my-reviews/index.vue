<template>
  <view class="page my-reviews-page">
    <Header title="我的评价" @back="backToHome" />

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="list">
        <view v-for="r in list" :key="r.id" class="review-card">
          <view class="card-head">
            <view class="card-head-left">
              <text class="dish-name">{{ r.dishName || '菜品' }}</text>
              <!-- 状态小标（仅余单一语义）：isHidden=true → 「已被隐藏」
                   （管理员隐藏，不再对外展示，仅作者本人可见，避免「评价凭空消失」的误解）
                   注：原微信内容安全检测中间态小标已于 2026-09-15 随「取消人工复核」删除——
                   检测 pass/review 均直接放行、仅 risky 拒绝，端上不存在中间态 -->
              <view v-if="r.isHidden" class="sec-badge">
                <text class="sec-badge-text">已被隐藏</text>
              </view>
            </view>
            <view class="rating">
              <IconSvg name="star-filled" :size="24" :color="COLOR_MAP['star']" />
              <text class="rating-num">{{ (r.rating || 0).toFixed(1) }}</text>
            </view>
          </view>
          <text class="review-content">{{ r.content }}</text>
          <!-- 配图行（≤3 张 COS URL）：等比小方图，点击预览大图；破图兜底 empty 中性占位 -->
          <view v-if="imagesOf(r).length" class="review-images">
            <view v-for="(img, i) in imagesOf(r)" :key="img" class="review-image-cell">
              <view class="review-image-box">
                <image
                  v-if="!isBroken(r.id, i)"
                  class="review-image"
                  :src="getImageUrl(img)"
                  mode="aspectFill"
                  role="img"
                  :aria-label="`评价配图 ${i + 1}`"
                  @tap="onPreviewImage(r, i)"
                  @error="markBroken(r.id, i)"
                />
                <view v-else class="review-image-fallback">
                  <IconSvg name="empty" :size="36" :color="COLOR_MAP['text-tertiary']" />
                </view>
              </view>
            </view>
          </view>
          <view class="card-foot">
            <text class="review-time">{{ formatDateTime(r.createTime) }}</text>
            <text
              class="delete-link"
              role="button"
              aria-label="删除评价"
              @tap.stop="onDelete(r)"
            >删除</text>
          </view>
        </view>
      </view>

      <!-- 加载失败重试块（MP-012 同族，P3-03 上提为公共组件）：首屏请求失败 ≠ 无评价——
           先于空态渲染，避免网络失败被误读；恢复走重试块 @tap 或下拉刷新 -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <!-- 空态：首次进入无评价保持静默；仅「删除最后一条」触发时给轻提示，避免被误解为加载异常（见 my-reviews） -->
      <view v-else-if="emptiedByDelete" class="empty-tip">
        <text class="empty-text">暂无评价，去菜品详情写一条吧</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 我的评价：评价类 UGC 的用户侧自管理入口（列表 + 本人删除）。
 * - 数据源 GET /my/reviews（后端联表返回 dishName），删除复用 DELETE /reviews/{id}
 * - 状态小标（§7.14）：作者本人可见自己的全部评价，isHidden=true 标「已被隐藏」，
 *   避免「评价凭空消失」；排序由后端默认控制（不传 sort）
 * - 空态双口径：首次进入静默；删除导致清空时给轻提示（见 spec my-reviews）
 * - 失败态（MP-012）：首屏/刷新失败渲染「加载失败 · 点击重试」块，与静默空态区分；
 *   分页失败保持静默，可再触底重试
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { getMyReviews, deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { formatDateTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import { backToHome } from '@/utils/nav'
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const list = ref<Review[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)
/** 仅「删除导致列表清空」时为 true，驱动空态轻提示 */
const emptiedByDelete = ref(false)
/** 首屏/下拉刷新是否失败（MP-012）：失败 ≠ 无评价，失败渲染重试块而非空态 */
const loadFailed = ref(false)
let page = 1
const pageSize = 20
const finished = ref(false)

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getMyReviews({ page: 1, pageSize })
    // 成功即清失败态（重试成功后错误块消失）
    loadFailed.value = false
    list.value = res.list
    page = 1
    finished.value = res.list.length < pageSize
    emptiedByDelete.value = false
  } catch (err) {
    // MP-012：首屏失败不再静默吞——置 loadFailed 渲染「加载失败 · 点击重试」块，恢复走重试块或下拉刷新
    console.error('[my-reviews] 加载评价失败', err)
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 重试块 @tap：从第 1 页重拉（与下拉刷新同路径，仅无下拉动画）（MP-012） */
function onRetryLoad() {
  load()
}

async function loadMore() {
  if (finished.value || loading.value) return
  loading.value = true
  try {
    page += 1
    const res = await getMyReviews({ page, pageSize })
    const existIds = new Set(list.value.map(r => r.id))
    list.value = list.value.concat(res.list.filter(r => !existIds.has(r.id)))
    if (res.list.length < pageSize) finished.value = true
  } catch {
    page -= 1
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refresherTriggered.value = true
  await load()
  refresherTriggered.value = false
}

/* ===== 配图展示（2026-09 恢复 UGC 配图）：≤3 张 COS URL，点击预览大图 ===== */
function imagesOf(r: Review): string[] {
  return Array.isArray(r.images) ? r.images.filter(Boolean) : []
}
/** 破图下标记录（按评价 id 分桶）：error 后切 empty 中性占位；重拉列表后按 id 天然重置 */
const brokenMap = ref<Record<string, Set<number>>>({})
function isBroken(id: number, i: number): boolean {
  return brokenMap.value[String(id)]?.has(i) ?? false
}
function markBroken(id: number, i: number) {
  const key = String(id)
  const next = new Set(brokenMap.value[key] || [])
  next.add(i)
  brokenMap.value = { ...brokenMap.value, [key]: next }
}
/** 预览大图（current 定位到点击那张；破图不计入预览列表） */
function onPreviewImage(r: Review, i: number) {
  const imgs = imagesOf(r)
  const okIdx = imgs.map((_, idx) => idx).filter((idx) => !isBroken(r.id, idx))
  const okUrls = okIdx.map((idx) => getImageUrl(imgs[idx]))
  if (!okUrls.length) return
  const cur = okIdx.indexOf(i)
  uni.previewImage({ urls: okUrls, current: okUrls[Math.max(cur, 0)] })
}

/** 删除本人评价：二次确认 → 删除 → 列表移除（删空后给轻提示） */
function onDelete(r: Review) {
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(r.id)
        list.value = list.value.filter(item => item.id !== r.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        emptiedByDelete.value = list.value.length === 0
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/**
 * onShow 重拉闸门（MP-07）：首次进入必拉；之后 30s 内返回本页不再全量重拉第 1 页、
 * 也不重置分页（本页无跨页写操作入口，删除已在本地移除条目）。
 * 下拉刷新与失败重试块不经过闸门（用户显式意图 → 直接 load）。
 */
const { refreshOnShow } = useOnShowRefresh(load)

onShow(() => {
  refreshOnShow()
})
</script>

<style scoped>
.my-reviews-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.review-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}

.card-head { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.card-head-left { display: flex; align-items: center; gap: var(--spacing-xs); flex: 1; min-width: 0; }
.dish-name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 「已被隐藏」（isHidden，§7.14）唯一状态小标：中性灰胶囊——隐藏是事后处置终态而非警示，
   语气客观不指责。（原本另有 warning 浅底的微信内容安全检测中间态小标，随 2026-09-15「取消人工复核」删除，
   其 --warning 变体样式同步收敛，不留死样式） */
.sec-badge {
  flex-shrink: 0;
  padding: 2rpx var(--spacing-xs);
  border-radius: var(--radius-pill);
  background: var(--bg-placeholder);
}
.sec-badge-text { font-size: var(--font-tiny); color: var(--text-secondary); line-height: 1.4; }
.rating { display: inline-flex; align-items: center; gap: var(--spacing-2xs); flex-shrink: 0; }
.rating-num { font-size: var(--font-small); color: var(--text-secondary); }

.review-content {
  font-size: var(--font-small);
  color: var(--text-primary);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

/* 配图行：与 ReviewItem/ImagePicker 同一网格语言（3 等分 + 16rpx gap + 等比盒） */
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: var(--spacing-2xs);
}
.review-image-cell {
  width: calc((100% - 32rpx) / 3);
}
.review-image-box {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-placeholder);
  -webkit-tap-highlight-color: transparent;
}
.review-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.review-image:active { opacity: 0.6; }
.review-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.review-time { font-size: var(--font-tiny); color: var(--text-tertiary); }
/* 负向操作弱化：右下角小文字链，不占大按钮位。
   QA-03 修复：视觉保持轻量，命中区经 ::after 透明覆盖扩至 ≥88rpx（Apple 44pt 触达下限）。 */
.delete-link {
  position: relative;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  padding: var(--spacing-2xs) var(--spacing-xs);
}
.delete-link::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-lg);
}
.empty-text { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }

/* 失败态块已上提为公共组件 components/RetryBlock.vue（P3-03），样式随之收敛，此处不再保留副本 */
</style>
