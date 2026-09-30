<template>
  <!--
    AttributePickerSheet（correction 包内私有，R41 新增）—— 「更多候选」底部选择弹层。

    底座复用公共 `BaseSheet`（遮罩 / grabber / 下滑关闭 / 安全区 / 焦点回收 / `scroll-body` 内滚，
    与 `ReviewComposer` 同口径）；本项目 `client/src` 零原生 `<picker>` / `showActionSheet` 先例，
    **禁**绕开 BaseSheet 自建弹层。

    职责边界（UI 稿「属性候选区 · 弹层」R41）：
    · **只做选择，不做提交**：确认 / 清空 / 单选点项 → `emit('update', next)` 回抛**新数组**，
      由 `AttributeGroup` 转发 `change(fieldKey, next)`，表单值真源恒在父级编排（`useCorrection`）；
    · **草稿态**：多选组在弹层内的勾选只写本地 `draft`，点【确认】才回抛；
      点关闭 / 遮罩 / 下滑 ⇒ 只 `emit('close')`，**草稿丢弃、主表单不变**；
    · **单选组无确认按钮**：点候选 = 选中并立即回抛 + 关闭（省一步空操作）；
      「取消当前项」不在弹层内做，统一由主表单已选 chip 的删除叉承担，避免两处清空语义；
    · **不放自定义输入**（自定义恒在主表单，避免弹层内输入框被键盘顶起）：
      候选没命中时由底部「没找到？去自定义」上抛 `request-custom`，父级关弹层后聚焦该组输入框；
    · **搜索仅本地过滤**：候选由编辑端点一次性下发（全库去重），**禁**在弹层内再发请求。
  -->
  <BaseSheet :visible="visible" :title="`选择${name}`" closable scroll-body @close="emit('close')">
    <!-- 搜索框：仅候选总数 ≥ SEARCH_MIN 时渲染（判据用总数 ⇒ 打字过程中过滤结果为空也不会让搜索框消失） -->
    <view v-if="showSearch" class="aps-search">
      <view class="aps-search-box" :class="{ 'aps-search-box--focus': focused }">
        <input
          class="aps-search-input"
          :value="keyword"
          :placeholder="`搜索${name}候选`"
          placeholder-class="aps-search-ph"
          maxlength="10"
          confirm-type="search"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onKeyword"
          @focus="focused = true"
          @blur="focused = false"
        />
        <view
          v-if="keyword"
          class="aps-search-clear"
          role="button"
          aria-label="清空搜索"
          hover-class="aps-search-clear--pressed"
          hover-stay-time="80"
          @tap="keyword = ''"
        >
          <IconSvg name="close" :size="24" :color="COLOR_MAP['text-tertiary']" />
        </view>
      </view>
    </view>

    <!-- 候选全览（含已选项，选中态带对勾）：多选点即草稿内 toggle；单选点即选中并关闭 -->
    <view class="aps-chips">
      <TagChip
        v-for="c in shownCandidates"
        :key="c"
        :label="c"
        size="lg"
        checkable
        :variant="draft.includes(c) ? 'selected' : 'candidate'"
        :selected-style="selectedStyle"
        :aria-label="`${name}「${c}」`"
        @pick="onPick(c)"
      />
      <text v-if="!shownCandidates.length" class="aps-empty">没有匹配项，可关闭后在表单里自定义</text>
    </view>

    <!-- 底部条：「去自定义」恒在；按钮组仅多选组（单选点项即关，无需确认） -->
    <view class="aps-foot">
      <text
        class="aps-link"
        role="button"
        :aria-label="`去自定义${name}`"
        hover-class="aps-link--pressed"
        hover-stay-time="80"
        @tap="emit('request-custom')"
      >没找到？去自定义</text>
      <view v-if="valueType === 'multi'" class="aps-actions">
        <text
          class="aps-clear"
          role="button"
          aria-label="清空全部"
          hover-class="aps-clear--pressed"
          hover-stay-time="80"
          @tap="onClear"
        >清空全部</text>
        <view
          class="aps-confirm"
          role="button"
          aria-label="确认选择"
          hover-class="aps-confirm--pressed"
          hover-stay-time="80"
          @tap="onConfirm"
        >
          <text class="aps-confirm-text">确认</text>
        </view>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
/**
 * AttributePickerSheet —— 属性「更多候选」弹层（R41）
 *
 * 由 `AttributeGroup` 在候选总数 > 常驻档时唤起；`BaseSheet` 的 `scroll-body` 承接候选超量内滚
 * （内容贴顶不占空，少量候选不会出现大片空白 —— 优于本页旧方案的定高内滚面板）。
 */
import { computed, ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import IconSvg from '@/components/IconSvg.vue'
import TagChip from './TagChip.vue'
import { COLOR_MAP } from '@/theme/tokens'

/**
 * 搜索框启用阈值（UI 稿 R41）：候选总数 ≥ 12 才给搜索 ——
 * 低于该值时「扫一眼」比「打字」更快，也与主表单「按数量决定收纳」的规则同源。
 */
const SEARCH_MIN = 12

const props = withDefaults(defineProps<{
  /** 显隐（父级 `v-if` 懒挂载后由 `visible` 驱动开合动画） */
  visible: boolean
  /** 维度中文名（标题「选择<维度名>」+ 搜索占位文案） */
  name: string
  /** `single` = 点项即选即关；`multi` = 草稿勾选 + 确认 / 清空 */
  valueType: 'single' | 'multi'
  /** 主表单当前已选（打开时作为草稿初值；单选组据此判断「点当前项 = 不改动」） */
  selected: string[]
  /** 全部候选（编辑端点 `options`，仅提示、不构成约束） */
  candidates: string[]
  /** 选中态视觉档（随维度 `valueType` 由父级下发，与主表单两套视觉同源） */
  selectedStyle?: 'soft' | 'solid'
}>(), {
  visible: false,
  selectedStyle: 'soft',
})

const emit = defineEmits<{
  /** 关闭（遮罩 / 下滑 / 关闭钮由 BaseSheet 统一上抛；确认与单选点项后也一并上抛） */
  (e: 'close'): void
  /** 回抛**新的**已选数组（父级转发 change；本组件不改 props） */
  (e: 'update', next: string[]): void
  /** 「去自定义」：请求父级关弹层并聚焦该组自定义输入框 */
  (e: 'request-custom'): void
}>()

/** 弹层内草稿（仅多选组有意义；关闭即丢弃 ⇒ 永不污染主表单） */
const draft = ref<string[]>([...props.selected])
/** 搜索关键词 + 聚焦态（下划线轻量样式，与主表单同一输入语言） */
const keyword = ref('')
const focused = ref(false)

/** 每次打开重新以主表单当前值为草稿初值并清搜索词 ⇒ 打开即回显、不残留上次会话 */
watch(
  () => props.visible,
  (open) => {
    if (!open) return
    draft.value = [...props.selected]
    keyword.value = ''
  },
)

/** 候选总数达阈值才渲染搜索框（阈值判据用**总数**而非过滤后条数） */
const showSearch = computed(() => props.candidates.length >= SEARCH_MIN)

/** 过滤后的候选（候选为中文值，只做子串包含，无大小写问题） */
const shownCandidates = computed(() => {
  const kw = keyword.value.trim()
  return kw ? props.candidates.filter((c) => c.includes(kw)) : props.candidates
})

/** 点候选：单选 = 选中并立即回抛 + 关闭；多选 = 草稿内 toggle（确认才生效） */
function onPick(value: string) {
  if (props.valueType === 'single') {
    // 点当前已选项 ⇒ 视为「无改动」：不产生 diff、也不在弹层内清空（清空只走主表单删除叉）
    if (props.selected[0] !== value) emit('update', [value])
    emit('close')
    return
  }
  draft.value = draft.value.includes(value)
    ? draft.value.filter((x) => x !== value)
    : [...draft.value, value]
}

/** 确认（多选）：草稿回抛后关闭 */
function onConfirm() {
  emit('update', [...draft.value])
  emit('close')
}

/** 清空全部（多选）：语义是「动作」而非「草稿」，故直接回抛空数组并关闭，不要求再点确认 */
function onClear() {
  emit('update', [])
  emit('close')
}

/** 搜索输入（uni input 事件对象运行时透传，形参取 `Event` 后结构化收窄，避免 `any` 逃逸） */
function onKeyword(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  keyword.value = detail?.value ?? ''
}
</script>

<style scoped>
/* BaseSheet 的 scroll-body 分支已带 `padding: md lg (lg + safe-area)` ⇒ 本层不再叠横向 / 底部留白
   （与 ReviewComposer 同源口径，避免双重缩进） */
.aps-search { margin-top: var(--spacing-2xs); }
.aps-search-box {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 88rpx;
  border-bottom: 2rpx solid var(--border-color);
  box-sizing: border-box;
  transition: border-color var(--duration-fast) var(--ease-out);
}
.aps-search-box--focus { border-bottom-color: var(--color-primary); }
.aps-search-input { flex: 1 1 auto; min-width: 0; height: 100%; font-size: var(--font-aux); color: var(--text-primary); }
.aps-search-ph { color: var(--text-placeholder); }
/* 清空搜索钮：88rpx 见方（弹层内是独立可点件，不适用主表单 chip 例外）；负外边距把它压回输入框右缘内侧 */
.aps-search-clear {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  margin-right: -88rpx;
  -webkit-tap-highlight-color: transparent;
}
.aps-search-clear--pressed { opacity: 0.5; }

/* 候选全览：与主表单**同一 chip 控件**（禁在弹层内换一套选择器形态）；
   `size="lg"` ⇒ 弹层内 chip 视觉高 ≥88rpx（44pt），主表单的 67rpx 例外不适用到这里 */
.aps-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
}
.aps-empty { font-size: var(--font-aux); color: var(--text-placeholder); }

/* 底部条：左「去自定义」文字入口，右多选按钮组；可点件恒 ≥88rpx */
.aps-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
}
.aps-link {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  font-size: var(--font-aux);
  color: var(--color-primary-text);
  -webkit-tap-highlight-color: transparent;
}
.aps-link--pressed { opacity: 0.7; }
.aps-actions { display: flex; align-items: center; gap: var(--spacing-md); }
.aps-clear {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  -webkit-tap-highlight-color: transparent;
}
.aps-clear--pressed { opacity: 0.6; }
/* 确认：主色实心 + 白字（与全站主按钮同色档；弹层内不涉及禁用态 ⇒ 无需复用 .is-disabled） */
.aps-confirm {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 200rpx;
  height: 88rpx;
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.aps-confirm--pressed { opacity: 0.7; }
.aps-confirm-text { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--color-on-primary); }
</style>
