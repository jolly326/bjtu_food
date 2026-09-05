<template>
  <view class="moment-card" :aria-label="ariaLabel" @tap="goDetail" role="button" tabindex="0">
    <view class="m-head">
      <image v-if="moment.userAvatar" class="m-avatar" :src="getImageUrl(moment.userAvatar)" mode="aspectFill" lazy-load />
      <view v-else class="m-avatar m-avatar-empty">
        <IconSvg name="user" :size="40" color="var(--text-tertiary)" class="m-avatar-fallback" />
      </view>
      <view class="m-head-right">
        <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
        <!-- 第二行：发布时间（关联菜品评分视觉已移除，评分归菜品详情/评价区） -->
        <view class="m-meta">
          <text class="m-time">{{ formatDateTime(moment.createdAt) }}</text>
        </view>
      </view>
      <!-- 右上角三点菜单：分享 / 举报 收进页面级 ActionSheet（去胶囊化，图标按钮；
           仅触发 emit，弹层由父页面在 scroll-view 外渲染，避免 fixed 遮罩层级被压扁） -->
      <view class="m-more" role="button" aria-label="更多操作" @tap.stop="emit('more', props.moment)">
        <IconSvg name="more-v" :size="28" color="var(--text-tertiary)" />
      </view>
    </view>

    <!-- 正文 -->
    <text class="m-content" :class="{ clamped: !expanded }">{{ moment.content }}</text>
    <text v-if="needClamp" class="m-expand" @tap.stop="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</text>

    <!-- 图片九宫格 -->
    <view v-if="moment.images.length > 0" class="m-images" :class="`img-${Math.min(moment.images.length, 9)}`">
      <view
        v-for="(img, idx) in moment.images.slice(0, 9)"
        :key="idx"
        class="m-image-wrap"
        @tap.stop="previewImage(idx)"
      >
        <image
          class="m-image"
          :class="{ loaded: loadedSet.has(idx) }"
          :src="getImageUrl(getThumbUrl(img))"
          mode="aspectFill"
          lazy-load
          @load="loadedSet.add(idx)"
        />
      </view>
    </view>

    <!-- 关联对象 chip + 互动栏（同一行，互动靠右） -->
    <view class="m-foot">
      <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
        <image v-if="moment.relatedImage" class="m-related-thumb" :src="getImageUrl(moment.relatedImage)" mode="aspectFill" lazy-load />
        <view v-else class="m-related-thumb m-related-thumb--empty">
          <IconSvg name="dish" :size="20" color="var(--text-tertiary)" />
        </view>
        <text class="m-related-text">{{ relatedLabel }}</text>
      </view>
      <view class="m-actions">
        <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
          <IconSvg :name="usefulActive ? 'thumb-filled' : 'thumb'" :size="36" class="m-action-icon" :color="usefulActive ? 'var(--color-primary)' : 'var(--text-secondary)'" />
          <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
        </view>
        <view class="m-action" @tap.stop="goDetail">
          <IconSvg name="comment" :size="36" color="var(--text-secondary)" class="m-action-icon" />
          <text class="m-action-count">{{ moment.commentCount > 0 ? moment.commentCount : 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { formatDateTime } from '@/utils/time'
import { previewImages, getImageUrl, getThumbUrl } from '@/utils/image'
import type { Moment } from '@/types/moment'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'

const props = defineProps<{
  moment: Moment
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件的 @tap 会被编译为原生 bindtap，emit 参数丢失，点击跳转 id 变 undefined。
// 故进详情用 select 作为自定义事件名。
const emit = defineEmits<{
  (e: 'useful', moment: Moment): void
  (e: 'select', moment: Moment): void
  (e: 'go-related', moment: Moment): void
  (e: 'more', moment: Moment): void
}>()

const userStore = useUserStore()

/** 卡片无障碍语义标签 */
const ariaLabel = computed(() => `${(props.moment.userNickname || '匿名用户')}的动态`)
/** 图片淡入：记录已加载下标，配合 .m-image.loaded 做 opacity 过渡 */
const loadedSet = reactive(new Set<number>())

// 正文展开态（超长折叠，粗判长度显示展开入口）
const expanded = ref(false)
const needClamp = computed(() => (props.moment.content?.length || 0) > 80)

const relatedLabel = computed(() => {
  // 动态仅可关联菜品：直接展示菜品名，不再加「菜品·」前缀
  return props.moment.relatedName || ''
})

// 有用 toggle 本地状态（乐观 UI）：初始与回显均取 moment.useful（api 层已归一当前用户点赞态）
const usefulActive = ref(!!props.moment.useful)
// 列表刷新/详情返回后 moment 对象更新时，同步点赞态回显（避免跨页状态丢失）
watch(() => props.moment.useful, (v) => {
  usefulActive.value = !!v
})

function goDetail() {
  emit('select', props.moment)
}

function goRelated() {
  emit('go-related', props.moment)
}

function previewImage(idx: number) {
  previewImages(props.moment.images, idx)
}

/** pending 锁防连点（P0 防重复请求 / 计数漂移） */
const pendingUseful = ref(false)
async function onUseful() {
  if (!userStore.requireAuth(() => onUseful())) return
  if (pendingUseful.value) return
  pendingUseful.value = true
  const prevActive = usefulActive.value
  const prevCount = props.moment.usefulCount || 0
  usefulActive.value = !prevActive
  props.moment.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleUseful(props.moment.id)
    usefulActive.value = res.useful
    props.moment.usefulCount = res.usefulCount
    props.moment.useful = res.useful
    emit('useful', props.moment)
  } catch {
    usefulActive.value = prevActive
    props.moment.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  } finally {
    pendingUseful.value = false
  }
}
</script>

<style scoped>
.moment-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  /* 表面：白卡 + 品牌淡色柔和投影（tab-pages-visual-unify）——
     页面层级由「浅米灰底 — 白卡 — 内容 — 强调」四层结构承担。
     ⚠️ overflow:hidden 不可移除：微信 WXSS 渲染「border-radius + background」时，
     圆角外侧会残留一圈背景色方角（四角皆有，左侧因贴齐列表边缘最明显，
     表现为「屏幕左侧色块」）。必须由本属性裁掉，否则该渲染残留会暴露。 */
  box-shadow: var(--shadow-card);
  /* moment-list-detail-polish：上下 28rpx(≈14px)、左右 lg(32rpx=16px)，8 基网格收紧口径（D1） */
  padding: 28rpx var(--spacing-lg);
  overflow: hidden;
  -webkit-tap-highlight-color: transparent;
}
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 默认头像：正圆 + 浅底色（头像/圆形图标底统一 50%） */
.m-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-circle); background: var(--bg-soft); flex-shrink: 0; overflow: hidden; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: var(--font-subtitle); line-height: 1; }
/* 昵称-时间行距 4px(=8rpx，--spacing-xs)，moment-list-detail-polish D1 */
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
/* 昵称：一级标题档（32rpx / 600），与菜名 / 菜单主标题同档 */
.m-nickname {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 第二行：发布时间（辅助信息档 24rpx / 400 / 三级文字） */
.m-meta { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 时间 12px(=--font-small 24rpx)/400/三级浅灰，moment-list-detail-polish D1 */
.m-time { flex-shrink: 0; font-size: var(--font-small); font-weight: var(--weight-regular); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
/* 正文：正文档（28rpx / 400），行高约 1.5 提升可读性 */
/* 正文：14px(--font-body) 二级灰、行高 1.45；头部-正文 10px(=20rpx)（moment-list-detail-polish D1） */
.m-content { display: block; margin-top: 20rpx; font-size: var(--font-body); font-weight: var(--weight-regular); color: var(--text-secondary); line-height: 1.45; word-break: break-word; }
.m-content.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.m-expand { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); align-self: flex-start; }
/* 正文→图片 12px(=24rpx md)，moment-list-detail-polish D1 */
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: var(--spacing-md); }
/* 缩略图：圆角正方形（16rpx，与全站缩略图/头像统一） */
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: var(--radius-xs); overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.m-image.loaded { opacity: 1; }
/* 关联 chip：胶囊背景（primary-soft + 主色文字），左侧为圆角正方形菜品缩略图（有图显图、无图显菜品占位图标）——
   与右侧互动区（纯文字链）形成「信息标识 vs 轻量操作」的视觉层级 */
/* ⚠️ overflow:hidden：本 chip 位于卡片底部左侧、带主色浅底，圆角外侧的背景方角残留
   会在列表里表现为「屏幕左侧色块」（每条动态一块），必须裁掉。 */
/* 关联 chip：底色/圆角/内边距与首页 TagLabel 同语言（radius-tag + primary-soft；moment-card-visual-polish D5） */
/* 关联菜品 chip：浅灰 #F5F5F5 底、深灰 #333 文字、中灰图标、矮高、8px(16rpx) 圆角（用户口径） */
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); height: 48rpx; padding: 4rpx var(--spacing-sm) 4rpx 4rpx; background: #F5F5F5; border-radius: var(--radius-tag); flex-shrink: 0; overflow: hidden; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-related:active { opacity: 0.7; }
/* 圆角正方形菜品缩略图（40rpx，chip 内上下各留 4rpx） */
.m-related-thumb { width: 40rpx; height: 40rpx; border-radius: var(--radius-xs); background: #F5F5F5; flex-shrink: 0; overflow: hidden; }
.m-related-thumb--empty { display: flex; align-items: center; justify-content: center; background: #F5F5F5; }
.m-related-text { font-size: var(--font-small); color: #333333; font-weight: var(--weight-medium); line-height: 1.2; }
/* 关联 chip + 互动栏同一行（m-foot），互动靠右 */
/* 正文→标签/互动行 12px(=24rpx md)，moment-list-detail-polish D1 */
.m-foot { display: flex; align-items: center; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
/* 两按钮间距 16px(=32rpx lg)，moment-list-detail-polish D1 */
.m-actions { display: flex; align-items: center; gap: var(--spacing-lg); margin-left: auto; flex-shrink: 0; }
/* 互动按钮：icon + 数字纯文字链，去胶囊背景。
   统一 64rpx 触控高度 + 轻内边距，hover/active 透明度反馈，激活态着 --color-like。
   与 ReviewItem 评价操作区（纯文字链）风格一致，符合 Apple Design 克制层级 */
/* icon-数字 6px(=12rpx)，moment-list-detail-polish D1 */
.m-action { display: inline-flex; align-items: center; justify-content: center; gap: 12rpx; height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); box-sizing: border-box; transition: opacity var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
/* 按压：图标+数字整组降至约 80%（moment-card-visual-polish D5） */
.m-action:active { opacity: 0.8; }
.m-action-icon { font-size: var(--font-caption); line-height: 1; color: var(--text-secondary); }
/* 点赞后：图标与数字同步变主色，反馈更明确 */
.m-action.active .m-action-icon { color: var(--color-primary); }
/* 互动数：500 字重 + 二级灰，弱于昵称（caption 30rpx，moment-card-visual-polish D5） */
.m-action-count { font-size: var(--font-caption); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.m-action.active .m-action-count { color: var(--color-primary); }
/* 右上角三点菜单按钮：图标按钮（无胶囊背景），与互动区同高；
   仅触发 emit，弹层由页面级 MomentActionSheet 渲染（scroll-view 外 fixed 层级才正确） */
.m-more { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; flex-shrink: 0; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-more:active { opacity: 0.5; }
</style>
