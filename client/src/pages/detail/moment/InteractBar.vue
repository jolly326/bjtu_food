<template>
  <view class="interact-bar">
    <!-- 「有用」：动态核心互动（与列表 MomentCard 语义一致：thumb 图标 + 计数 + 激活态） -->
    <view
      class="interact-btn"
      :class="{ active: usefulActive }"
      hover-class="pressed"
      hover-stay-time="80"
      role="button"
      aria-label="标记有用"
      @tap="onUseful"
    >
      <view v-if="usefulPending" class="interact-text">…</view>
      <IconSvg
        name="thumb"
        :size="36"
        :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'"
        class="interact-icon"
      />
      <text class="interact-count">{{ usefulCount > 0 ? usefulCount : '有用' }}</text>
    </view>
    <view class="interact-btn" hover-class="pressed" hover-stay-time="80" role="button" aria-label="评论" @tap="onComment">
      <IconSvg name="comment" :size="36" color="var(--text-secondary)" class="interact-icon" />
      <text class="interact-count">{{ commentCount > 0 ? commentCount : '评论' }}</text>
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
  (e: 'useful'): void
}>()

function onComment() { emit('comment') }
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
/* moment-list-detail-polish：去浅灰底，改极简图标+数字（与列表 m-action 同款 36rpx/2px 图标、计数 30rpx/500） */
.interact-btn { display: inline-flex; align-items: center; justify-content: center; gap: 12rpx; height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); border: none; background: transparent; box-sizing: border-box; transition: color var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.interact-icon { font-size: var(--font-body); line-height: 1; color: var(--text-secondary); }
/* 有用在途指示（受控锁视觉，用 --color-like 与激活态一致） */
.interact-text { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--color-like); }
.interact-count { font-size: var(--font-caption); font-weight: var(--weight-medium); color: var(--text-secondary); }
.interact-btn.active .interact-icon { color: var(--color-like); }
.interact-btn.active .interact-count { color: var(--color-like); }
/* moment-detail-action-deemphasis：动态主体卡举报统一收纳进「三点」动作面板，
   互动条移除原弱化「举报」文字项，仅保留有用/评论高频互动 */
</style>
