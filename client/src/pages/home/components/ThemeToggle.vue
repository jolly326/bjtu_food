<script setup lang="ts">
/**
 * 主题切换入口（docs/ui/client/菜品首页.md `ThemeToggle`）。
 *
 * 落位页头 wordmark 右侧、与标题同一字号档；图标示出「点一下切到的模式」：
 * 浅色下为月亮、深色下为太阳。命中区经 ::after 补到触达下限，不撑高页头行。
 */
import { computed } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { useTheme } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const { isDark, toggleMode } = useTheme()

const label = computed(() => (isDark.value ? '切换到浅色模式' : '切换到深色模式'))
</script>

<template>
  <view
    class="toggle"
    hover-class="toggle--press"
    aria-role="button"
    :aria-label="label"
    @tap="toggleMode"
  >
    <AppIcon v-if="isDark" name="sun" :size="40" />
    <AppIcon v-else name="moon" :size="40" />
  </view>
</template>

<style lang="scss" scoped>
/* 视觉盒与 wordmark 同档：边长取 --font-title，图标因而与标题等大 */
.toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-sizing: border-box;
  width: var(--font-title);
  height: var(--font-title);
  transition: opacity var(--duration-fast) var(--ease-out);
}

.toggle::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--font-title)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  content: '';
}

.toggle--press {
  opacity: 0.6;
}
</style>
