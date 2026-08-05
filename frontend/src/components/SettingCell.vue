<template>
  <view
    class="menu-item"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap.stop="$emit('select')"
  >
    <view v-if="icon && iconBg" class="menu-icon-block" :class="`tone-${icon}`">
      <IconSvg :name="icon" :size="36" :color="iconTone?.color" class="menu-icon-block-svg" />
    </view>
    <IconSvg v-else-if="icon" :name="icon" :size="36" color="var(--color-primary)" class="menu-icon" />
    <text v-else class="menu-icon-placeholder" />
    <text class="menu-label" :class="{ danger }">{{ label }}</text>
    <text v-if="hint" class="menu-hint">{{ hint }}</text>
    <view v-if="badgeCount && badgeCount > 0" class="menu-badge-count">{{ badgeCount > 99 ? '99+' : badgeCount }}</view>
    <view v-else-if="badge" class="menu-badge" />
    <IconSvg name="arrow-left" :size="28" color="var(--text-tertiary)" class="menu-arrow" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

const props = defineProps<{
  label: string
  icon?: string
  hint?: string
  badge?: boolean
  /** 数值型未读角标；传入 >0 时显示数字（上限 99+），优先级高于 badge 红点 */
  badgeCount?: number
  /** 彩色软底图标块（Apple 设置风格）：按 icon key 映射语义色，增强「我的」页层次 */
  iconBg?: boolean
  /** 危险操作（如账号注销）：label 标红 */
  danger?: boolean
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件 @tap 编译为原生 bindtap + emit 同名事件双触发，handler 会被调用两次
// （showModal 会叠加两个弹窗，需点两次关闭）。故用 select + .stop 防连击。
defineEmits<{ (e: 'select'): void }>()

/** 图标块色映射（统一品牌主色系，避免多色/中性灰；iconBg 开启时生效） */
const ICON_TONE: Record<string, { bg: string; color: string }> = {
  comment: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  list: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  bell: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  plus: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  contact: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  settings: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  logo: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  lock: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  delete: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
  profile: { bg: 'var(--color-primary-soft)', color: 'var(--color-primary)' },
}

const iconTone = computed(() => (props.icon && props.iconBg ? ICON_TONE[props.icon] ?? null : null))

const pressed = ref(false)
</script>

<style scoped>
.menu-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  gap: var(--spacing-sm);
  border-bottom: 2rpx solid var(--border-color);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  transition: transform 120ms var(--ease-out), background 0.12s ease;
}
.menu-item:last-child { border-bottom: none; }
.menu-item.pressed { transform: scale(var(--press-scale)); background: var(--bg-soft); }
/* 图标与文本严格中线对齐：图标 block + line-height:1，避免图标因 image 默认行高偏上 */
.menu-icon { flex-shrink: 0; display: block; line-height: 1; align-self: center; }
.menu-icon-placeholder { width: 36rpx; flex-shrink: 0; display: block; line-height: 1; }
/* 彩色软底图标块（Apple 设置风格） */
.menu-icon-block {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-icon);
  display: flex;
  align-items: center;
  justify-content: center;
}
.menu-icon-block-svg { display: block; line-height: 1; }
.menu-icon-block { background: var(--color-primary-soft); }
.menu-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); line-height: 1; align-self: center; }
.menu-label.danger { color: var(--color-error); }
.menu-hint { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }
.menu-arrow { flex-shrink: 0; transform: rotate(180deg); }
.menu-badge { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); flex-shrink: 0; margin-right: calc(-1 * var(--spacing-xs)); }
.menu-badge-count {
  flex-shrink: 0;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: var(--color-error);
  color: var(--text-white);
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  line-height: 32rpx;
  text-align: center;
  margin-right: calc(-1 * var(--spacing-xs));
}
</style>
