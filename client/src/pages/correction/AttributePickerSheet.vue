<template>
  <!--
    AttributePickerSheet（correction 包内私有）—— 属性维度的**唯一选择场所**（方案①：字段行 + 底部弹层）。

    为什么必须是「唯一场所」：主表单里同一维度**不再有任何可点候选**（既无已选 chip、也无常驻候选），
    ⇒ 不存在「这个能点、那个只能看」的语义分裂；主表单一行 = 一个入口，该维度的增 / 删 / 清空 / 自定义
      全部在这一层完成（低频但要认真填的纠错表单，一致性优先于少一次点按）。

    底座复用公共 `BaseSheet`（遮罩 / grabber / 下滑关闭 / 安全区 / 焦点回收 / `scroll-body` 内滚）；
    本项目 `client/src` 零原生 `<picker>` / `showActionSheet` 先例 ⇒ **禁**绕开 BaseSheet 自建弹层。
    开启 `keyboard-lift`（BaseSheet 的键盘避让开关）⇒ 本弹层内**两个输入框均须 `:adjust-position="false"`**，
    否则「整页上顶」与「抽屉抬升」叠加成双位移（同 R42 决策：弹层内输入不再甩回主表单）。

    职责边界：
    · **只做选择，不做提交**：确认 / 清空 / 单选点项 → `emit('update', next)` 回抛**新数组**，
      由 `AttributeGroup` 转发 `change(fieldKey, next)`，表单值真源恒在 `useCorrection`；
    · **草稿态（多选）**：勾选只写本地 `draft`，点【确认】才回抛；关闭 / 遮罩 / 下滑 ⇒ **草稿丢弃**；
    · **单选无确认按钮**：点候选 / 添加自定义 = 选中并立即回抛 + 关闭；清空走底部「不填该项」；
    · **搜索仅本地过滤**：候选由编辑端点一次性下发（全库已用值去重），**禁**在弹层内再发请求。
  -->
  <BaseSheet :visible="visible" :title="`选择${name}`" closable scroll-body keyboard-lift @close="emit('close')">
    <!-- ① 搜索框：候选**总数** ≥ SEARCH_MIN 才渲染（判据用总数 ⇒ 打字过滤到空也不会让搜索框消失） -->
    <view v-if="showSearch" class="aps-search">
      <view class="aps-search-box" :class="{ 'aps-search-box--focus': searchFocused }">
        <input
          class="aps-search-input"
          :value="keyword"
          :placeholder="`搜索${name}候选`"
          placeholder-class="aps-search-ph"
          maxlength="10"
          confirm-type="search"
          :cursor-spacing="40"
          :adjust-position="false"
          @input="onKeyword"
          @focus="searchFocused = true"
          @blur="searchFocused = false"
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

    <!-- ② 该维度无参考候选（编辑端点 options 为空 / 候选接口失败）⇒ 明示只剩手动输入，不暗改行为 -->
    <text v-if="!candidates.length" class="aps-hint">该维度暂无参考候选，可直接手动输入</text>

    <!-- ③ 自定义添加的值（不在候选池里）：恒可见、**不参与搜索过滤**（打字时不该找不到自己刚填的值）；
         点 = 取消该值（多选退草稿 / 单选即清空并关闭） -->
    <view v-if="customValues.length" class="aps-group">
      <text class="aps-group-label">已添加</text>
      <view class="aps-chips">
        <TagChip
          v-for="v in customValues"
          :key="v"
          :label="v"
          variant="selected"
          checkable
          :selected-style="selectedStyle"
          :aria-label="`取消${name}「${v}」`"
          @pick="onPick(v)"
        />
      </view>
    </view>

    <!-- ④ 候选全览（含已选项，选中态带对勾）：勾选态在 `rows` 里算好后下发（WXML 侧只做属性取值） -->
    <view v-if="rows.length" class="aps-chips aps-chips--main">
      <TagChip
        v-for="row in rows"
        :key="row.value"
        :label="row.value"
        checkable
        :variant="row.checked ? 'selected' : 'candidate'"
        :selected-style="selectedStyle"
        :aria-label="`${name}「${row.value}」`"
        @pick="onPick(row.value)"
      />
    </view>
    <text v-else-if="candidates.length" class="aps-hint">没有匹配项，可清空搜索或改手动输入</text>

    <!-- ⑤ 手动输入（恒在）：候选没命中 / 候选为空时的唯一出口；自定义入口全页仅此一处 -->
    <view class="aps-custom">
      <view
        v-if="!customOpen"
        class="aps-custom-entry"
        role="button"
        :aria-label="`手动输入${name}`"
        hover-class="aps-custom-entry--pressed"
        hover-stay-time="80"
        @tap="openCustom"
      >
        <IconSvg name="plus" :size="28" :color="COLOR_MAP['primary-text']" />
        <text class="aps-custom-entry-text">手动输入{{ name }}</text>
      </view>
      <view v-else class="aps-custom-box" :class="{ 'aps-custom-box--focus': customFocused }">
        <input
          class="aps-custom-input"
          :value="customDraft"
          :placeholder="`输入自定义${name}`"
          placeholder-class="aps-custom-ph"
          :maxlength="CUSTOM_MAX"
          confirm-type="done"
          :cursor-spacing="40"
          :adjust-position="false"
          :focus="customFocus"
          @input="onCustomInput"
          @confirm="addCustom"
          @focus="customFocused = true"
          @blur="customFocused = false"
        />
        <!-- 「添加」仅在草稿非空时出现（有内容才给确认 affordance；回车等价）。
             视觉 56rpx，命中区经 ::after 纵向透明扩展撑满 88rpx（不改变视觉尺寸）。 -->
        <view
          v-if="customDraft.trim()"
          class="aps-custom-add"
          role="button"
          :aria-label="`添加${name}`"
          hover-class="aps-custom-add--pressed"
          hover-stay-time="80"
          @tap="addCustom"
        >
          <text class="aps-custom-add-text">添加</text>
        </view>
      </view>
    </view>

    <!-- ⑥ 底部条：单选 = 「不填该项」（清空也必须能在弹层里做完）；多选 = 清空全部 + 确认（带已选数） -->
    <view v-if="showFoot" class="aps-foot">
      <text
        v-if="canClear"
        class="aps-clear"
        role="button"
        :aria-label="valueType === 'single' ? `不填${name}` : '清空全部'"
        hover-class="aps-clear--pressed"
        hover-stay-time="80"
        @tap="onClear"
      >{{ valueType === 'single' ? '不填该项' : '清空全部' }}</text>
      <view v-if="valueType === 'multi'" class="aps-actions">
        <view
          class="aps-confirm"
          role="button"
          :aria-label="confirmText"
          hover-class="aps-confirm--pressed"
          hover-stay-time="80"
          @tap="onConfirm"
        >
          <text class="aps-confirm-text">{{ confirmText }}</text>
        </view>
      </view>
    </view>
  </BaseSheet>
</template>
<script setup lang="ts">
/**
 * AttributePickerSheet —— 属性维度选择弹层（该维度的**唯一**选择场所）
 *
 * 由 `AttributeGroup` 的字段行唤起（**无条件**：候选为空时同样要能进来手动输入）；
 * `BaseSheet` 的 `scroll-body` 承接候选超量内滚（内容贴顶不占空 ⇒ 少量候选不会大片留白）。
 * 颜色全走语义 token；图标走 `IconSvg`；事件统一 `@tap`；按压用 hover-class 透明度微降。
 */
import { computed, nextTick, ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import IconSvg from '@/components/IconSvg.vue'
import TagChip from './TagChip.vue'
import { COLOR_MAP } from '@/theme/tokens'

/**
 * 搜索框启用阈值：候选总数 ≥ 12 才给搜索 —— 低于该值「扫一眼」比「打字」更快。
 * 候选由编辑端点一次性下发（全库已用值去重）⇒ **仅本地子串过滤，禁在弹层内再发请求**。
 */
const SEARCH_MIN = 12
/** 自定义值长度上限（属性值是短词，超长多半是误填；与旧主表单自定义输入同档） */
const CUSTOM_MAX = 10

const props = withDefaults(defineProps<{
  /** 显隐（父级 `v-if` 懒挂载后由 `visible` 驱动开合动画） */
  visible: boolean
  /** 维度中文名（标题「选择<维度名>」+ 搜索 / 自定义占位文案的词根） */
  name: string
  /** `single` = 点项即选即关；`multi` = 草稿勾选 + 确认 / 清空 */
  valueType: 'single' | 'multi'
  /** 主表单当前已选（打开时作为草稿初值；单选组据此判断「点当前项 = 不改动」） */
  selected: string[]
  /** 全部候选（编辑端点 `options`，仅提示、不构成约束；为空 ⇒ 本层只剩手动输入） */
  candidates: string[]
  /** 选中态视觉档（随维度 `valueType` 由父级下发，与弹层内 chip 两套视觉同源） */
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
}>()

/** 弹层内草稿（仅多选组有意义；关闭即丢弃 ⇒ 永不污染主表单） */
const draft = ref<string[]>([...props.selected])
/** 搜索关键词 + 聚焦态（下划线轻量样式，与主表单同一输入语言） */
const keyword = ref('')
const searchFocused = ref(false)
/** 自定义输入：折叠入口 / 展开后的草稿 / 聚焦态（`customFocus` 才真正驱动 `<input :focus>`） */
const customOpen = ref(false)
const customDraft = ref('')
const customFocused = ref(false)
const customFocus = ref(false)

/** 每次打开都回到「主表单当前值」：回显即时、且不残留上次未确认的草稿 */
watch(
  () => props.visible,
  (open) => {
    if (!open) return
    draft.value = [...props.selected]
    keyword.value = ''
    resetCustom()
  },
)

/** 当前生效集合：多选读草稿（弹层内实时反馈），单选读主表单值（点项即写即关，无需草稿） */
const current = computed(() => (props.valueType === 'multi' ? draft.value : props.selected))
/**
 * 候选行的勾选态**在 JS 侧算好**再下发（`{ value, checked }`）：WXML 侧只做属性取值、
 * 不在模板里调函数 ⇒ 跨端渲染一致，也省掉每次渲染重复 `includes`。
 */
const rows = computed(() => {
  const kw = keyword.value.trim()
  return props.candidates
    .filter((c) => !kw || c.includes(kw))
    .map((value) => ({ value, checked: current.value.includes(value) }))
})
/** 自定义值（不在候选池里的已选）：恒可见、**不参与搜索过滤**；多选取草稿、单选取当前值 */
const customValues = computed(() => current.value.filter((v) => !props.candidates.includes(v)))
/** 候选总数达阈值才渲染搜索框（判据用**总数**而非过滤后条数 ⇒ 打字时搜索框不闪没） */
const showSearch = computed(() => props.candidates.length >= SEARCH_MIN)
/** 有值才给「清空 / 不填」；多选行恒渲染（【确认】是主操作，不能因为 0 项就消失） */
const canClear = computed(() => current.value.length > 0)
const showFoot = computed(() => props.valueType === 'multi' || canClear.value)
/** 确认按钮带已选数：让「改了没 / 改了几个」在关层前可见（仅多选组有确认按钮） */
const confirmText = computed(() => (draft.value.length ? `确认 · 已选 ${draft.value.length}` : '确认'))

/** 点候选 / 点自定义值：单选 = 选中并立即回抛 + 关闭；多选 = 草稿内 toggle（确认才生效） */
function onPick(value: string) {
  if (props.valueType === 'single') {
    // 点「当前项」⇒ 视为无改动（不产生 diff），但仍关闭弹层：点这一下的语义就是「就它了 / 算了离开」
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

/**
 * 清空（多选 =「清空全部」/ 单选 =「不填该项」）：语义是**动作**而非草稿，
 * 故直接回抛空数组并关闭，不要求再点一次确认；「该维度不填」在弹层内就能做完，
 * 无需回主表单找删除叉（主表单已无删除叉）。
 */
function onClear() {
  emit('update', [])
  emit('close')
}

/** 展开自定义输入并聚焦（`nextTick` 等 `<input>` 真实挂载后再置 `focus`，否则属性打不上） */
function openCustom() {
  customOpen.value = true
  nextTick(() => {
    customFocus.value = true
  })
}

/** 收起自定义输入（连同聚焦请求一并撤销，避免下次展开时抢焦点） */
function resetCustom() {
  customOpen.value = false
  customDraft.value = ''
  customFocus.value = false
  customFocused.value = false
}

/**
 * 添加自定义值：去空格后非空、自动去重（已存在 ⇒ 静默不重复添加）。
 * · `single` = 替换当前值并关闭（与点候选同一语义）；
 * · `multi` = 追加进草稿并**保留输入框展开**（连填几个是常态），仅清空文本。
 */
function addCustom() {
  const value = customDraft.value.trim()
  if (!value) return
  if (props.valueType === 'single') {
    if (props.selected[0] !== value) emit('update', [value])
    resetCustom()
    emit('close')
    return
  }
  if (!draft.value.includes(value)) draft.value = [...draft.value, value]
  customDraft.value = ''
}

/** 搜索输入（uni input 事件对象运行时透传，形参取 `Event` 后结构化收窄，避免 `any` 逃逸） */
function onKeyword(e: Event) {
  keyword.value = (e as unknown as { detail?: { value?: string } })?.detail?.value ?? ''
}

/** 自定义值输入（同上口径） */
function onCustomInput(e: Event) {
  customDraft.value = (e as unknown as { detail?: { value?: string } })?.detail?.value ?? ''
}
</script>
<style scoped lang="scss">
/* 下划线字段行样式来自共享 partial（本包内同源） */
@use './field-shared' as field;
/* BaseSheet 的 scroll-body 分支已带 `padding: md lg (lg + safe-area)` ⇒ 本层不再叠横向 / 底部留白
   （与 ReviewComposer 同源口径，避免双重缩进）。
   ⚠️ 本层两个输入框都带 `:adjust-position="false"` ⇒ 键盘避让统一由 BaseSheet 的 `keyboard-lift` 承接。 */
.aps-search { margin-top: var(--spacing-2xs); }
.aps-search-box {
  @include field.underline;
}
.aps-search-box--focus { border-bottom-color: var(--color-primary); }
.aps-search-input { flex: 1 1 auto; min-width: 0; height: 100%; font-size: var(--font-aux); color: var(--text-primary); }
.aps-search-ph { color: var(--text-placeholder); }
/* 清空搜索钮：88rpx 见方（弹层内是独立可点件）；负外边距把它压回输入框右缘内侧，不额外占宽 */
.aps-search-clear {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
  margin-right: -88rpx;
  -webkit-tap-highlight-color: transparent;
}
.aps-search-clear--pressed { opacity: 0.6; }

/* 引导 / 空态提示：最小字档 + 占位灰（不做成按钮，避免与真入口争注意力） */
.aps-hint {
  display: block;
  margin-top: var(--spacing-sm);
  font-size: var(--font-tiny);
  color: var(--text-placeholder);
}

/* 自定义值区（「已添加」）：与候选区同一 chip 语言，只多一行分组小标题 ⇒ 一眼分清「这不是候选，是我填的」 */
.aps-group { margin-top: var(--spacing-md); }
.aps-group-label {
  display: block;
  margin-bottom: var(--spacing-2xs);
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
}

/* 候选全览：与自定义值区**同一 chip 控件**（禁在弹层内换一套选择器形态）；
   chip 视觉高恒 88rpx（由 `TagChip` 内聚），行距由 `--spacing-sm` 补足 */
.aps-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.aps-chips--main { margin-top: var(--spacing-md); }
/* 手动输入区：折叠态 = 一行「＋ 手动输入<维度名>」入口（iOS / Android 设置的「其他…」惯例）；
   展开态 = 下划线输入框 + 「添加」，与主表单输入语言一致。行高 88rpx ⇒ 可点可打都不误触 */
.aps-custom { margin-top: var(--spacing-lg); }
.aps-custom-entry {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  height: var(--tap-target-size);
  -webkit-tap-highlight-color: transparent;
}
.aps-custom-entry--pressed { opacity: 0.6; }
.aps-custom-entry-text { font-size: var(--font-aux); color: var(--color-primary-text); }
.aps-custom-box {
  @include field.underline;
}
.aps-custom-box--focus { border-bottom-color: var(--color-primary); }
.aps-custom-input { flex: 1 1 auto; min-width: 0; height: 100%; font-size: var(--font-aux); color: var(--text-primary); }
.aps-custom-ph { color: var(--text-placeholder); }
/* 「添加」：视觉高 56rpx，命中区经 ::after 纵向透明扩展撑满 88rpx（不改变视觉尺寸，纯文字钮才够触达） */
.aps-custom-add {
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 var(--spacing-2xs);
  -webkit-tap-highlight-color: transparent;
}
.aps-custom-add::after {
  content: '';
  position: absolute;
  top: 50%;
  right: calc(var(--spacing-2xs) * -1);
  left: calc(var(--spacing-2xs) * -1);
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
.aps-custom-add--pressed { opacity: 0.6; }
.aps-custom-add-text {
  font-size: var(--font-aux);
  font-weight: var(--weight-medium);
  color: var(--color-primary-text);
}

/* 底部条：左「清空 / 不填」（次级文字入口），右【确认】主操作；可点件恒 ≥88rpx */
.aps-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
}
.aps-clear {
  display: flex;
  align-items: center;
  min-height: var(--tap-target-size);
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  -webkit-tap-highlight-color: transparent;
}
.aps-clear--pressed { opacity: 0.6; }
/* 单选组底部只有左侧「不填该项」⇒ 无须居中补偿；多选组把按钮推到右端（space-between 单子项会靠左） */
.aps-actions { display: flex; align-items: center; margin-left: auto; }
/* 确认：主色实心 + 白字（与全站主按钮同色档；弹层内不涉及禁用态 ⇒ 无需复用 .is-disabled） */
.aps-confirm {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 240rpx;
  height: var(--tap-target-size);
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  -webkit-tap-highlight-color: transparent;
}
.aps-confirm--pressed { opacity: 0.85; }
.aps-confirm-text {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--color-on-primary);
}
</style>