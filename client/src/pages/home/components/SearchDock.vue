<script setup lang="ts">
/**
 * 底部 HUD（docs/ui/client/菜品首页.md `SearchDock`）：上层筛选行 + 下层搜索胶囊。
 *
 * 筛选行承载结果数、约束 chip 与候选值（行内就地替换、纵向换行）；
 * 胶囊承载输入框与主色放大镜按钮，长时间无交互淡出待机。
 */
import { computed, onMounted, ref, watch } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { RESULT, SEARCH_DOCK } from '@/constants'
import { useTimers } from '@/hooks'
import type { Constraint } from '@/utils/constraints'
import { budgetConstraint, canteenConstraint } from '@/utils/constraints'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  modelValue: string
  searching: boolean
  conditioned: boolean
  /** 当前结果卡数（在售位置数） */
  count: number
  constraints: Constraint[]
  canteenOptions: string[]
  stale: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
  (e: 'clear'): void
  (e: 'pick', constraint: Constraint): void
  (e: 'remove', constraint: Constraint): void
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

const panel = ref<'canteen' | 'budget' | null>(null)

const budgetOptions = computed(() =>
  RESULT.BUDGET_OPTIONS.map((yuan) => {
    const c = budgetConstraint(yuan)
    return { c, on: props.constraints.some((x) => x.value === c.value) }
  }),
)
const canteenOptions = computed(() =>
  props.canteenOptions.map((name) => {
    const c = canteenConstraint(name)
    return { c, on: props.constraints.some((x) => x.value === c.value) }
  }),
)
/** 候选值只列未生效项，已生效约束由 chip 呈现 */
const candidateOptions = computed(() => {
  const all = panel.value === 'budget' ? budgetOptions.value : panel.value === 'canteen' ? canteenOptions.value : []
  return all.filter((opt) => !opt.on)
})

function togglePanel(kind: 'canteen' | 'budget'): void {
  panel.value = panel.value === kind ? null : kind
  wake()
}

function closePanel(): void {
  panel.value = null
  wake()
}

function onPick(c: Constraint): void {
  emit('pick', c)
  panel.value = null
  wake()
}

function onRemove(c: Constraint): void {
  emit('remove', c)
  wake()
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

/** 外部（搜索完成 / 清空）改动输入时同样重新计时 */
watch(
  () => props.modelValue,
  () => wake(),
)

onMounted(sleep)
</script>

<template>
  <view class="hud" :class="{ 'hud--idle': idle }" @tap="wake">
    <view class="hud__filters">
      <text v-if="conditioned && !stale" class="hud__count">{{ count }} 处结果</text>
      <text v-else-if="stale" class="hud__count hud__count--muted">点搜索更新</text>

      <view class="hud__chips">
        <template v-if="panel">
          <view
            v-for="opt in candidateOptions"
            :key="`opt-${opt.c.value}`"
            class="hud__chip"
            hover-class="hud__chip--press"
            @tap="onPick(opt.c)"
          >{{ opt.c.label }}</view>
          <view class="hud__entry" hover-class="hud__entry--press" @tap="closePanel">取消</view>
        </template>
        <template v-else>
          <view
            v-for="c in constraints"
            :key="`on-${c.value}`"
            class="hud__chip hud__chip--on"
            hover-class="hud__chip--press"
            @tap="onRemove(c)"
          >
            <text>{{ c.label }}</text>
            <text class="hud__chipX">×</text>
          </view>
          <view class="hud__entry" hover-class="hud__entry--press" @tap="togglePanel('canteen')">食堂</view>
          <view class="hud__entry" hover-class="hud__entry--press" @tap="togglePanel('budget')">预算</view>
          <view v-if="conditioned" class="hud__entry" hover-class="hud__entry--press" @tap="emit('clear')">重置</view>
        </template>
      </view>
    </view>

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
        <view v-if="modelValue" class="dock__clear" hover-class="dock__clear--press" @tap="emit('clear')">
          清空
        </view>
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
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

/* 筛选行：无底色、无描边，只占自身 chip 与文字的矩形 */
.hud__filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  min-height: var(--control-height-sm);
}

.hud__count {
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
}

.hud__count--muted {
  color: var(--text-disabled);
}

.hud__chips {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  animation: chips-in var(--duration-base) var(--ease-out);
}

@keyframes chips-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.hud__chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-sm);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  font-size: var(--font-caption);
  color: var(--text-body);
}

/* 视觉高度 64rpx，命中区纵向补到触达下限 */
.hud__chip::after,
.hud__entry::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  content: '';
}

.hud__chip--on {
  border-color: var(--brand);
  background: var(--brand);
  color: var(--on-brand);
}

.hud__chipX {
  margin-left: var(--spacing-2xs);
  color: var(--on-brand);
}

.hud__chip--press,
.hud__entry--press {
  opacity: 0.6;
}

.hud__entry {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-xs);
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
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
