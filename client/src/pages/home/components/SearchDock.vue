<script setup lang="ts">
/**
 * 底部搜索 HUD（docs/ui/client/菜品首页.md `SearchDock`）：单行胶囊。
 *
 * 输入框 + 主色放大镜按钮；不承载筛选、结果数或约束，条件只来自一句话文本。
 * 长时间无交互 ⇒ 胶囊淡出待机，任意点击 / 输入立即恢复。
 */
import { onMounted, ref, watch } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { SEARCH_DOCK } from '@/constants'
import { useTimers } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  modelValue: string
  searching: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
  (e: 'clear'): void
}>()

/** 待机态：长时间无点击 / 输入 ⇒ 搜索胶囊降低透明度，任意交互立即恢复 */
const idle = ref(false)
const { setTimer, clearTimer } = useTimers()
let idleTimer: ReturnType<typeof setTimeout> | null = null

function sleep(): void {
  idleTimer = clearTimer(idleTimer)
  idleTimer = setTimer(() => {
    idle.value = true
  }, SEARCH_DOCK.IDLE_MS)
}

function wake(): void {
  idle.value = false
  sleep()
}

function onInput(event: Event): void {
  const detail = (event as unknown as { detail?: { value?: string } }).detail
  wake()
  emit('update:modelValue', detail?.value ?? '')
}

function submit(): void {
  wake()
  if (props.searching) return
  emit('search', props.modelValue)
}

/** 外部（清空）改动输入时同样重新计时 */
watch(
  () => props.modelValue,
  () => wake(),
)

onMounted(sleep)
</script>

<template>
  <view class="hud" :class="{ 'hud--idle': idle }" @tap="wake">
    <view class="dock">
      <view class="dock__field">
        <input
          class="dock__input"
          :value="modelValue"
          type="text"
          confirm-type="search"
          placeholder="想吃点什么？"
          placeholder-class="dock__ph"
          @input="onInput"
          @focus="wake"
          @confirm="submit"
        />
        <view
          v-if="modelValue"
          class="dock__clear"
          hover-class="dock__clear--press"
          @tap="emit('clear')"
        >清空</view>
      </view>
      <view
        class="dock__btn"
        :class="{ 'dock__btn--busy': searching }"
        hover-class="dock__btn--press"
        @tap="submit"
      >
        <view v-if="searching" class="dock__spinner">
          <AppIcon name="loading" :size="40" inverse />
        </view>
        <AppIcon v-else name="search" :size="40" inverse />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 悬浮 HUD：脱离布局流，浮于内容之上；左右与底部对齐页面 gutter / 底缘安全区 */
.hud {
  position: absolute;
  right: var(--page-gutter);
  /* 兜底：不含安全区；下一层叠加安全区（calc 失效时仍保留底距） */
  bottom: var(--spacing-sm);
  bottom: calc(var(--spacing-sm) + env(safe-area-inset-bottom, 0px));
  left: var(--page-gutter);
  z-index: var(--z-dock);
}

/* 搜索胶囊：纯白底 + 细描边 + 主色实底按钮 */
.dock {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  gap: var(--spacing-sm);
  padding: var(--spacing-2xs) var(--spacing-2xs) var(--spacing-2xs) var(--spacing-md);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  transition: opacity var(--duration-base) var(--ease-out);
}

/* 待机：长时间无交互 ⇒ 胶囊淡出，任意点击 / 输入立即回到常态 */
.hud--idle .dock {
  opacity: 0.7;
}

.dock__field {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  height: var(--control-height);
  gap: var(--spacing-xs);
}

.dock__input {
  flex: 1;
  min-width: 0;
  height: var(--control-height);
  font-size: var(--font-body);
  color: var(--text-title);
}

.dock__ph {
  color: var(--text-disabled);
}

.dock__clear {
  padding: 0 var(--spacing-2xs);
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.dock__clear--press {
  opacity: 0.6;
}

.dock__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-sizing: border-box;
  width: var(--control-height);
  height: var(--control-height);
  border: var(--border-width) solid var(--brand);
  border-radius: var(--radius-pill);
  background: var(--brand);
  transition: opacity var(--duration-fast) var(--ease-out);
}

.dock__btn--press {
  opacity: 0.7;
}

.dock__btn--busy {
  opacity: 0.6;
}

/* 搜索中：图标本体匀速旋转，无外围圆环 */
.dock__spinner {
  display: flex;
  animation: dock-spin var(--duration-spin) linear infinite;
}

@keyframes dock-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
