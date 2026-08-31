<template>
  <!-- 统一按压基础件（global-ui-polish / ui-press-system）。
       包裹可点内容，按下经 usePress 状态机 + 全局 .pressed 类缩放；
       mp-weixin 真机另用 hover-class="pressed" 兜底。透传 role/aria-label 与默认 slot、@tap。 -->
  <view
    class="pressable press"
    :class="[{ pressed: pressedVal }, pressClass]"
    :role="role"
    :aria-label="ariaLabel"
    hover-class="pressed"
    :hover-stay-time="hoverStayTime"
    :hover-start-time="hoverStartTime"
    @touchstart="h.onTouchStart"
    @touchend="h.onTouchEnd"
    @touchcancel="h.onPressCancel"
    @mousedown="h.onMouseDown"
    @mouseup="h.onMouseUp"
    @mouseleave="h.onMouseLeave"
    @tap="$emit('tap', $event)"
  >
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePress } from '@/composables/usePress'

const props = withDefaults(defineProps<{
  /** 无障碍角色，默认 button */
  role?: string
  /** 无障碍语义标签（纯图标按钮必填） */
  ariaLabel?: string
  /** 额外类名透传（如 dish-card / app-btn，供既有样式命中） */
  pressClass?: string | string[] | Record<string, boolean>
  /** 按下态保活时长（ms），与全局 .pressed 手感一致 */
  hoverStayTime?: number
  hoverStartTime?: number
}>(), {
  role: 'button',
  ariaLabel: undefined,
  pressClass: '',
  hoverStayTime: 80,
  hoverStartTime: 0,
})

const emit = defineEmits<{
  (e: 'tap', ev: any): void
}>()

const h = usePress()
const pressedVal = computed(() => h.pressed.value)
</script>

<style scoped>
.pressable {
  display: block;
  -webkit-tap-highlight-color: transparent;
}
</style>
