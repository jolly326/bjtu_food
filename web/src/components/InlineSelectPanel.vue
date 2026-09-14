<script setup lang="ts">
/**
 * InlineSelectPanel：自绘下拉选择面板（§4.2 自封装组件）。
 *
 * 为什么不用原生 select / el-select：
 *  `content-management` 收敛后，菜品表单的「所属食堂 / 所属档口」需要在**面板底部固定一个
 *  「+ 新增档口 / + 新增食堂」入口**。原生 <select> 无法承载自定义底部项；
 *  el-select 的 el-option 列表也无法自然固定在面板底部（见 docs/loop/design/dish-entry-flow.md §4.1）。
 *  故用 el-popover + 自绘列表：选项区（可滚动）+ 1px 分隔线 + 底部固定新增项。
 *
 * 交互与可达性：
 *  - 点外部关闭（el-popover 默认）、Esc 关闭、选中后关闭；
 *  - 键盘：Tab 聚焦触发器 → Enter/Space 展开；面板内 ↑/↓ 移动、Enter 选中、Esc 关闭；
 *  - 语义：触发器 aria-haspopup="listbox" / aria-expanded，列表 role="listbox"，选项 role="option"；
 *  - renamable（Q-113/Q-115）：选项行右侧追加改名入口（独立按钮，不参与 ↑/↓ 导航），
 *    点击后收起面板并 emit rename，由调用方弹改名弹窗。
 */
import { ref, computed, nextTick, useId } from 'vue'
import { ElPopover } from 'element-plus'
import { Plus, Check, EditPen } from '@element-plus/icons-vue'

interface PanelOption {
  label: string
  value: number | string
}

const props = withDefaults(
  defineProps<{
    /** 当前选中值（'' = 未选） */
    modelValue: number | string
    options: PanelOption[]
    placeholder?: string
    /** 未选上级时禁用（占位文案仍可见） */
    disabled?: boolean
    /** 底部固定新增项文案；为空则不渲染该项 */
    addText?: string
    /** 选项区空态文案（如「该食堂暂无档口」） */
    emptyText?: string
    /**
     * 是否允许「改名」（Q-113/Q-115：食堂/档口是属性字典，生命周期只有新增/改名）。
     * 开启后，每个选项行右侧出现改名入口，点击 emit rename(option)（不选中、不关闭面板）。
     */
    renamable?: boolean
  }>(),
  {
    placeholder: '请选择',
    disabled: false,
    addText: '',
    emptyText: '',
    renamable: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | string]
  add: []
  /** 请求改名：由调用方弹出改名弹窗（本组件不自行处理表单） */
  rename: [option: PanelOption]
}>()

const open = ref(false)
/** 键盘导航高亮下标：-1 = 无高亮（= options.length 时为底部新增项） */
const activeIndex = ref(-1)
const listId = useId()

/** 全部可导航项（选项 + 底部新增项），供 ↑/↓ 与 Enter 统一处理 */
const navigableCount = computed(() => props.options.length + (props.addText ? 1 : 0))

function selectedLabel(): string {
  const hit = props.options.find(o => String(o.value) === String(props.modelValue))
  return hit ? hit.label : ''
}

const hasValue = computed(() => props.modelValue !== '' && props.modelValue !== null && props.modelValue !== undefined)

function toggle() {
  if (props.disabled) return
  open.value = !open.value
}

function onPopoverShow() {
  // 展开时高亮当前选中项，键盘用户可直接 ↑/↓ 调整
  const idx = props.options.findIndex(o => String(o.value) === String(props.modelValue))
  activeIndex.value = idx
}

function close() {
  open.value = false
  activeIndex.value = -1
}

function pick(opt: PanelOption) {
  emit('update:modelValue', opt.value)
  close()
}

function onAdd() {
  open.value = false
  activeIndex.value = -1
  emit('add')
}

/**
 * 改名：请求调用方弹出改名弹窗。先收起面板——否则 el-popover 会浮在弹窗之上，
 * 形成「面板压弹窗」的层级混乱（§4.5 材质/层级一致性）。
 */
function onRename(opt: PanelOption, e: Event) {
  e.stopPropagation()
  close()
  emit('rename', opt)
}

/** 面板内键盘导航：↑/↓ 移动、Enter 选中/开新增、Esc 关闭（Esc 由 el-popover 兜底） */
function onPanelKeydown(e: KeyboardEvent) {
  if (!navigableCount.value) {
    if (e.key === 'Escape') close()
    return
  }
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    const dir = e.key === 'ArrowDown' ? 1 : -1
    activeIndex.value = (activeIndex.value + dir + navigableCount.value) % navigableCount.value
    nextTick(() => {
      const el = document.querySelector<HTMLElement>(`.isp-opt[data-idx="${activeIndex.value}"]`)
      el?.scrollIntoView({ block: 'nearest' })
    })
    return
  }
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    const idx = activeIndex.value
    if (idx >= 0 && idx < props.options.length) pick(props.options[idx]!)
    else if (props.addText && idx === props.options.length) onAdd()
    return
  }
  if (e.key === 'Escape') close()
}
</script>

<template>
  <div class="isp" :class="{ disabled }">
    <ElPopover
      v-model:visible="open"
      trigger="click"
      placement="bottom-start"
      :width="240"
      :disabled="disabled"
      :show-arrow="false"
      popper-class="isp-popper"
      @show="onPopoverShow"
    >
      <template #reference>
        <button
          class="isp-trigger"
          type="button"
          :disabled="disabled"
          aria-haspopup="listbox"
          :aria-expanded="open"
          :aria-controls="listId"
          @click="toggle"
          @keydown.enter.prevent="toggle"
          @keydown.space.prevent="toggle"
          @keydown.down.prevent="!disabled && (open = true)"
        >
          <span class="isp-value" :class="{ placeholder: !hasValue }">{{ hasValue ? selectedLabel() : placeholder }}</span>
          <span class="isp-caret" aria-hidden="true" />
        </button>
      </template>

      <div
        :id="listId"
        class="isp-panel"
        role="listbox"
        tabindex="-1"
        @keydown="onPanelKeydown"
      >
        <div class="isp-list">
          <div
            v-for="(opt, idx) in options"
            :key="String(opt.value)"
            class="isp-row"
            @mouseenter="activeIndex = idx"
          >
            <button
              class="isp-opt"
              :data-idx="idx"
              type="button"
              role="option"
              :aria-selected="String(opt.value) === String(modelValue)"
              :class="{ on: String(opt.value) === String(modelValue), hi: activeIndex === idx }"
              @click="pick(opt)"
            >
              <span class="isp-opt-label">{{ opt.label }}</span>
              <el-icon v-if="String(opt.value) === String(modelValue)" class="isp-check" aria-hidden="true"><Check /></el-icon>
            </button>
            <button
              v-if="renamable"
              class="isp-rename"
              type="button"
              :title="`改名「${opt.label}」`"
              :aria-label="`改名「${opt.label}」`"
              @click="onRename(opt, $event)"
            >
              <el-icon class="isp-rename-ico"><EditPen /></el-icon>
            </button>
          </div>
          <p v-if="!options.length && emptyText" class="isp-empty">{{ emptyText }}</p>
        </div>

        <template v-if="addText">
          <div class="isp-divider" />
          <button
            class="isp-opt isp-add"
            :data-idx="options.length"
            type="button"
            @click="onAdd"
            @mouseenter="activeIndex = options.length"
          >
            <el-icon class="isp-add-ico"><Plus /></el-icon>
            <span class="isp-opt-label">{{ addText }}</span>
          </button>
        </template>
      </div>
    </ElPopover>
  </div>
</template>

<style scoped>
.isp { width: 100%; }

/* 触发器：高度 / 圆角 / 边框与 .field input / select 基线一致（shared.css） */
.isp-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-card);
  font-size: var(--font-base);
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.isp-trigger:hover:not(:disabled) { border-color: var(--color-primary); }
.isp-trigger:focus-visible { outline: none; box-shadow: var(--focus-ring); border-color: var(--color-primary); }
.isp-trigger:active:not(:disabled) { transform: scale(var(--press-scale)); }
.isp-trigger:disabled { cursor: not-allowed; opacity: 0.6; }
.isp.disabled .isp-value { color: var(--text-light); }

.isp-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}
.isp-value.placeholder { color: var(--text-light); }

/* 展开指示（纯 CSS 三角，避免 emoji/图标语义混淆） */
.isp-caret {
  width: 0;
  height: 0;
  flex-shrink: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid var(--text-light);
  transition: transform 0.2s var(--ease-out);
}
</style>

<style>
/* 面板本体（el-popover 内容挂在 body，需非 scoped 样式） */
.isp-popper.el-popover.el-popper {
  padding: 0;
  border-radius: var(--radius);
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  box-shadow: var(--shadow-pop);
  overflow: hidden;
}
.isp-panel { display: flex; flex-direction: column; }
.isp-list { max-height: 240px; overflow-y: auto; padding: var(--space-1); }
/* 选项行：选中按钮 + （可选）改名按钮并排 */
.isp-row { display: flex; align-items: center; gap: var(--space-1); }
.isp-row .isp-opt { flex: 1; width: auto; }
.isp-opt {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  font-size: var(--font-base);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.isp-opt:hover,
.isp-opt.hi { background: var(--bg-soft); }
.isp-opt:active { transform: scale(var(--press-scale)); }
.isp-opt:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.isp-opt.on { color: var(--color-primary); font-weight: var(--weight-medium); }
.isp-opt-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.isp-check { flex-shrink: 0; width: 14px; height: 14px; color: var(--color-primary); }
.isp-empty { margin: 0; padding: var(--space-3); font-size: var(--font-sm); color: var(--text-light); text-align: center; }

/* 改名入口（Q-113/Q-115）：仅在 renamable 时渲染；悬停行内可见，静止时弱化不抢焦点 */
.isp-rename {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  color: var(--text-light);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.isp-rename:hover { background: var(--bg-soft); color: var(--color-primary); }
.isp-rename:active { transform: scale(var(--press-scale)); }
.isp-rename:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.isp-rename-ico { width: 14px; height: 14px; }

/* 底部固定新增项：1px 分隔线隔开，主色文字 */
.isp-divider { height: 1px; background: var(--border-light); }
.isp-add { border-radius: 0; margin: 0; padding: var(--space-3); color: var(--color-primary); font-weight: var(--weight-medium); }
.isp-add:hover { background: var(--color-primary-bg); }
.isp-add-ico { width: 14px; height: 14px; flex-shrink: 0; }
</style>
