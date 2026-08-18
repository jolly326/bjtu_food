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
        class="broadcast-track"
        :class="{ 'is-animating': animating }"
        :style="{ transform: `translateY(${-index * itemHeight}rpx)` }"
      >
        <view
          v-for="(item, i) in items"
          :key="i"
          class="broadcast-item"
          :style="{ height: itemHeight + 'rpx' }"
        >
          <text class="broadcast-text">{{ item.text }}</text>
        </view>
        <view
          v-if="items.length"
          class="broadcast-item"
          :style="{ height: itemHeight + 'rpx' }"
        >
          <text class="broadcast-text">{{ items[0].text }}</text>
        </view>
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
  // 末尾克隆项（index === items.length）视觉显示的是 items[0] 的内容，
  // 点击应映射回首项——否则克隆位点击时 props.items[index] 为 undefined，静默无响应。
  const i = index.value >= props.items.length ? 0 : index.value
  const item = props.items[i]
  if (item) emit('select', item)
}

// item 高度（与 .broadcast-viewport 的 88rpx 对齐，单位 rpx）
const itemHeight = 88
const index = ref(0)
const animating = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
// N03 修复：复位 setTimeout 句柄，离开页面时清理，避免切后台仍跑叠加
let resetTimer: ReturnType<typeof setTimeout> | null = null

function tick() {
  if (props.items.length <= 1) return
  animating.value = true
  index.value = (index.value + 1) % (props.items.length + 1)
  // 滚到末尾的克隆项后，无动画复位到首项
  if (index.value === props.items.length) {
    if (resetTimer) clearTimeout(resetTimer)
    resetTimer = setTimeout(() => {
      animating.value = false
      index.value = 0
    }, 300)
  }
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
  if (resetTimer) {
    clearTimeout(resetTimer)
    resetTimer = null
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
    animating.value = false
    if (list.length > 1) start()
    else stop()
  },
  { immediate: true }
)

onUnmounted(stop)

// 页面可见性由宿主页面生命周期控制（onShow/onHide 在组件中不生效）：
// 页面隐藏时 stop() 暂停轮播（切后台/进详情页不再白耗 setData），重新可见时 start() 恢复。
defineExpose({ start, stop })
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
  height: 88rpx;
  overflow: hidden;
}
.broadcast-track {
  display: flex;
  flex-direction: column;
  will-change: transform;
}
.broadcast-track.is-animating {
  transition: transform var(--duration-slow) var(--ease-out);
}
.broadcast-item {
  display: flex;
  align-items: center;
  height: 88rpx;
  width: 100%;
}
.broadcast-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: normal;
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-track.is-animating { transition: none; }
}
</style>
