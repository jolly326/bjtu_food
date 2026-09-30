<template>
  <!--
    AttributeGroup（correction 包内私有，**核心复用**）—— 单个「描述属性维度」的编辑分组。

    数据驱动：维度名 / 当前值 / 单多选取自详情，候选取自编辑态端点（端上零硬编码维度）。
    三段结构（自上而下）：
      ① 已选中标签区（常驻，永远可见）+ 右上角删除叉；
      ② 候选折叠面板（默认折叠；候选 ≤4 时**默认直接展开、不显示折叠按钮**；≥5 才给「查看更多候选」入口）；
         **候选为空（无参考值 / 接口失败）⇒ 整个候选入口隐藏**，仅留自定义输入；
      ③ 自定义输入（回车或「添加」入已选；自动去重、禁空 / 纯空格、≤10 字；`single` 为替换）。

    `first`（P1 返工）：组间距**不再**用 `.ag:first-child` —— `.ag` 是子组件根节点，mp-weixin 下每个
    实例都被包在各自的宿主节点里 ⇒ `:first-child` 会对**每一组**命中，组间距整体归零、分组不可辨。
    改为父级 `v-for` 按 index 显式传 `first`（仅首组去上边距），确定性生效、跨端一致。
  -->
  <view class="ag" :class="{ 'ag--first': first }">
    <!-- 分组标题 + 单值维度小字说明 -->
    <view class="ag-head">
      <text class="ag-name">{{ name }}</text>
      <text v-if="valueType === 'single'" class="ag-tip">仅可选择一项</text>
    </view>
    <text class="ag-sub">点击标签增删，支持自定义输入</text>

    <!-- ① 已选中标签区（常驻）：标签体**不可点**（删除只走右上角叉），故不下发 pickable -->
    <view class="ag-selected">
      <TagChip
        v-for="v in selected"
        :key="v"
        variant="selected"
        closable
        :pickable="false"
        :label="v"
        :aria-label="`删除${name}「${v}」`"
        @remove="removeSelected(v)"
      />
      <text v-if="!selected.length" class="ag-placeholder">暂无，可从下方选择或直接输入</text>
    </view>

    <!-- ② 候选折叠面板（默认折叠 / ≤4 项直接展开；用户展开的长列表定高 240rpx 内滚） -->
    <view v-if="hasCandidates && panelOpen" class="ag-panel-box">
      <scroll-view class="ag-panel" :class="{ 'ag-panel--fixed': !autoExpand }" scroll-y :show-scrollbar="false">
        <view class="ag-chips">
          <TagChip
            v-for="c in candidates"
            :key="c"
            :variant="selected.includes(c) ? 'selected' : 'candidate'"
            :label="c"
            :aria-label="`${name}「${c}」`"
            @pick="toggleCandidate(c)"
          />
        </view>
      </scroll-view>
    </view>

    <!-- ③ 候选入口（仅候选 > 4 项时给）+ 自定义输入 -->
    <view class="ag-entry">
      <view
        v-if="hasCandidates && !autoExpand"
        class="ag-more"
        role="button"
        :aria-label="panelOpen ? `收起${name}候选` : `查看更多${name}候选`"
        :aria-expanded="panelOpen ? 'true' : 'false'"
        hover-class="ag-more--pressed"
        hover-stay-time="80"
        @tap="expanded = !expanded"
      >
        <text class="ag-more-text">{{ panelOpen ? `收起候选（共 ${candidates.length} 项）` : `查看更多候选（共 ${candidates.length} 项）` }}</text>
        <IconSvg :name="panelOpen ? 'arrow-up' : 'arrow-down'" :size="24" :color="COLOR_MAP['text-tertiary']" />
      </view>

      <view class="ag-input-wrap" :class="{ 'ag-input-wrap--focus': focused }">
        <input
          class="ag-input"
          :value="draft"
          :placeholder="`输入自定义${name}，回车添加`"
          placeholder-class="ag-input-ph"
          maxlength="10"
          confirm-type="done"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onInput"
          @confirm="addCustom"
          @focus="focused = true"
          @blur="focused = false"
        />
        <!-- 「添加」按钮仅在草稿非空时出现：有内容才给确认 affordance（回车等价）。
             视觉 56rpx，命中区经 ::after 纵向透明扩展撑满所在行 88rpx（不改变视觉尺寸）。 -->
        <view
          v-if="draft.trim()"
          class="ag-add"
          role="button"
          :aria-label="`添加${name}`"
          hover-class="ag-add--pressed"
          hover-stay-time="80"
          @tap="addCustom"
        >
          <text class="ag-add-text">添加</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * AttributeGroup —— 单维度属性分组（选中区 + 候选折叠 + 自定义输入）
 *
 * 接口口径（UI 稿「`AttributeGroup` 入参」）：
 * `props: { fieldKey, name, valueType, selected, candidates }`，
 * `emits: change(fieldKey, selected)` —— **本组件不直接改 props**，一律回抛新数组由父级写回，
 * 保证「表单值唯一真源在父级编排（useCorrection）」。
 *
 * 候选仅为**提示**：自定义输入恒可用，且不像候选池回写任何东西。
 * 颜色全走语义 token；图标走 `IconSvg`（禁 emoji / 文本 / `content:'+'` 当图标）；
 * 按压用 hover-class 透明度微降（禁 `transform: scale`）。
 */
import { computed, ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import TagChip from './TagChip.vue'
import { COLOR_MAP } from '@/theme/tokens'

/** 候选数 ≤ 该值时默认直接展开（不给折叠入口）：少量候选折叠反而多一次点击 */
const AUTO_EXPAND_MAX = 4

const props = withDefaults(defineProps<{
  /** 维度键（camelCase）＝ 提交时 `attributes` 的键 */
  fieldKey: string
  /** 分组名（维度中文名，如「主料」） */
  name: string
  /** `single`（单选，仅可选择一项）｜ `multi`（多选） */
  valueType: 'single' | 'multi'
  /** 当前已选（预填原始值，可增删） */
  selected: string[]
  /** 参考候选（来自编辑态端点；为空 = 无候选 ⇒ 隐藏候选入口） */
  candidates: string[]
  /** 是否首个分组（由父级 `v-for` 按 index 显式传入；首组去上边距） */
  first?: boolean
}>(), {
  first: false,
})

const emit = defineEmits<{
  /** 选区变化：回抛**新的**已选数组（父级写回，组件不改 props） */
  (e: 'change', fieldKey: string, selected: string[]): void
}>()

/** 候选面板是否展开（仅 `candidates.length > AUTO_EXPAND_MAX` 时由用户控制） */
const expanded = ref(false)
/** 少量候选直接展开：无折叠按钮，也无需用户点开 */
const autoExpand = computed(() => props.candidates.length > 0 && props.candidates.length <= AUTO_EXPAND_MAX)
const panelOpen = computed(() => autoExpand.value || expanded.value)
/** 候选为空（无参考值 / 接口失败）⇒ 候选入口整体隐藏，仅留自定义输入 */
const hasCandidates = computed(() => props.candidates.length > 0)

/** 自定义输入草稿 + 聚焦态（聚焦时底线切主色） */
const draft = ref('')
const focused = ref(false)

/**
 * input @input 回调（平台例外：uni input 事件对象由运行时透传，形参取 `Event` 后结构化收窄，避免 `any` 逃逸）。
 */
function onInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  draft.value = detail?.value ?? ''
}

/**
 * 候选点选：
 * · `multi` —— 未选则加入、已选则取消（toggle）；
 * · `single` —— 取代已选（**仅保留 1 个**）；再点当前项 = 取消选择（允许清空该维度）。
 */
function toggleCandidate(value: string) {
  if (props.valueType === 'single') {
    emit('change', props.fieldKey, props.selected[0] === value ? [] : [value])
    return
  }
  emit(
    'change',
    props.fieldKey,
    props.selected.includes(value)
      ? props.selected.filter((x) => x !== value)
      : [...props.selected, value],
  )
}

/** 删除某一项（已选中区右上角叉） */
function removeSelected(value: string) {
  emit('change', props.fieldKey, props.selected.filter((x) => x !== value))
}

/** 自定义输入：去空格后非空、自动去重（已存在则视为已选，不再重复）；`single` 为替换 */
function addCustom() {
  const value = draft.value.trim()
  if (!value) return
  if (props.valueType === 'single') {
    if (props.selected[0] !== value) emit('change', props.fieldKey, [value])
  } else if (!props.selected.includes(value)) {
    emit('change', props.fieldKey, [...props.selected, value])
  }
  draft.value = ''
}
</script>

<style scoped>
/* 组间距：`.ag` 恒 spacing-lg，**首组**由 `--first` 去掉上边距（不再用 :first-child 跨组件选择器） */
.ag { margin-top: var(--spacing-lg); }
.ag--first { margin-top: 0; }

/* 分组标题（维度名）+ 单值小字说明 */
.ag-head { display: flex; align-items: baseline; gap: var(--spacing-xs); }
.ag-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.ag-tip { font-size: var(--font-tiny); color: var(--text-tertiary); }
.ag-sub { display: block; margin-top: var(--spacing-2xs); font-size: var(--font-tiny); color: var(--text-tertiary); }

/* ===== ① 已选中标签区（常驻）=====
   间距 sm(16rpx) + chip 视觉高 ≈67rpx（文字行 30.8 + 内边距 32 + 描边 4）⇒ 行节距 ≈83rpx（≥64rpx 返工口径） */
.ag-selected {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  min-height: 56rpx;
  margin-top: var(--spacing-sm);
}
.ag-placeholder { font-size: var(--font-aux); color: var(--text-placeholder); }

/* ===== ② 候选折叠面板：底比卡片略深一档、最多 240rpx 后纵向滚动 =====
   `max-height` 在 mp-weixin 下**不保证**给 scroll-view 定高（无定高则内滚失效）：
   故「用户展开」的长候选列表（>4 项）额外走 `.ag-panel--fixed` 定高，
   而 ≤4 项自动展开的短列表保留 max-height（引擎不认时退化为自撑高度，内容全可见、不丢候选）。 */
.ag-panel-box {
  margin-top: var(--spacing-xs);
  padding: var(--spacing-xs);
  background: var(--bg-input);
  border-radius: var(--radius-xs);
  box-sizing: border-box;
}
.ag-panel { max-height: 240rpx; }
.ag-panel--fixed { height: 240rpx; }
.ag-chips { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); padding: var(--spacing-2xs); }

/* ===== ③ 候选入口 + 自定义输入（同一行；窄屏自动换行）=====
   两个可点件（折叠入口 / 输入行）统一 88rpx = 44pt 触达下限 */
.ag-entry {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-sm);
}
.ag-more {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  height: 88rpx;
  padding: 0 var(--spacing-sm);
  background: var(--bg-input);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.ag-more--pressed { opacity: 0.7; }
.ag-more-text { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 自定义输入：下划线轻量样式（与基础信息表单同一输入语言，禁全包围框）；
   行高 88rpx = 44pt（Apple 触达下限） */
.ag-input-wrap {
  flex: 1 1 300rpx;
  min-width: 300rpx;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 88rpx;
  border-bottom: 2rpx solid var(--border-color);
  box-sizing: border-box;
  transition: border-color var(--duration-fast) var(--ease-out);
}
.ag-input-wrap--focus { border-bottom-color: var(--color-primary); }
.ag-input {
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  font-size: var(--font-aux);
  color: var(--text-primary);
}
.ag-input-ph { color: var(--text-placeholder); }
/* 「添加」：文字按钮（非图标），有草稿时才出现；视觉 56rpx，命中区经 ::after 撑满所在行 */
.ag-add {
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 var(--spacing-sm);
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  -webkit-tap-highlight-color: transparent;
}
/* 命中区扩展：上下各 16rpx（56 + 32 = 88rpx = 44pt），仅纵向、不侵占输入框横向区域 */
.ag-add::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: -16rpx;
  bottom: -16rpx;
}
.ag-add--pressed { opacity: 0.7; }
.ag-add-text { font-size: var(--font-tiny); font-weight: var(--weight-medium); color: var(--color-primary-text); }
</style>
