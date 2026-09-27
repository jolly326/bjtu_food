<template>
  <!-- 搜索栏（跨页唯一实现；UI 统一 Loop Round 20 结构升级）
       结构 = **单个搜索胶囊**（白底 + 大圆角 + 极轻阴影），胶囊内自左向右：
       ① 放大镜（左侧、行内垂直居中）→ ② 输入框 / 占位文案 → ③（有值时）清除 ✕ → ④ **右侧内嵌「搜索」按钮**。
       · "内嵌" = 按钮与胶囊等高（`align-self: stretch`）、四周只留 `--spacing-xs` 内距，
         看起来像胶囊右端的一颗内胆；不再有「左胶囊 + 右独立按钮」两颗并列。
       · ❗️**不做下拉箭头**（用户明确不要）。
       · `mode="entry"`（首页）：整条胶囊（含内嵌按钮）都是「进搜索页」入口（`@tap`）；
       · `mode="input"`（搜索页）：胶囊内为可输入框（点击即聚焦），按钮提交（`@search`），有值时可清除。
       高度取 `--search-bar-height`（96rpx ≈ 48px）；触控目标 ≥ 88rpx 由整条胶囊自身满足，
       故不再需要旧版的 `::after` 扩展热区。 -->
  <view class="search-bar-host">
    <view
      class="search-bar"
      :class="{ 'is-input': mode === 'input' }"
      role="search"
      :aria-label="placeholder"
      :hover-class="mode === 'entry' ? 'search-bar-pressed' : 'none'"
      @tap="onBarTap"
    >
      <IconSvg name="search" :size="40" :color="COLOR_MAP['text-placeholder']" class="search-bar-icon" />
      <input
        v-if="mode === 'input'"
        class="search-bar-input"
        :value="modelValue"
        type="text"
        confirm-type="search"
        :placeholder="placeholder"
        placeholder-class="search-bar-ph"
        :adjust-position="true"
        @input="onInput"
        @confirm="emit('search')"
      />
      <text v-else class="search-bar-placeholder">{{ placeholder }}</text>
      <view
        v-if="mode === 'input' && modelValue"
        class="search-bar-clear"
        role="button"
        aria-label="清除关键词"
        @tap.stop="emit('clear')"
      >
        <IconSvg name="close" :size="30" :color="COLOR_MAP['text-placeholder']" />
      </view>
      <!-- 内嵌提交按钮：`@tap.stop` 必须保留 —— 否则 entry 模式下按钮与整条胶囊会各发一次 tap（双跳） -->
      <view
        class="search-bar-btn"
        :class="{ 'is-searching': searching, 'is-disabled': disabled }"
        role="button"
        :aria-label="buttonText"
        :aria-disabled="disabled ? 'true' : 'false'"
        :hover-class="searching || disabled ? 'none' : 'search-bar-btn-pressed'"
        @tap.stop="onButtonTap"
      >
        <text class="search-bar-btn-text">{{ buttonText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** entry=整条胶囊可点（进搜索页）；input=可输入 + 提交（搜索页） */
  mode?: 'entry' | 'input'
  /** input 模式的双向绑定值 */
  modelValue?: string
  placeholder?: string
  /** 内嵌按钮文案（默认「搜索」） */
  buttonText?: string
  /**
   * 提交中（仅 `input` 模式有意义）：按钮降透明 + 禁点，避免重复提交与「点了没反应」。
   * 依据 ui-ux-pro-max §2 `loading-buttons`（异步操作期间禁用按钮并给出反馈）与
   * §8 `submit-feedback`；MVP 不引入 spinner / 骨架，故仅以禁用态表达。
   */
  searching?: boolean
  /**
   * 空关键词禁用态（仅 `input` 模式有意义）：**灰底灰字** + 禁点，**不让点击静默失效**。
   * 依据 ui-ux-pro-max §8 `submit-feedback`：不可执行的动作必须给出可见状态。
   * 输入框本身 **不禁用**（始终可输入）。
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
  /** entry 模式：点击胶囊 / 内嵌按钮（进搜索页） */
  (e: 'tap'): void
  /** 提交搜索（input 模式回车 / 点按钮；entry 模式下与 tap 同义） */
  (e: 'search'): void
  (e: 'update:modelValue', value: string): void
  (e: 'clear'): void
}>()

function onBarTap() {
  // entry：整条胶囊是入口；input：交给原生 input 聚焦，不额外处理
  if (props.mode === 'entry') emit('tap')
}
/**
 * 内嵌「搜索」按钮：**`entry` 模式与胶囊同义**（进搜索页 → 发 `tap`），
 * 只有 `input` 模式才是提交语义（发 `search`）。
 * 若 entry 也发 `search`，首页（只监听 `@tap`）的按钮会「点了没反应」。
 */
function onButtonTap() {
  // 提交中拦下重复点击（skill §2 loading-buttons）；空词禁用态同样不提交（禁用必须可见）
  if (props.searching || props.disabled) return
  if (props.mode === 'entry') emit('tap')
  else emit('search')
}

/**
 * 取输入值：uni-app 把 `@input` 的载荷声明为 DOM `Event`（不含 `detail`），
 * 而小程序运行时实际是 `{ detail: { value } }` ⇒ 此处做一次**最小结构收窄**（平台例外，不再用 `any`）。
 */
function onInput(e: Event) {
  emit('update:modelValue', (e as unknown as { detail: { value: string } }).detail.value)
}
</script>

<style scoped>
/* 宿主：只负责页面级左右 gutter（与全站 `--spacing-md` 一致），高度由内层胶囊自持 */
.search-bar-host {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

/* 搜索胶囊（唯一形态）：白底 + pill + 极轻阴影；
   内距 = 左 `--spacing-md`（放大镜呼吸位）/ 右与上下 `--spacing-xs`（内嵌按钮的内胆留白） */
.search-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: var(--search-bar-height);
  padding: var(--spacing-xs) var(--spacing-xs) var(--spacing-xs) var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.search-bar-pressed { background: var(--bg-soft); }

/* ① 放大镜：**宿主节点**（`<icon-svg>` 自定义组件，未开 virtualHost）显式定为 40rpx 方形 flex 盒。
   原因（Round 20 实测）：flex 行的子项是宿主节点而非内部 `.icon-svg` view，宿主无布局样式时
   内部图标按「文本行盒 + 基线」排 ⇒ 随继承字体度量垂直偏移（真机读作"偏下"）。
   宿主自身成为 flex 容器后，内部图标在其内**精确居中**，与行盒/基线彻底解耦。 */
.search-bar-icon {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ② 文案：占位更轻（`--text-placeholder`）、已输入更深（`--text-primary`）—— 色差即「已输入」的第一信号 */
.search-bar-placeholder,
.search-bar-input {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-bar-input { color: var(--text-primary); }
.search-bar-ph { color: var(--text-placeholder); }

/* ③ 清除：56rpx 命中盒 + 圆形按压反馈（视觉 30rpx 图标） */
.search-bar-clear {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: var(--radius-circle);
  -webkit-tap-highlight-color: transparent;
}
.search-bar-clear:active { opacity: 0.55; }

/* ④ 内嵌「搜索」按钮：贴胶囊右端、与胶囊等高（`align-self: stretch` + 上下 `--spacing-xs` 内距）
   填充档主色 + 白字（实测 5.01:1 ✅，见 UI 文档 §4.4 / §10.2 取色边界）；
   ⚠️ 不得改用 --color-primary-orange #E67E22 —— 白字 on 它仅 2.85:1，不达 4.5:1 */
.search-bar-btn {
  flex: none;
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 左右内距 `--spacing-lg`：按钮宽 ≈ 132rpx，胶囊形饱满、好点 */
  padding: 0 var(--spacing-lg);
  border-radius: var(--radius-pill);
  background: var(--color-primary-fill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.search-bar-btn-pressed { opacity: 0.85; }
/* 提交中：降透明 + 禁点（skill §2 loading-buttons / §8 submit-feedback；MVP 不引入 spinner） */
.search-bar-btn.is-searching { opacity: 0.6; pointer-events: none; }
/* 空词禁用态：**灰底 + 灰字**（比单纯降透明更明确的「不可执行」语义），并去掉按压反馈 */
.search-bar-btn.is-disabled {
  background: var(--bg-input);
  pointer-events: none;
}
.search-bar-btn.is-disabled .search-bar-btn-text { color: var(--text-tertiary); }
/* 与胶囊占位（--font-body）同级：CTA 文案不得小于它旁边的输入占位 */
.search-bar-btn-text {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--color-on-primary);
  white-space: nowrap;
}
</style>
