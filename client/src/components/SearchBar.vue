<template>
  <!-- 搜索行（跨页唯一实现，2026-09-22 change `search-page-refresh`）
       结构恒为「左搜索胶囊 + 右独立『搜索』按钮」——两者同高、间距 `--spacing-sm`、同为 `--radius-pill`：
       · `mode="entry"`（首页）：胶囊与按钮均为「进搜索页」入口（`@tap`）；
       · `mode="input"`（搜索页）：胶囊内为可输入框（点击即聚焦），按钮提交（`@search`），有值时可清除。
       高度取本机真实胶囊高（`useNavMetrics`）——写死 32px 会在胶囊高不同的机型上与微信胶囊不齐。 -->
  <view class="search-row">
    <!-- 左：搜索胶囊 -->
    <view
      class="search-pill"
      :class="{ 'is-input': mode === 'input' }"
      :style="{ height: capsuleH }"
      role="search"
      :aria-label="placeholder"
      :hover-class="mode === 'entry' ? 'search-pill-pressed' : 'none'"
      @tap="onPillTap"
    >
      <IconSvg name="search" :size="18" :color="COLOR_MAP['text-tertiary']" class="search-pill-icon" />
      <input
        v-if="mode === 'input'"
        class="search-pill-input"
        :value="modelValue"
        type="text"
        confirm-type="search"
        :placeholder="placeholder"
        placeholder-class="search-pill-ph"
        :adjust-position="true"
        @input="onInput"
        @confirm="emit('search')"
      />
      <text v-else class="search-pill-placeholder">{{ placeholder }}</text>
      <view
        v-if="mode === 'input' && modelValue"
        class="search-pill-clear"
        role="button"
        aria-label="清除关键词"
        @tap.stop="emit('clear')"
      >
        <IconSvg name="close" :size="16" :color="COLOR_MAP['text-tertiary']" />
      </view>
    </view>

    <!-- 右：独立「搜索」按钮（按文字定宽、不与胶囊等分） -->
    <view
      class="search-btn"
      :style="{ height: capsuleH }"
      role="button"
      aria-label="搜索"
      hover-class="search-btn-pressed"
      @tap="onButtonTap"
    >
      <text class="search-btn-text">{{ buttonText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { useNavMetrics } from '@/utils/useNavMetrics'

const props = withDefaults(defineProps<{
  /** entry=整行可点（进搜索页）；input=可输入 + 提交（搜索页） */
  mode?: 'entry' | 'input'
  /** input 模式的双向绑定值 */
  modelValue?: string
  placeholder?: string
  /** 右侧按钮文案（默认「搜索」） */
  buttonText?: string
}>(), {
  mode: 'entry',
  modelValue: '',
  placeholder: '搜索菜品、食堂、套餐',
  buttonText: '搜索',
})

const emit = defineEmits<{
  /** entry 模式：点击胶囊 / 按钮（进搜索页） */
  (e: 'tap'): void
  /** 提交搜索（input 模式回车 / 点按钮；entry 模式下与 tap 同义） */
  (e: 'search'): void
  (e: 'update:modelValue', value: string): void
  (e: 'clear'): void
}>()

const { capsuleHeightPx } = useNavMetrics()
/** 胶囊高（px 串）：与微信原生胶囊同高（全站头部高度统一口径） */
const capsuleH = computed(() => `${capsuleHeightPx.value}px`)

function onPillTap() {
  // entry：整颗胶囊是入口；input：交给原生 input 聚焦，不额外处理
  if (props.mode === 'entry') emit('tap')
}
/**
 * 右侧「搜索」按钮：**`entry` 模式与胶囊同义**（进搜索页 → 发 `tap`），
 * 只有 `input` 模式才是提交语义（发 `search`）。
 * 若 entry 也发 `search`，首页（只监听 `@tap`）的按钮会「点了没反应」。
 */
function onButtonTap() {
  if (props.mode === 'entry') emit('tap')
  else emit('search')
}
// 平台例外：uni input 事件对象未纳入项目 TS 类型，取 e.detail.value
function onInput(e: any) {
  emit('update:modelValue', e.detail.value)
}
</script>

<style scoped>
/* 行：左胶囊（flex:1）+ 右按钮（定宽）；间距 --spacing-sm（块内），左右外侧 = 页面级 gutter */
.search-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}
/* 搜索胶囊：白底 + pill + 柔和投影 */
.search-pill {
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: center;
  /* 图标 ↔ 文字间距：--spacing-sm（--spacing-xs 会让放大镜与文字贴在一起） */
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-float);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.search-pill-pressed { background: var(--bg-soft); }
/* 触达：胶囊本体低于 44px 时，::after 上下各扩 16rpx（落在容器留白内，不侵占相邻可点件）→ 整行可点 */
.search-pill::after {
  content: '';
  position: absolute;
  top: -16rpx;
  bottom: -16rpx;
  left: calc(-1 * var(--spacing-md));
  right: 0;
}
.search-pill.is-input::after { content: none; }
.search-pill-icon { flex-shrink: 0; line-height: 1; }
.search-pill-placeholder,
.search-pill-input {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-pill-input { color: var(--text-primary); }
.search-pill-ph { color: var(--text-tertiary); }
.search-pill-clear {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: var(--spacing-xs);
  margin: calc(-1 * var(--spacing-xs));
  border-radius: var(--radius-circle);
  -webkit-tap-highlight-color: transparent;
}
.search-pill-clear:active { opacity: 0.55; }
/* 「搜索」按钮：填充档主色 + 白字（对比度 5.18:1 ✅）；与胶囊同高 / 同圆角 / 按文字定宽 */
.search-btn {
  flex-shrink: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 左右内距 --spacing-lg：按钮宽 ≈60px，胶囊形更饱满、更好点 */
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.search-btn-pressed { opacity: 0.85; }
/* 触达：按钮可点区上下各扩 16rpx → ≥88rpx（不改变视觉尺寸） */
.search-btn::after {
  content: '';
  position: absolute;
  top: -16rpx;
  bottom: -16rpx;
  left: 0;
  right: 0;
}
/* 与胶囊占位（--font-body）同级：CTA 文案不得小于它旁边的输入占位 */
.search-btn-text {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--color-on-primary);
  white-space: nowrap;
}
</style>
