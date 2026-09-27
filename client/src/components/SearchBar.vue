<template>
  <!-- 搜索行（跨页唯一实现）
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
      <IconSvg name="search" :size="18" :color="COLOR_MAP['text-placeholder']" class="search-pill-icon" />
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
        <IconSvg name="close" :size="16" :color="COLOR_MAP['text-placeholder']" />
      </view>
    </view>

    <!-- 右：独立「搜索」按钮（按文字定宽、不与胶囊等分） -->
    <view
      class="search-btn"
      :class="{ 'is-searching': searching, 'is-disabled': disabled }"
      :style="{ height: capsuleH }"
      role="button"
      :aria-label="buttonText"
      :aria-disabled="disabled ? 'true' : 'false'"
      :hover-class="searching || disabled ? 'none' : 'search-btn-pressed'"
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
  /**
   * 提交中（仅 `input` 模式有意义）：按钮降透明 + 禁点，避免重复提交与「点了没反应」。
   * 依据 ui-ux-pro-max §2 `loading-buttons`（异步操作期间禁用按钮并给出反馈）与
   * §8 `submit-feedback`；MVP 不引入 spinner / 骨架，故仅以禁用态表达。
   */
  searching?: boolean
  /**
   * 空关键词禁用态（仅 `input` 模式有意义）：置灰 + 禁点，**不让点击静默失效**。
   * 依据 ui-ux-pro-max §8 `submit-feedback`：不可执行的动作必须给出可见状态，
   * 而非「点了没反应」。输入框本身 **不禁用**（始终可输入）。
   */
  disabled?: boolean
}>(), {
  mode: 'entry',
  modelValue: '',
  // 占位只列真实可搜维度：服务端仅匹配「菜名 / 档口名 / 食堂名」（§7.35），
  // 无「套餐」实体（「套餐盖饭」是 meal_type 大类，不参与关键词匹配）——
  // 见 docs/ui/client-搜索.md §1 第 2 条。首页与搜索页共用本默认值，两页同源。
  placeholder: '搜索菜品、食堂、档口',
  buttonText: '搜索',
  searching: false,
  disabled: false,
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
  // 提交中拦下重复点击（skill §2 loading-buttons）；空词禁用态同样不提交（禁用必须可见，见 disabled 注释）；
  // entry 模式（首页）不受两者影响
  if (props.searching || props.disabled) return
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
  /* 极轻阴影（UI 统一 Loop Round 18 视觉微调）：原用 `--shadow-float`（0 6rpx 16rpx / 12% 黑）偏"浮起"，
     在暖底上显得比输入区本身更抢眼；`--shadow-card`（4% 黑）只做「与底色分离」的最小提示，
     让输入区（白底 + 文字）成为视觉主体 —— 贴图/底色只做氛围。 */
  box-shadow: var(--shadow-card);
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
/* 文案分三档（UI 统一 Loop Round 18）：占位 = `--text-placeholder`（#B5A594，轻）／
   已输入 = `--text-primary`（#2D1F14，深）—— 输入前后文字色差即「已输入」的第一视觉信号。 */
.search-pill-placeholder,
.search-pill-input {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-pill-input { color: var(--text-primary); }
.search-pill-ph { color: var(--text-placeholder); }
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
/* 「搜索」按钮：**填充档**主色 + 白字（实测 5.01:1 ✅，见 UI 文档 §4.4 / §10.2 取色边界）；
   与胶囊同高 / 同圆角 / 按文字定宽。
   ⚠️ 不得改用 --color-primary-orange #E67E22 —— 白字 on 它仅 2.85:1，不达 4.5:1 */
.search-btn {
  flex-shrink: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 左右内距 --spacing-lg：按钮宽 ≈60px，胶囊形更饱满、更好点 */
  padding: 0 var(--spacing-lg);
  background: var(--color-primary-fill);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.search-btn-pressed { opacity: 0.85; }
/* 提交中（input 模式）：降透明 + 禁点 —— skill §2 `loading-buttons` / §8 `submit-feedback`。
   MVP 不引入 spinner / 骨架，故仅以禁用态表达「已受理」，
   消除「点了没反应」并挡住重复提交。 */
.search-btn.is-searching { opacity: 0.6; pointer-events: none; }
/* 空关键词禁用态：**灰底 + 灰字**（UI 统一 Loop Round 18）。
   原先仅 `opacity: 0.5`，在暖底上仍偏「橙色半透明」，与「主色但没点」易混；
   改为中性灰底（`--bg-input`）+ 中性灰字（`--text-tertiary`）⇒「不可执行」一眼可辨（skill §8 submit-feedback）。
   `pointer-events: none` 同时去掉按压态 —— 不可执行的动作不给按压反馈。 */
.search-btn.is-disabled {
  background: var(--bg-input);
  pointer-events: none;
}
.search-btn.is-disabled .search-btn-text { color: var(--text-tertiary); }
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
