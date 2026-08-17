<template>
  <view
    class="broadcast-bar"
    @touchstart="pause"
    @touchend="resume"
    @touchcancel="resume"
    @mousedown="pause"
    @mouseup="resume"
    @mouseleave="resume"
    @tap="onTap"
  >
    <view class="broadcast-icon">
      <IconSvg name="broadcast" :size="36" color="var(--color-primary)" />
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

const emit = defineEmits<{
  (e: 'select', item: BroadcastItem): void
}>()

function onTap() {
  if (current.value) emit('select', current.value)
}

const index = ref(0)
const entering = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const current = computed(() => props.items[index.value] || null)

function tick() {
  if (props.items.length <= 1) return
  entering.value = true // 当前条淡出
  // 淡出过渡结束后，再切换内容并淡入（220ms > opacity 过渡 200ms，避免半透明残留）
  setTimeout(() => {
    index.value = (index.value + 1) % props.items.length
    entering.value = false // 新条淡入
  }, 220)
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

/** 触摸时暂停轮播计时，松手恢复 */
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
  padding: 0 var(--spacing-sm);
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
  min-height: 100rpx;
  display: flex;
  align-items: center;
  overflow: hidden;
}
.broadcast-item {
  width: 100%;
  opacity: 1;
  transition: opacity 200ms ease;
}
.broadcast-item.is-enter {
  opacity: 0;
}
/* 最多 2 行截断，超长省略，不强制单行 */
.broadcast-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.5;
}
.broadcast-placeholder {
  color: var(--text-tertiary);
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-item { transition: none; }
}
</style>
