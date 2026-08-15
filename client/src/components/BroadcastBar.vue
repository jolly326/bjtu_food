<template>
  <view
    class="broadcast-bar"
    @touchstart="pause"
    @touchend="resume"
    @touchcancel="resume"
    @mousedown="pause"
    @mouseup="resume"
    @mouseleave="resume"
  >
    <view class="broadcast-icon">
      <IconSvg name="broadcast" :size="28" color="var(--color-primary)" />
    </view>
    <view class="broadcast-viewport">
      <view
        v-if="current"
        class="broadcast-item"
        :class="{ 'is-enter': entering }"
      >
        <text class="broadcast-text">{{ current.text }}</text>
      </view>
      <view v-else class="broadcast-item">
        <text class="broadcast-text broadcast-placeholder">暂无广播</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import IconSvg from './IconSvg.vue'
import type { BroadcastItem } from '@/api/notify'

const props = defineProps<{
  items: BroadcastItem[]
}>()

const index = ref(0)
const entering = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const current = computed(() => props.items[index.value] || null)

function tick() {
  if (props.items.length <= 1) return
  entering.value = false
  // 下一帧触发淡入，确保 transition 生效
  requestAnimationFrame(() => {
    index.value = (index.value + 1) % props.items.length
    entering.value = true
  })
}

function start() {
  stop()
  if (props.items.length <= 1) return
  timer = setInterval(tick, 3000)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function pause() {
  stop()
}
function resume() {
  start()
}

watch(
  () => props.items,
  (list) => {
    index.value = 0
    if (list.length > 1) start()
    else stop()
  },
  { immediate: true }
)

onUnmounted(stop)
</script>

<style scoped>
.broadcast-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
}
.broadcast-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.broadcast-viewport {
  flex: 1;
  min-width: 0;
  height: 40rpx;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.broadcast-item {
  width: 100%;
  opacity: 1;
  transition: opacity 200ms ease;
}
.broadcast-item.is-enter {
  opacity: 0;
}
.broadcast-text {
  display: block;
  font-size: var(--font-aux);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.broadcast-placeholder {
  color: var(--text-tertiary);
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-item {
    transition: none;
  }
}
</style>
