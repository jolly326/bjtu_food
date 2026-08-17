<template>
  <view class="page feedback-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="意见反馈" @back="goBack" />

    <!-- 轻量单视图：写反馈表单（2026-08-17 重设计：任何人都可反馈，无登录守卫、无「我的反馈」Tab；进度追踪后续另做） -->
    <scroll-view class="scroll-wrap write-scroll" scroll-y>
      <view class="tab-pane">
        <!-- 反馈类型 -->
        <CardSection title="反馈类型">
          <view class="type-row">
            <view
              v-for="t in types"
              :key="t.value"
              class="type-chip"
              :class="{ active: type === t.value }"
              hover-class="pressed"
              hover-stay-time="80"
              role="button"
              :aria-label="`反馈类型：${t.label}`"
              @tap="type = t.value"
            >
              <text class="type-text">{{ t.label }}</text>
            </view>
          </view>
          <!-- 内容纠错二级细分：新增菜品 / 信息有误（独立成档，提交均映射 type=error） -->
          <view v-if="type === 'error'" class="type-row sub-row" role="radiogroup" aria-label="内容纠错细分">
            <view
              v-for="a in correctionActions"
              :key="a.key"
              class="type-chip sub-chip"
              :class="{ active: feedbackAction === a.key }"
              hover-class="pressed"
              hover-stay-time="80"
              role="radio"
              :aria-checked="feedbackAction === a.key"
              :aria-label="a.label"
              @tap="feedbackAction = a.key"
            >
              <text class="type-text">{{ a.label }}</text>
            </view>
          </view>
        </CardSection>

        <!-- 反馈对象（选填） -->
        <CardSection title="反馈对象（选填）">
          <view v-if="presetDishName" class="preset-tip">
            <IconSvg name="dish" :size="28" color="var(--color-primary)" />
            <text class="preset-tip-text">已关联：{{ presetDishName }}</text>
            <view class="preset-tip-clear" hover-class="pressed" hover-stay-time="80" role="button" aria-label="移除关联菜品" @tap="clearPreset">
              <text class="preset-tip-clear-text">移除</text>
            </view>
          </view>
          <view class="type-row">
            <view
              v-for="e in entityOptions"
              :key="e.key"
              class="type-chip"
              :class="{ active: selectedEntity === e.key }"
              hover-class="pressed"
              hover-stay-time="80"
              role="button"
              :aria-label="`反馈对象：${e.label}`"
              @tap="selectedEntity = e.key"
            >
              <text class="type-text">{{ e.label }}</text>
            </view>
          </view>
        </CardSection>

        <!-- 内容 -->
        <CardSection title="反馈内容">
          <textarea
            class="content-input"
            v-model="content"
            :placeholder="contentPlaceholder"
            maxlength="1000"
            :auto-height="true"
            :cursor-spacing="20"
            :adjust-position="true"
          />
          <text class="counter" :class="{ warn: content.length >= 900 }">{{ content.length }}/1000</text>
        </CardSection>

        <!-- 联系方式 -->
        <CardSection title="联系方式（选填）">
          <input
            class="contact-input"
            v-model="contact"
            :cursor-spacing="20"
            :adjust-position="true"
            placeholder="邮箱 / 微信，方便我们回复你"
          />
          <text v-if="contactError" class="contact-tip error">{{ contactError }}</text>
          <text v-else class="contact-tip">选填；若填写请确保格式正确，方便我们回复你</text>
        </CardSection>

        <!-- 提交按钮：内容内嵌（随表单流动，避免固定底栏误触/遮挡/切Tab跳变） -->
        <view class="submit-inline">
          <AppButton :text="submitting ? '提交中…' : '提交反馈'" :loading="submitting" @click="submit" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'

const theme = useThemeStore()

// 返回：有返回栈时 navigateBack（从「我的」进入回「我的」）；无返回栈（redirectTo 直达）才 reLaunch 首页
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else backToHome()
}

// ---- 提交表单 ----
// 类型与后端契约枚举对齐：suggestion(提建议)/error(内容纠错)/bug(遇到问题)
// 2026-08-17 重设计：轻量收敛为 3 类（移除「其他」，减少选择负担）；error 语义为「内容纠错」，与首页「反馈菜品」入口副文案一致
const types: { value: FeedbackSubmit['type']; label: string }[] = [
  { value: 'suggestion', label: '提建议' },
  { value: 'error', label: '信息纠错' },
  { value: 'bug', label: '遇到问题' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')
// 内容纠错二级细分（独立成档：新增菜品 / 信息有误；提交均映射 type=error，admin 靠 content 前缀「【新增菜品】」区分，不改表）
const correctionActions = [
  { key: 'add' as const, label: '新增菜品' },
  { key: 'correct' as const, label: '信息有误' },
]
const feedbackAction = ref<'add' | 'correct'>('correct')
const entityOptions = [
  { key: 'dish', label: '菜品' },
  { key: 'stall', label: '档口' },
  { key: 'canteen', label: '食堂' },
  { key: 'none', label: '其他' },
]
const selectedEntity = ref('none')
const content = ref('')
const contact = ref('')
const contactError = ref('')
const submitting = ref(false)

// 预选菜品（首页「上传菜品」入口带 object/name/id）
const presetDishName = ref('')
const presetRelatedId = ref<number | undefined>(undefined)

onLoad((opts?: Record<string, string>) => {
  const obj = opts?.object
  if (obj && entityOptions.some((e) => e.key === obj)) {
    selectedEntity.value = obj
  }
  // 「反馈菜品」入口（object=dish）或带菜品名/ID 进入：默认预选「内容纠错」，与入口副文案「纠错·建议」心智一致
  if (obj === 'dish' || opts?.name || opts?.id) type.value = 'error'
  // 带入菜品名与关联 ID，提交时一并上报
  if (opts?.name) presetDishName.value = opts.name
  if (opts?.id) {
    const id = Number(opts.id)
    if (!Number.isNaN(id)) presetRelatedId.value = id
  }
})

function clearPreset() {
  presetDishName.value = ''
  presetRelatedId.value = undefined
  if (selectedEntity.value === 'dish') selectedEntity.value = 'none'
}

const contentPlaceholder = computed(() => {
  if (type.value === 'error' && feedbackAction.value === 'add') return '请描述你想新增的菜品：名称、所在档口、特色等…'
  if (type.value === 'error') return '请描述需要纠错的内容：名称、价格、档口信息等…'
  if (type.value === 'bug') return '请描述遇到的问题：操作步骤、现象、报错提示等…'
  if (selectedEntity.value === 'dish') return '请描述你想上传的菜品：名称、所在档口、特色等…'
  return '请描述你的想法或建议…'
})

async function submit() {
  // 防重入：快速连点时 submitting 尚未置位前的竞态窗口直接早退，确保幂等
  if (submitting.value) return
  // 反馈不登录也可用（后端 POST /feedback 公开，游客 userId=null）；仅做内容校验
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  if (text.length > 1000) {
    uni.showToast({ title: '内容不能超过1000字', icon: 'none' })
    return
  }
  // 联系方式轻校验：可空；填写了则校验邮箱/微信格式，不阻断提交仅提示
  contactError.value = ''
  if (contact.value.trim() && !validateContact(contact.value.trim())) {
    contactError.value = '联系方式格式不太对（邮箱或微信号），可留空'
    uni.showToast({ title: '联系方式格式有误', icon: 'none' })
  }
  submitting.value = true
  try {
    // 新增菜品：对象强制为菜品，content 加「【新增菜品】」前缀（admin 可区分，不改表）；其余对象按选择
    const isAddDish = type.value === 'error' && feedbackAction.value === 'add'
    const relatedType = isAddDish
      ? 'dish'
      : presetDishName.value ? 'dish' : selectedEntity.value === 'none' ? undefined : selectedEntity.value
    const relatedId = presetRelatedId.value
    await submitFeedback({
      type: type.value,
      relatedType,
      relatedId,
      content: isAddDish ? `【新增菜品】${text}` : text,
      contact: contact.value.trim() || undefined,
    })
    uni.showToast({ title: '提交成功，感谢反馈', icon: 'success' })
    content.value = ''
    contact.value = ''
    contactError.value = ''
    selectedEntity.value = 'none'
    type.value = 'suggestion'
    feedbackAction.value = 'correct'
    clearPreset()
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

// 联系方式轻校验：邮箱或微信号（5-20 位字母数字下划线连字符）
function validateContact(v: string): boolean {
  return /^[\w.+-]+@[\w-]+\.[\w.-]+$/.test(v) || /^[\w-]{5,20}$/.test(v)
}
</script>

<style scoped>
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

/* scroll-view 作为 flex 子项只需 min-height:0 允许收缩；不能再设 height:0（微信小程序下
   scroll-view 高度会被锁死不参与 flex 拉伸，导致内容区下部分被裁剪、底栏错位）。 */
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding-bottom: env(safe-area-inset-bottom); }

/* 卡片纵向节奏：不用 flex gap（会与 CardSection 自带 margin 叠加成 56rpx 大缝），
   改由下方 .card-section 的 margin 单源控制，卡距收敛到 8pt（16rpx） */
.tab-pane { display: flex; flex-direction: column; }
.tab-pane .card-section { margin: 0 var(--spacing-md) var(--spacing-sm); }
.tab-pane .card-section:first-child { margin-top: var(--spacing-xs); }

/* 预选菜品提示：紧贴卡片内顶部，与下方 chips 间距收窄到 8rpx（避免内容区被过度下推） */
.preset-tip { display: flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary-soft); border-radius: var(--radius-card); margin-bottom: var(--spacing-xs); }
.preset-tip-text { flex: 1; font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
.preset-tip-clear { flex-shrink: 0; padding: var(--spacing-xs) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-tag); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.preset-tip-clear:active { transform: scale(var(--press-scale)); }
.preset-tip-clear-text { font-size: var(--font-aux); color: var(--text-secondary); }

/* 类型选择（选中态：主色描边 + 软底 + 主色字，与 ApplySheet .seg.on 一致） */
.type-row { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.type-chip { padding: var(--spacing-sm) var(--spacing-lg); border-radius: var(--radius-pill); background: var(--bg-soft); border: 2rpx solid transparent; transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.type-chip:active { transform: scale(var(--press-scale)); }
.type-chip.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.type-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-semibold); }
.type-chip.active .type-text { color: var(--color-primary); }
/* 内容纠错二级细分：与一级拉开层级（更紧凑、字号更小、bg-input 底），选中仍主色态 */
.sub-row { margin-top: var(--spacing-xs); }
.sub-chip { padding: var(--spacing-2xs) var(--spacing-sm); background: var(--bg-input); }
.sub-chip .type-text { font-size: var(--font-tiny); font-weight: var(--weight-regular); }

/* 内容输入：与发布菜品/评价 textarea 同款；min-height 240rpx 让核心字段视觉权重最高 */
.content-input { width: 100%; min-height: 240rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; padding: var(--spacing-md); background: var(--bg-input); border-radius: var(--radius-card); box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
/* 字数接近上限转警示色（≥900/1000） */
.counter.warn { color: var(--color-warning); }
.contact-input { width: 100%; height: 88rpx; background: var(--bg-input); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.contact-tip { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }
.contact-tip.error { color: var(--color-warning); }

/* 提交按钮：内容内嵌（随表单流动，避免固定底栏的误触风险/内容遮挡/切Tab布局跳变）。
   水平 margin 与卡片对齐（24rpx），底部留白含安全区；按钮走 AppButton 默认 width:100% 全宽 */
.submit-inline {
  margin: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
}

@media (prefers-reduced-motion: reduce) {
  .type-chip, .preset-tip-clear { transition: none !important; }
  .type-chip:active, .preset-tip-clear:active { transform: none !important; }
}
</style>
