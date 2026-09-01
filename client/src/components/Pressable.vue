<template>
  <!-- 统一按压基础件（global-ui-polish / ui-press-system）。
       包裹可点内容，按下经 usePress 状态机 + .pressed 类做整卡缩放。
       ⚠️ 仅走 usePress 单一来源，不再叠加 hover-class="pressed"：
       两套机制叠加会在滑动划过列表时高频触发 .pressed，而卡片根无
       overflow:hidden 裁切、合成层下 border-radius 对自身背景裁剪失效，
       换色型 .pressed（如 MomentCard 的 --bg-soft）会在左上角露出方角 =
       社区页「每条动态左上角色块」。统一改为整卡缩放（不换背景色）后消除。
       透传 role/aria-label 与默认 slot、@tap。 -->
  <view
    class="pressable"
    :class="[{ pressed: pressedVal }, pressClass]"
    :role="role"
    :aria-label="ariaLabel"
    tabindex="0"
    @touchstart="h.onTouchStart"
    @touchend="h.onTouchEnd"
    @touchcancel="h.onPressCancel"
    @mousedown="h.onMouseDown"
    @mouseup="h.onMouseUp"
    @mouseleave="h.onMouseLeave"
    @keydown="onKeydown"
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
}>(), {
  role: 'button',
  ariaLabel: undefined,
  pressClass: '',
})

const emit = defineEmits<{
  (e: 'tap', ev: any): void
}>()

const h = usePress()
const pressedVal = computed(() => h.pressed.value)

// 键盘可达（H5/桌面）：Enter / 空格触发与点击等价的激活（2.1）
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
    e.preventDefault()
    h.onMouseDown()
    h.onMouseUp()
    emit('tap', e)
  }
}
</script>

<style scoped>
.pressable {
  display: block;
  /* 兜底裁切：根元素圆角外背景残留（合成层 border-radius 裁剪失效）由本属性裁掉，
     避免子卡换色/图片贴边时露出方角色块 */
  overflow: hidden;
  /* 按压反馈仅整卡缩放（不换背景色），与 DishCard 一致；单一 transform 不触发换色型色块 */
  transition: transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.pressable.pressed {
  transform: scale(var(--press-scale));
}
</style>
