<template>
  <view class="gone-form">
    <view class="card">
      <!-- ① 锚定卡（只读）：这道菜是谁 —— 避免用户「反馈错菜」 -->
      <view class="anchor">
        <text class="anchor-name">{{ dishName || '这道菜' }}</text>
        <text v-if="dishLocation" class="anchor-loc">{{ dishLocation }}</text>
      </view>

      <!-- ② 主问题：一句话 + 恒可用提交按钮（**核心：不填任何内容也能提交**） -->
      <view class="main">
        <text class="main-title">这道菜已经没了吗？</text>
        <text class="main-sub">提交后我们会下架它；若仍在售，你收到的回执会说明原因</text>
        <view class="submit-area" @tap="onSubmitTap">
          <AppButton
            :text="submitting ? '提交中…' : '确认提交'"
            :loading="!!submitting"
            @press="emit('submit')"
          />
        </view>
      </view>

      <!-- ③ 选填补充（**默认折叠**）：note + 图片，各自独立可选、都不必填 -->
      <view class="optional">
        <view class="opt-row" @tap="toggle('note')">
          <text class="opt-label">补充说明{{ note ? '（已填）' : '' }}</text>
          <IconSvg :name="noteOpen ? 'arrow-up' : 'arrow-down'" :size="28" />
        </view>
        <view v-if="noteOpen" class="opt-body">
          <textarea
            class="note-input"
            :value="note"
            :maxlength="GONE_NOTE_MAX"
            placeholder="例如：这个窗口换成麻辣香锅了 / 菜还在，挪到隔壁窗口了 / 今天临时没供"
            placeholder-class="note-ph"
            :disabled="!!submitting"
            @input="onNoteInput"
          />
          <text class="note-count">{{ note.length }}/{{ GONE_NOTE_MAX }}</text>
        </view>

        <view class="opt-row" @tap="toggle('image')">
          <text class="opt-label">添加图片{{ images.length ? `（${images.length}）` : '' }}</text>
          <IconSvg :name="imageOpen ? 'arrow-up' : 'arrow-down'" :size="28" />
        </view>
        <view v-if="imageOpen" class="opt-body">
          <ImagePicker
            ref="imagePickerRef"
            :model-value="images"
            :max="GONE_IMAGE_MAX"
            :disabled="!!submitting"
            @update:model-value="onImagesChange"
            @pick="emit('pick-image')"
          />
          <text class="opt-tip">拍一张现在的窗口，管理员不用跑现场就知道是什么情况</text>
        </view>
      </view>

      <!-- 提交说明（不暗示提交即生效：系统不自动下架，由管理员人工判断） -->
      <text class="submit-note">提交不代表立即下架，管理员核实后会处理并回执</text>
      <text v-if="submitError" class="submit-error">{{ submitError }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * GoneForm —— 菜品问题反馈 · **`type=gone`（已经下架）** 表单。
 *
 * 🔴 **三条硬约束**（违反即破坏产品定位，见评审问题 1 决议 5.1.3）：
 * 1. **提交按钮恒可用** —— 不填 note / images 也能提交；
 * 2. **补充区默认折叠** —— 展开即暗示"要填"，抬高提交门槛；
 * 3. **不预填菜品详情** —— 由 `useCorrection` 跳过 `GET /dishes/{id}`（少一次请求 = 更快）。
 *
 * 之所以带 note / images 两个选填项：单靠"一键提交"会把「变成了别的菜」/「换窗口了」/
 * 「今天临时没供」压平成同一信号，而这三种情况的**管理员处置动作完全不同**
 * （补录新菜 / 改档口 / 不下架）—— 只看"N 人反馈已下架"会误下架。
 */
import { ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import { GONE_IMAGE_MAX } from '@/constants/ugc'
import type { PickSource } from '@/components/imagePickSource'

/** `note` 字数上限（与服务端 `CorrectionConst.NOTE_MAX_LENGTH` 同源） */
const GONE_NOTE_MAX = 200

const props = withDefaults(
  defineProps<{
    dishName?: string
    dishLocation?: string
    /** 选填补充说明（≤200 字；v-model 双向） */
    note?: string
    /** 选填图片（≤3 张；v-model 双向） */
    images?: string[]
    submitting?: boolean
    submitError?: string
  }>(),
  {
    dishName: '',
    dishLocation: '',
    note: '',
    images: () => [] as string[],
    submitting: false,
    submitError: '',
  },
)

const emit = defineEmits<{
  (e: 'update:note', value: string): void
  (e: 'update:images', value: string[]): void
  (e: 'submit'): void
  /** 请求选择配图来源（页面根级弹层承接 —— 本组件在 scroll-view 内不能自带 fixed） */
  (e: 'pick-image'): void
}>()

/** 折叠区开合状态（**默认全折叠**，见硬约束 2） */
const noteOpen = ref(false)
const imageOpen = ref(false)

function toggle(which: 'note' | 'image') {
  if (which === 'note') noteOpen.value = !noteOpen.value
  else imageOpen.value = !imageOpen.value
}

/** 说明输入（≤200 字；`maxlength` 已由原生属性兜底，此处只回抛父级模型） */
function onNoteInput(e: Event) {
  const detail = (e as unknown as { detail?: { value?: string } })?.detail
  emit('update:note', detail?.value ?? '')
}

function onImagesChange(urls: string[]) {
  emit('update:images', urls)
}

/**
 * 提交按钮**恒可点**（无门禁）—— 与 `field` 型的 `canSubmit` 不同：
 * gone 型「什么都可以不填」，故不存在"缺什么"的状态。
 * 热区额外拦**提交中**的重复点击（与退避期同口径）。
 */
function onSubmitTap() {
  if (props.submitting) return
  emit('submit')
}

/** ImagePicker 的 startPick 中转（来源弹层在页面根级，故经宿主页透传） */
const imagePickerRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
function startPick(source: PickSource) {
  imagePickerRef.value?.startPick(source)
}
defineExpose({ startPick })
</script>

<style scoped>
.gone-form { padding: 0 var(--page-gutter); }

.card {
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-lg);
}

/* ① 锚定卡（只读）：弱化处理，仅作「我在反馈哪道菜」的定位信息 */
.anchor { display: flex; flex-direction: column; gap: var(--spacing-2xs); padding-bottom: var(--spacing-md); }
.anchor-name { font-size: var(--font-body); color: var(--text-body); font-weight: var(--weight-semibold); }
.anchor-loc { font-size: var(--font-caption); color: var(--text-secondary); }

/* ② 主问题区：标题 + 说明 + 提交 */
.main { display: flex; flex-direction: column; gap: var(--spacing-sm); padding-top: var(--spacing-md); }
.main-title { font-size: var(--font-subtitle); color: var(--text-primary); font-weight: var(--weight-semibold); }
.main-sub { font-size: var(--font-caption); color: var(--text-secondary); }
.submit-area { margin-top: var(--spacing-sm); }

/* ③ 选填补充：折叠行（≥ 88rpx 触达基线）+ 展开体 */
.optional { margin-top: var(--spacing-md); border-top: 1rpx solid var(--border-color); }
.opt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  min-height: var(--tap-target-size);
  color: var(--text-body);
  font-size: var(--font-body);
  -webkit-tap-highlight-color: transparent;
}
.opt-body { padding-bottom: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-xs); }
.opt-tip { font-size: var(--font-caption); color: var(--text-tertiary); }

.note-input {
  width: 100%;
  /* 两倍触达高（176rpx）⇒ 多行说明的起手可见区，不引用未登记裸尺寸 */
  min-height: calc(var(--tap-target-size) * 2);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  font-size: var(--font-body);
  color: var(--text-body);
  box-sizing: border-box;
}
.note-ph { color: var(--text-placeholder); }
.note-count { align-self: flex-end; font-size: var(--font-caption); color: var(--text-tertiary); }

.submit-note {
  display: block;
  margin-top: var(--spacing-md);
  font-size: var(--font-caption);
  color: var(--text-tertiary);
  text-align: center;
}
.submit-error {
  display: block;
  margin-top: var(--spacing-sm);
  font-size: var(--font-caption);
  color: var(--color-error);
}
</style>
