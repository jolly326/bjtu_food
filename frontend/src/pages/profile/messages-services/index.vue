<template>
  <view class="page messages-services-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="反馈中心" showBack />

    <!-- 反馈记录列表（进入即展示） -->
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && list.length === 0" class="skeleton-list">
        <view v-for="s in 4" :key="s" class="sk-item skeleton" />
      </view>

      <view v-else-if="list.length > 0" class="list">
        <view v-for="f in list" :key="f.id" class="sub-item" @tap="viewDetail(f)">
          <view class="item-main">
            <view class="item-title-row">
              <text class="item-title">{{ feedbackTypeLabel(f.type) }}</text>
              <StatusBadge :status="f.status === 'handled' ? 'approved' : 'pending'" />
            </view>
            <text class="item-content">{{ f.content }}</text>
            <text class="item-time">{{ formatTime(f.createdAt) }}</text>
          </view>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="item-arrow" />
        </view>
      </view>
      <!-- 加载失败：与空数据语义区分，提供重试 -->
      <EmptyState v-else-if="loadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="loadData" />
      <EmptyState v-else text="还没有反馈，点右下角发起反馈" />
      <view style="height: 180rpx" />
    </scroll-view>

    <!-- 发起反馈：底部提交栏 + 全宽按钮（仿发布动态页的发布按钮） -->
    <view class="submit-bar">
      <AppButton text="写反馈" @click="openFeedback" />
    </view>

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />

    <!-- 反馈表单弹层：多级联分类（信息类型→实体类型→操作类型）+ 下拉拖动关闭 + 接近全屏高度 -->
    <view v-if="feedbackOpen" class="fb-mask" :class="{ show: maskShow }" @tap="closeFeedback" @touchmove.stop.prevent="noop" />
    <view
      class="fb-sheet"
      :class="{ open: sheetOpen, dragging: dragging }"
      :style="dragStyle"
      @touchstart="onSheetTouchStart"
      @touchmove="onSheetTouchMove"
      @touchend="onSheetTouchEnd"
      @touchcancel="onSheetTouchEnd"
    >
      <view class="fb-grabber" />
      <view class="fb-head">
        <text class="fb-title">发起反馈</text>
        <IconSvg name="close" :size="36" color="var(--text-tertiary)" class="fb-close" @tap="closeFeedback" />
      </view>
      <scroll-view class="fb-body" scroll-y>
        <!-- 一级：信息类型 -->
        <view class="fb-block">
          <text class="fb-label">信息类型</text>
          <view class="type-chips">
            <view
              v-for="opt in typeOptions"
              :key="opt.key"
              class="type-chip"
              :class="{ active: selectedType === opt.key }"
              @tap="selectedType = opt.key"
            >
              <text class="type-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>
        <!-- 二级：反馈对象 -->
        <view class="fb-block">
          <text class="fb-label">反馈对象</text>
          <view class="type-chips">
            <view
              v-for="opt in entityOptions"
              :key="opt.key"
              class="type-chip"
              :class="{ active: selectedEntity === opt.key }"
              @tap="selectedEntity = opt.key"
            >
              <text class="type-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>
        <!-- 三级：操作类型 -->
        <view class="fb-block">
          <text class="fb-label">操作类型</text>
          <view class="type-chips">
            <view
              v-for="opt in actionOptions"
              :key="opt.key"
              class="type-chip"
              :class="{ active: selectedAction === opt.key }"
              @tap="selectedAction = opt.key"
            >
              <text class="type-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>
        <view class="fb-block">
          <text class="fb-label">问题描述</text>
          <textarea
            v-model="content"
            class="content-input"
            placeholder="请描述你的问题或建议…"
            maxlength="1000"
            placeholder-class="input-placeholder"
          />
        </view>
      </scroll-view>
      <!-- 底部提交栏（与 ApplySheet 提交按钮同款：全宽主色按钮） -->
      <view class="fb-submit">
        <view
          class="fb-submit-btn"
          :class="{ disabled: submitting || !content.trim() }"
          @tap="submit"
        >
          <text class="fb-submit-text">{{ submitting ? '提交中…' : '提交反馈' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import AppButton from '@/components/AppButton.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { getMyFeedback, submitFeedback, type FeedbackMyItem } from '@/api/feedback'

const userStore = useUserStore()
const theme = useThemeStore()

/** 一级：信息类型（与「信息有误」弹窗统一） */
const typeOptions = [
  { key: 'error', label: 'bug 反馈' },
  { key: 'wrong', label: '信息有误' },
  { key: 'suggestion', label: '建议' },
  { key: 'other', label: '其他' },
]
/** 二级：反馈对象（实体类型） */
const entityOptions = [
  { key: 'dish', label: '菜品' },
  { key: 'stall', label: '档口' },
  { key: 'canteen', label: '食堂' },
  { key: 'none', label: '其他' },
]
/** 三级：操作类型 */
const actionOptions = [
  { key: 'close', label: '下架 / 关闭' },
  { key: 'wrong-info', label: '信息错误' },
  { key: 'other', label: '其他' },
]

const selectedType = ref('suggestion')
const selectedEntity = ref('none')
const selectedAction = ref('other')
const content = ref('')
const submitting = ref(false)

// 下拉拖动关闭（参照 ApplySheet / DishDetailSheet 手势）
/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}
const startY = ref(0)
const dragging = ref(false)
const dragOffset = ref(0)
const dragStyle = computed(() => (dragging.value ? { transform: `translateY(${Math.max(dragOffset.value, 0)}px)` } : {}))
function onSheetTouchStart(e: any) {
  if (!sheetOpen.value) return
  startY.value = e.touches[0].clientY
  dragging.value = true
  dragOffset.value = 0
}
function onSheetTouchMove(e: any) {
  if (!dragging.value) return
  const dy = e.touches[0].clientY - startY.value
  dragOffset.value = Math.max(dy, 0)
}
function onSheetTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragOffset.value > 120) {
    closeFeedback()
  } else {
    dragOffset.value = 0
  }
}

const list = ref<FeedbackMyItem[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

// 弹层
const feedbackOpen = ref(false)
const maskShow = ref(false)
const sheetOpen = ref(false)
function openFeedback() {
  if (!userStore.requireAuth()) return
  feedbackOpen.value = true
  nextTick(() => {
    maskShow.value = true
    sheetOpen.value = true
  })
}
function closeFeedback() {
  maskShow.value = false
  sheetOpen.value = false
  setTimeout(() => { feedbackOpen.value = false }, 250)
}

/** 一级信息类型 → 后端 type 枚举（后端按 suggestion/error/other/report 过滤，禁止复合串） */
function mapType(sel: string): 'suggestion' | 'error' | 'other' | 'report' {
  switch (sel) {
    case 'error': return 'error'      // bug 反馈 → error
    case 'wrong': return 'error'      // 信息有误 → error（内容纠错）
    case 'suggestion': return 'suggestion'
    case 'other': return 'other'
    default: return 'other'
  }
}

/** 三级操作类型 → content 前缀（后端不解析复合串，仅并入文案保留信息） */
function actionPrefix(action: string): string {
  const m: Record<string, string> = { close: '下架 / 关闭', 'wrong-info': '信息错误' }
  return m[action] ? `[操作：${m[action]}] ` : ''
}

async function submit() {
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    // 契约整改：type 只传枚举值；二级反馈对象映射 relatedType；三级操作并入 content 前缀
    await submitFeedback({
      type: mapType(selectedType.value),
      relatedType: selectedEntity.value === 'none' ? undefined : selectedEntity.value,
      content: `${actionPrefix(selectedAction.value)}${text}`,
    })
    uni.showToast({ title: '反馈已提交', icon: 'success' })
    content.value = ''
    selectedType.value = 'suggestion'
    selectedEntity.value = 'none'
    selectedAction.value = 'other'
    closeFeedback()
    await loadData(true)
  } catch {
    /* toast 由 http 层统一处理 */
  } finally {
    submitting.value = false
  }
}

function viewDetail(f: FeedbackMyItem) {
  uni.showModal({
    title: feedbackTypeLabel(f.type),
    content: `内容：${f.content}\n\n状态：${f.status === 'handled' ? '已处理' : '待处理'}${f.reply ? `\n回复：${f.reply}` : ''}`,
    showCancel: false,
  })
}

async function loadData(silent = false) {
  if (!userStore.requireAuth()) return
  if (!silent) loading.value = true
  loadFailed.value = false
  try {
    list.value = await getMyFeedback()
  } catch {
    loadFailed.value = true
    /* toast 由 http 层统一处理 */
  } finally {
    loading.value = false
  }
}

function feedbackTypeLabel(t: string): string {
  const map: Record<string, string> = { error: 'bug 反馈', wrong: '信息有误', suggestion: '建议', other: '其他', dish: '菜品问题', stall: '档口问题', pay: '支付问题', page: '页面无法使用', report: '举报' }
  return map[t] ?? '反馈'
}
function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

let initialized = false
// 游客直访时弹认证；认证成功后自动加载
watch(
  () => userStore.isLoggedIn(),
  (v) => {
    if (v) {
      loadData(initialized)
      initialized = true
    }
  },
)
onShow(() => {
  if (!userStore.requireAuth()) return
  loadData(initialized)
  initialized = true
})

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.messages-services-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 0; }
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); padding: 0 var(--spacing-md); }
.skeleton-list { display: flex; flex-direction: column; gap: var(--spacing-sm); padding: 0 var(--spacing-md); }
.sk-item { height: 140rpx; border-radius: var(--radius-card); }
.sub-item { display: flex; align-items: center; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sub-item:active { transform: scale(var(--press-scale)); }
.item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.item-title-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.item-title { font-size: var(--font-caption); font-weight: var(--weight-semibold); color: var(--text-primary); }
.item-content { font-size: var(--font-aux); color: var(--text-secondary); display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.item-time { font-size: var(--font-tiny); color: var(--text-tertiary); }
.item-arrow { flex-shrink: 0; }

/* 发起反馈：底部提交栏（仿发布动态页 submit-bar：白底 + 上边框 + 阴影 + 全宽 AppButton） */
.submit-bar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 40;
  padding: var(--spacing-md);
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-soft);
  border-top: 2rpx solid var(--border-color);
}

/* 反馈表单弹层：接近全屏高度（92vh，多级分类表单需要更多空间）+ 拖动手势（dragging 时关过渡） */
.fb-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; opacity: 0; transition: opacity 0.25s ease; }
.fb-mask.show { opacity: 1; }
.fb-sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 100;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
  display: flex;
  flex-direction: column;
  max-height: 92vh;
}
.fb-sheet.open { transform: translateY(0); }
/* 拖动中：跟随手指 + 关闭过渡，禁用自动动画 */
.fb-sheet.dragging { transition: none; }
/* grabber 与三弹层统一 token（--overlay-dark-soft） */
.fb-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.fb-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.fb-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.fb-close { padding: 0 var(--spacing-xs); }
.fb-body { flex: 1; overflow-y: auto; box-sizing: border-box; }
.fb-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.fb-block:last-child { border-bottom: none; }
.fb-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-bold); color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.type-chips { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
/* 命中区 ≥44px：上下 padding sm（16rpx）保证可点高度 */
.type-chip { padding: var(--spacing-sm) var(--spacing-md); border-radius: var(--radius-tag); background: var(--bg-soft); border: 2rpx solid transparent; transition: transform 120ms var(--ease-out), background 0.15s ease, border-color 0.15s ease; -webkit-tap-highlight-color: transparent; }
.type-chip:active { transform: scale(var(--press-scale)); }
.type-chip.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.type-chip-text { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.type-chip.active .type-chip-text { color: var(--color-primary); }
.content-input { width: 100%; min-height: 200rpx; box-sizing: border-box; padding: var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-card); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; }
.input-placeholder { color: var(--text-tertiary); }
/* 底部提交栏：与 ApplySheet 同款（全宽主色按钮 + 上分隔线 + 安全区避让） */
.fb-submit { padding: var(--spacing-md) var(--spacing-lg) calc(var(--spacing-md) + env(safe-area-inset-bottom)); border-top: 2rpx solid var(--border-color); flex-shrink: 0; }
.fb-submit-btn { height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); transition: transform 0.12s ease, opacity 0.12s ease; -webkit-tap-highlight-color: transparent; }
.fb-submit-btn:active { transform: scale(var(--press-scale)); }
.fb-submit-btn.disabled { opacity: 0.58; }
.fb-submit-text { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--color-on-primary); }

/* 减少动态效果（Apple §14 / 与其他弹层一致）：交叉淡入替代抽屉滑动 */
@media (prefers-reduced-motion: reduce) {
  .fb-mask { transition: opacity 0.2s ease; }
  .fb-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
