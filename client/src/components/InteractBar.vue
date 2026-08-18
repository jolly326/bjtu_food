<template>
  <view class="interact-bar">
    <!-- 「有用」：动态核心互动（与列表 MomentCard / 评论 CommentItem 语义一致：thumb 图标 + 计数 + 激活态） -->
    <view
      class="interact-btn"
      :class="{ active: usefulActive }"
      hover-class="pressed"
      hover-stay-time="80"
      role="button"
      aria-label="标记有用"
      @tap="onUseful"
    >
      <view v-if="usefulPending" class="interact-spinner" />
      <IconSvg
        name="thumb"
        :size="28"
        :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'"
        class="interact-icon"
      />
      <text class="interact-count">{{ usefulCount > 0 ? usefulCount : '有用' }}</text>
    </view>
    <view class="interact-btn" hover-class="pressed" hover-stay-time="80" role="button" aria-label="评论" @tap="onComment">
      <IconSvg name="comment" :size="28" color="var(--text-secondary)" class="interact-icon" />
      <text class="interact-count">{{ commentCount > 0 ? commentCount : '评论' }}</text>
    </view>
    <view class="interact-btn report" hover-class="pressed" hover-stay-time="80" role="button" aria-label="举报" @tap="onReport">
      <IconSvg name="report" :size="28" color="var(--text-secondary)" class="interact-icon" />
      <text class="interact-count">举报</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from '@/components/IconSvg.vue'

const props = withDefaults(
  defineProps<{
    commentCount: number
    /** 「有用」计数（后端语义已含当前用户） */
    usefulCount?: number
    /** 当前用户是否已点「有用」（驱动激活态高亮） */
    usefulActive?: boolean
    /** 「有用」请求在途锁（受控：父页面请求期间置 true，防连点） */
    usefulPending?: boolean
  }>(),
  { usefulCount: 0, usefulActive: false, usefulPending: false },
)

const emit = defineEmits<{
  (e: 'comment'): void
  (e: 'report'): void
  (e: 'useful'): void
}>()

function onComment() { emit('comment') }
function onReport() { emit('report') }
function onUseful() {
  // 受控连点锁：请求在途直接拦截（P0 防重复请求 / 计数漂移）
  if (props.usefulPending) return
  emit('useful')
}
</script>

<style scoped>
/* 扁平容器（不带卡片背景/圆角/外边距，由父级卡片控制整体样式；
   注意：mp-weixin 组件样式隔离，父级 :deep() 无法命中本组件根节点，
   顶部留白必须写在本组件内，避免互动栏与上方分隔线贴合） */
.interact-bar { display: flex; align-items: center; gap: var(--spacing-md); margin: 0; padding: var(--spacing-md) 0 0; }
/* 互动按钮：高度/字号与列表 MomentCard 的 m-action 完全一致（64rpx + 28rpx 图标 + 24rpx 文字），
   透明边框占位保证激活时变边框不跳高；「有用」激活态统一用点赞色 --color-like（与列表/评论语义一致） */
.interact-btn { display: inline-flex; align-items: center; justify-content: center; gap: var(--spacing-xs); height: 64rpx; padding: 0 var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid transparent; background: var(--bg-soft); box-sizing: border-box; transition: transform var(--duration-fast) ease, background var(--duration-fast) ease, border-color var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.interact-btn:active { transform: scale(var(--press-scale)); }
.interact-icon { font-size: 28rpx; line-height: 1; color: var(--text-secondary); }
/* 有用在途指示（受控锁视觉，用 --color-like 与激活态一致） */
.interact-spinner { width: 22rpx; height: 22rpx; border: 3rpx solid var(--color-like-soft); border-top-color: var(--color-like); border-radius: 50%; animation: interact-spin 0.7s linear infinite; }
@keyframes interact-spin { to { transform: rotate(360deg); } }
.interact-count { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.interact-btn.active { border-color: var(--color-like); background: var(--color-like-soft); }
.interact-btn.active .interact-icon { color: var(--color-like); }
.interact-btn.active .interact-count { color: var(--color-like); }
.interact-btn.report { margin-left: auto; }
.interact-btn.report .interact-icon { color: var(--text-tertiary); }
.interact-btn.report .interact-count { color: var(--text-tertiary); }
</style>
