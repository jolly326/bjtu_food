<template>
  <Pressable class="moment-card" :aria-label="ariaLabel" @tap="goDetail">
    <!-- 发布者 -->
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
      <!-- 审核态徽标（仅作者本人可见，我的动态页） -->
      <view v-if="showAudit && moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
        <text class="m-audit-text">{{ auditLabel }}</text>
      </view>
      <!-- 右上角三点菜单：分享 / 举报 收进页面级 ActionSheet（去胶囊化，图标按钮；
           仅触发 emit，弹层由父页面在 scroll-view 外渲染，避免 fixed 遮罩层级被压扁） -->
      <view class="m-more" role="button" aria-label="更多操作" @tap.stop="emit('more', props.moment)">
        <IconSvg name="more-v" :size="36" color="var(--text-tertiary)" />
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

    <!-- 退回原因（作者本人可见） -->
    <view v-if="moment.auditStatus === 'rejected' && moment.rejectReason" class="m-reject">
      <text class="m-reject-text">已退回：{{ moment.rejectReason }}</text>
    </view>

    <!-- 关联对象 chip + 互动栏（同一行，互动靠右） -->
    <view class="m-foot">
      <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
        <image v-if="moment.relatedImage" class="m-related-thumb" :src="getImageUrl(moment.relatedImage)" mode="aspectFill" lazy-load />
        <view v-else class="m-related-thumb m-related-thumb--empty">
          <IconSvg name="dish" :size="26" color="var(--color-primary)" />
        </view>
        <text class="m-related-text">{{ relatedLabel }}</text>
      </view>
      <view class="m-actions">
        <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
          <IconSvg name="thumb" :size="30" class="m-action-icon" :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'" />
          <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
        </view>
        <view class="m-action" @tap.stop="goDetail">
          <IconSvg name="comment" :size="30" color="var(--text-secondary)" class="m-action-icon" />
          <text class="m-action-count">{{ moment.commentCount > 0 ? moment.commentCount : 0 }}</text>
        </view>
      </view>
    </view>
  </Pressable>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import IconSvg from './IconSvg.vue'
import Pressable from './Pressable.vue'
import { formatDateTime } from '@/utils/time'
import { previewImages, getImageUrl, getThumbUrl } from '@/utils/image'
import type { Moment } from '@/types/moment'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'

const props = withDefaults(defineProps<{
  moment: Moment
  /** 是否展示审核态徽标（我的动态页） */
  showAudit?: boolean
}>(), {
  showAudit: false,
})

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

/** 卡片无障碍语义标签（global-ui-polish / ui-press-system） */
const ariaLabel = computed(() => `${(props.moment.userNickname || '匿名用户')}的动态`)
/** 图片淡入：记录已加载下标，配合 .m-image.loaded 做 opacity 过渡（B.5） */
const loadedSet = reactive(new Set<number>())

// 正文展开态（点1：超长折叠，粗判长度显示展开入口）
const expanded = ref(false)
const needClamp = computed(() => (props.moment.content?.length || 0) > 80)

const relatedLabel = computed(() => {
  const prefix = props.moment.relatedType === 'dish' ? '菜品' : props.moment.relatedType === 'stall' ? '档口' : ''
  return `${prefix}·${props.moment.relatedName || ''}`
})

const auditLabel = computed(() => {
  if (props.moment.auditStatus === 'pending') return '审核中'
  if (props.moment.auditStatus === 'rejected') return '已退回'
  return ''
})
const auditClass = computed(() => `audit-${props.moment.auditStatus}`)

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
  box-shadow: var(--shadow-card);
  padding: var(--spacing-md);
  /* Apple highlight 按压：背景微变而非整卡缩放（与 find 混合卡一致） */
  transition: background-color var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.moment-card.pressed { background-color: var(--bg-soft); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 圆角正方形头像：用明确 rpx（16rpx），不用 var(--radius-card)=16px（在 64rpx 头像上接近圆形） */
.m-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-xs); background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: var(--font-subtitle); line-height: 1; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
/* Apple Design Typography：昵称 body-bold（与动态详情页昵称一致） */
.m-nickname {
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 第二行：星星（左）+ 发布时间（小间隙同行，不推右；无星星时仅时间自然排列） */
.m-meta { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-time { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
.m-audit { padding: var(--spacing-2xs) var(--spacing-sm); border-radius: var(--radius-tag); flex-shrink: 0; }
.m-audit-text { font-size: var(--font-aux); font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-sm); font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; word-break: break-word; }
.m-content.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.m-expand { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); align-self: flex-start; }
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
/* 缩略图：圆角正方形（16rpx，与全站缩略图/头像统一） */
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: var(--radius-xs); overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out), transform var(--duration-base) var(--ease-out); }
.m-image.loaded { opacity: 1; }
.m-image-wrap:active .m-image { transform: scale(var(--press-scale)); }
/* 关联 chip：胶囊背景（primary-soft + 主色文字），左侧为圆角正方形菜品缩略图（有图显图、无图显菜品占位图标）——
   与右侧互动区（纯文字链）形成「信息标识 vs 轻量操作」的视觉层级 */
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); height: 64rpx; padding: 4rpx var(--spacing-md) 4rpx 4rpx; background: var(--color-primary-soft); border-radius: var(--radius-tag); flex-shrink: 0; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-related:active { opacity: 0.7; }
/* 圆角正方形菜品缩略图（56rpx + 12rpx 圆角，chip 内上下各留 4rpx） */
.m-related-thumb { width: 56rpx; height: 56rpx; border-radius: var(--radius-xs); background: var(--bg-page); flex-shrink: 0; }
.m-related-thumb--empty { display: flex; align-items: center; justify-content: center; background: var(--color-primary-soft); }
.m-related-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
.m-reject { margin-top: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-tag); }
.m-reject-text { font-size: var(--font-aux); color: var(--color-error); line-height: 1.5; }
/* 关联 chip + 互动栏同一行（m-foot），互动靠右 */
.m-foot { display: flex; align-items: center; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.m-actions { display: flex; align-items: center; gap: var(--spacing-xs); margin-left: auto; flex-shrink: 0; }
/* 互动按钮：icon + 数字纯文字链，去胶囊背景（原 bg-soft 胶囊与关联 chip 叠加视觉过重）。
   统一 64rpx 触控高度 + 轻内边距，hover/active 透明度反馈，激活态着 --color-like。
   与 ReviewItem 评价操作区（纯文字链）风格一致，符合 Apple Design 克制层级 */
.m-action { display: inline-flex; align-items: center; justify-content: center; gap: var(--spacing-xs); height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); box-sizing: border-box; transition: opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.m-action:active { opacity: 0.55; transform: scale(var(--press-scale)); }
/* button 重置（微信原生分享按钮）：与其他互动按钮完全同高同间距，仅清除原生样式 */
.m-action.m-action-share { margin: 0; padding: 0 var(--spacing-sm); line-height: 1; font-size: var(--font-small); font-weight: var(--weight-semibold); }
.m-action.m-action-share::after { border: none; }
.m-action-icon { font-size: var(--font-body); line-height: 1; color: var(--text-secondary); }
.m-action.active .m-action-icon { color: var(--color-like); }
.m-action-count { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.m-action.active .m-action-count { color: var(--color-like); }
/* 右上角三点菜单按钮：图标按钮（无胶囊背景），与互动区同高；
   仅触发 emit，弹层由页面级 MomentActionSheet 渲染（scroll-view 外 fixed 层级才正确） */
.m-more { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; flex-shrink: 0; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-more:active { opacity: 0.5; }
</style>
