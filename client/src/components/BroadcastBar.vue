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
      <IconSvg name="broadcast" :size="28" color="var(--color-primary)" />
    </view>
    <view class="broadcast-viewport">
      <view
        v-if="current"
        class="broadcast-item"
        :class="{ 'is-enter': entering }"
      >
        <text
          class="broadcast-text"
          :class="{ 'is-scroll': needScroll, 'is-paused': paused }"
        >{{ current.text }}</text>
      </view>
      <view v-else class="broadcast-item">
        <text class="broadcast-text broadcast-placeholder">暂无广播</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch, nextTick, getCurrentInstance } from 'vue'
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
const needScroll = ref(false)
const paused = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
const instance = getCurrentInstance()

const current = computed(() => props.items[index.value] || null)

/** 测量文字宽度是否超出视口：超出则跑马灯滚动（跨端用 selectorQuery 拿真实宽度，兼容小程序无 DOM） */
function measure() {
  if (!current.value) {
    needScroll.value = false
    return
  }
  uni.createSelectorQuery().in(instance!)
    .select('.broadcast-viewport').boundingClientRect()
    .select('.broadcast-text').boundingClientRect()
    .exec((res: any) => {
      const vp = res?.[0]
      const tx = res?.[1]
      needScroll.value = !!(vp && tx && tx.width > vp.width)
    })
}

function tick() {
  if (props.items.length <= 1) return
  entering.value = true        // 当前条淡出
  // 淡出过渡结束后，再切换内容并淡入（220ms > opacity 过渡 200ms，避免半透明残留）
  setTimeout(() => {
    index.value = (index.value + 1) % props.items.length
    entering.value = false      // 新条淡入
    nextTick(measure)
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

/** 触摸时：暂停轮播计时 + 暂停跑马灯；松手恢复 */
function pause() {
  paused.value = true
  stop()
}
function resume() {
  paused.value = false
  start()
}

watch(
  () => props.items,
  (list) => {
    index.value = 0
    if (list.length > 1) start()
    else stop()
    nextTick(measure)
  },
  { immediate: true }
)

// 文案切换后重新测量是否需滚动（长文案才跑马灯）
watch(() => current.value?.text, () => nextTick(measure))

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
/* 文字默认 inline-block（自适应内容宽），不截断；超出视口才滚动 */
.broadcast-text {
  display: inline-block;
  white-space: nowrap;
  font-size: var(--font-aux);
  color: var(--text-secondary);
}
.broadcast-placeholder {
  color: var(--text-tertiary);
}
/* 超长文案：跑马灯滚动（触摸暂停由 .is-paused 控制） */
.broadcast-text.is-scroll {
  padding-right: 40rpx;
  animation: bc-marquee 8s linear infinite;
}
.broadcast-text.is-paused {
  animation-play-state: paused;
}
@keyframes bc-marquee {
  0% { transform: translateX(0); }
  100% { transform: translateX(calc(-100% - 40rpx)); }
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-item { transition: none; }
  .broadcast-text.is-scroll { animation: none; }
}
</style>
