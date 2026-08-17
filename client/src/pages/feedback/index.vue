<template>
  <view class="page feedback-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="意见反馈" @back="goBack" />

    <!-- 写反馈视图 -->
    <scroll-view v-if="activeTab === 'write'" class="scroll-wrap write-scroll" scroll-y>
      <!-- 双 Tab 分段控制器（随内容滚动，不固定视口；滚回顶部即可切换视图） -->
      <view class="seg-tabs" role="tablist" aria-label="反馈页视图切换">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="seg"
          :class="{ on: activeTab === t.key }"
          hover-class="pressed"
          hover-stay-time="60"
          role="tab"
          :aria-selected="activeTab === t.key"
          :aria-label="t.label"
          @tap="activeTab = t.key"
        >
          <text class="seg-text">{{ t.label }}</text>
        </view>
      </view>
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

    <!-- 我的反馈视图 -->
    <scroll-view v-else class="scroll-wrap mine-scroll" scroll-y>
      <!-- 双 Tab 分段控制器（随内容滚动，不固定视口） -->
      <view class="seg-tabs" role="tablist" aria-label="反馈页视图切换">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="seg"
          :class="{ on: activeTab === t.key }"
          hover-class="pressed"
          hover-stay-time="60"
          role="tab"
          :aria-selected="activeTab === t.key"
          :aria-label="t.label"
          @tap="activeTab = t.key"
        >
          <text class="seg-text">{{ t.label }}</text>
        </view>
      </view>
      <view class="tab-pane">
        <!-- 未登录：引导登录 -->
        <view v-if="!loggedIn" class="login-hint" hover-class="pressed" hover-stay-time="80" role="button" :aria-label="`登录后可查看你的反馈记录，点击去登录`" @tap="goLogin">
          <text class="login-hint-text">登录后可查看你的反馈记录</text>
          <view class="login-hint-action">
            <text class="login-hint-action-text">去登录</text>
            <IconSvg name="arrow" :size="28" color="var(--color-primary)" />
          </view>
        </view>

        <!-- 状态筛选 -->
        <view v-else class="filter-tabs" role="tablist" aria-label="反馈状态筛选">
          <view
            v-for="f in filterOptions"
            :key="f.key"
            class="filter-chip"
            :class="{ on: filterStatus === f.key }"
            hover-class="pressed"
            hover-stay-time="60"
            role="tab"
            :aria-selected="filterStatus === f.key"
            :aria-label="`${f.label}（${statusCount(f.key)}）`"
            @tap="filterStatus = f.key"
          >
            <text class="filter-text">{{ f.label }}</text>
            <text class="filter-count">{{ statusCount(f.key) }}</text>
          </view>
        </view>

        <!-- 记录列表 -->
        <template v-if="loggedIn">
          <view v-if="recordsLoading && records.length === 0" class="sk-list">
            <view v-for="s in 3" :key="s" class="sk-item" />
          </view>
          <view v-else-if="filteredRecords.length > 0" class="rec-list">
            <view
              v-for="f in filteredRecords"
              :key="f.id"
              class="rec-item"
              hover-class="pressed"
              hover-stay-time="80"
              role="button"
              :aria-label="`反馈详情：${feedbackTypeLabel(f.type)}，${f.status === 'handled' ? '已处理' : '待处理'}`"
              @tap="viewDetail(f)"
            >
              <view class="rec-top">
                <text class="rec-type-tag">{{ feedbackTypeLabel(f.type) }}</text>
                <text class="fb-status" :class="f.status === 'handled' ? 'handled' : 'pending'">{{ f.status === 'handled' ? '已处理' : '待处理' }}</text>
                <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="rec-arrow" />
              </view>
              <text class="rec-content">{{ f.content }}</text>
              <text class="rec-time">{{ formatTime(f.createdAt) }}</text>
            </view>
          </view>
          <EmptyState v-else-if="recordsLoadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="loadRecords" />
          <EmptyState v-else text="还没有反馈记录" icon="report" />
        </template>

        <view style="height: var(--spacing-xl)" />
      </view>
    </scroll-view>

    <!-- 认证弹层（未登录提交反馈 requireAuth 统一在此弹出） -->
    <AuthSheet />

    <!-- 反馈详情 BottomSheet（替换原生 showModal） -->
    <view
      v-if="detailOpen"
      class="sheet-mask"
      :class="{ show: detailMaskShow }"
      @tap="closeDetail"
      @touchmove.stop.prevent="noop"
    />
    <view
      class="bottom-sheet"
      :class="{ open: detailSheetOpen }"
      :style="detailSheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <text class="sheet-title">反馈详情</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" role="button" aria-label="关闭" @tap="closeDetail" />
      </view>
      <scroll-view v-if="detailData" class="sheet-body" scroll-y>
        <view class="detail-block">
          <text class="detail-label">反馈类型</text>
          <text class="detail-value">{{ feedbackTypeLabel(detailData.type) }}</text>
        </view>
        <view class="detail-block">
          <text class="detail-label">处理状态</text>
          <text class="fb-status" :class="detailData.status === 'handled' ? 'handled' : 'pending'">{{ detailData.status === 'handled' ? '已处理' : '待处理' }}</text>
        </view>
        <view class="detail-block">
          <text class="detail-label">反馈内容</text>
          <text class="detail-content">{{ detailData.content }}</text>
        </view>
        <view v-if="detailData.reply" class="detail-block">
          <text class="detail-label">后台回复</text>
          <text class="detail-content detail-reply">{{ detailData.reply }}</text>
        </view>
        <view class="detail-block">
          <text class="detail-label">提交时间</text>
          <text class="detail-value">{{ formatTime(detailData.createdAt) }}</text>
        </view>
      </scroll-view>
      <!-- 底部操作行：复制内容 / 再反馈一次 -->
      <view class="sheet-actions">
        <view class="sheet-action" hover-class="pressed" hover-stay-time="80" role="button" aria-label="复制反馈内容" @tap="copyContent">
          <IconSvg name="copy" :size="32" color="var(--text-secondary)" />
          <text class="sheet-action-text">复制内容</text>
        </view>
        <view class="sheet-action primary" hover-class="pressed" hover-stay-time="80" role="button" aria-label="再反馈一次" @tap="reFeedback">
          <IconSvg name="edit" :size="32" color="var(--color-primary)" />
          <text class="sheet-action-text primary">再反馈一次</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { onShow, onLoad, onUnload } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { submitFeedback, getMyFeedback, type FeedbackMyItem } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const userStore = useUserStore()
const loggedIn = computed(() => userStore.isLoggedIn())

// 返回：有返回栈时 navigateBack（从「我的」进入回「我的」）；无返回栈（redirectTo 直达）才 reLaunch 首页
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else backToHome()
}

// ---- 视图与筛选 ----
const tabs = [
  { key: 'write' as const, label: '写反馈' },
  { key: 'mine' as const, label: '我的反馈' },
]
const activeTab = ref<'write' | 'mine'>('write')
const filterOptions = [
  { key: 'all' as const, label: '全部' },
  { key: 'pending' as const, label: '待处理' },
  { key: 'handled' as const, label: '已处理' },
]
const filterStatus = ref<'all' | 'pending' | 'handled'>('all')

// 切到「我的反馈」时首次加载记录（仅登录用户）
watch(activeTab, (t) => {
  if (t === 'mine' && loggedIn.value && records.value.length === 0) loadRecords(true)
})

const filteredRecords = computed(() =>
  filterStatus.value === 'all'
    ? records.value
    : records.value.filter((r) => r.status === filterStatus.value)
)
function statusCount(key: 'all' | 'pending' | 'handled'): number {
  if (key === 'all') return records.value.length
  return records.value.filter((r) => r.status === key).length
}

// ---- 提交表单 ----
// 类型与后端契约枚举对齐（error 语义为「内容纠错」，与首页「反馈菜品」入口副文案一致）
const types: { value: FeedbackSubmit['type']; label: string }[] = [
  { value: 'suggestion', label: '功能建议' },
  { value: 'error', label: '内容纠错' },
  { value: 'other', label: '其他' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')
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
  if (type.value === 'error') return '请描述需要纠错的内容：名称、价格、档口信息等…'
  if (selectedEntity.value === 'dish') return '请描述你想上传的菜品：名称、所在档口、特色等…'
  return '请描述你遇到的问题或建议…'
})

async function submit() {
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
    // 预选菜品时无论用户是否改为「其他」都上报相关对象/ID（优先以预选为准）
    const relatedType = presetDishName.value ? 'dish' : selectedEntity.value === 'none' ? undefined : selectedEntity.value
    const relatedId = presetRelatedId.value
    await submitFeedback({
      type: type.value,
      relatedType,
      relatedId,
      content: text,
      contact: contact.value.trim() || undefined,
    })
    uni.showToast({ title: '提交成功，感谢反馈', icon: 'success' })
    content.value = ''
    contact.value = ''
    contactError.value = ''
    selectedEntity.value = 'none'
    type.value = 'suggestion'
    clearPreset()
    // 提交成功即时刷新记录并切到「我的反馈」Tab，新反馈置顶（后端倒序）
    if (loggedIn.value) await loadRecords(true)
    filterStatus.value = 'all'
    activeTab.value = 'mine'
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

// ---- 我的反馈记录（登录后加载） ----
const records = ref<FeedbackMyItem[]>([])
const recordsLoading = ref(false)
const recordsLoadFailed = ref(false)

async function loadRecords(silent = false) {
  if (!loggedIn.value) return
  if (!silent) recordsLoading.value = true
  recordsLoadFailed.value = false
  try {
    records.value = await getMyFeedback()
  } catch {
    recordsLoadFailed.value = true
    /* toast 由 http 层统一处理 */
  } finally {
    recordsLoading.value = false
  }
}

function goLogin() {
  // 未登录：引导认证；登录成功后 watch 自动重新加载
  userStore.requireAuth(() => loadRecords(false))
}

// ---- 详情 BottomSheet ----
const detailOpen = ref(false)
const detailMaskShow = ref(false)
const detailSheetOpen = ref(false)
const detailData = ref<FeedbackMyItem | null>(null)
const detailDragOffset = ref(0)
const detailDragging = ref(false)
let startY = 0
let lastY = 0
let lastTime = 0
let velocity = 0
// N06 修复：详情关闭延迟定时器句柄，避免快速开关误关 / 卸载后误清理
let closeTimer: ReturnType<typeof setTimeout> | null = null

const detailSheetStyle = computed(() => ({
  transform: `translateY(calc(${detailSheetOpen.value ? 0 : 100}% + ${detailDragging.value ? detailDragOffset.value : 0}px))`,
  transition: detailDragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

function viewDetail(f: FeedbackMyItem) {
  detailData.value = f
  detailOpen.value = true
  nextTick(() => {
    detailMaskShow.value = true
    detailSheetOpen.value = true
  })
}

function closeDetail() {
  detailMaskShow.value = false
  detailSheetOpen.value = false
  detailDragOffset.value = 0
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => { detailOpen.value = false }, 300)
}

// 复制反馈内容到剪贴板
function copyContent() {
  if (!detailData.value?.content) return
  uni.setClipboardData({
    data: detailData.value.content,
    success: () => uni.showToast({ title: '已复制内容', icon: 'none' }),
  })
}

// 再反馈一次：关闭详情、切到写反馈并预填内容
function reFeedback() {
  const src = detailData.value
  closeDetail()
  activeTab.value = 'write'
  if (src) {
    type.value = src.type as FeedbackSubmit['type']
    content.value = src.content
  }
}

function noop() {}

function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  lastY = startY
  lastTime = Date.now()
  velocity = 0
  detailDragging.value = true
}
function onTouchMove(e: any) {
  if (!detailDragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const now = Date.now()
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000
  lastY = y
  lastTime = now
  const delta = y - startY
  detailDragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!detailDragging.value) return
  detailDragging.value = false
  if (velocity > 480 || detailDragOffset.value > 120) closeDetail()
  detailDragOffset.value = 0
}

function feedbackTypeLabel(t: string): string {
  const map: Record<string, string> = { error: '内容纠错', suggestion: '功能建议', other: '其他', report: '举报', wrong: '信息有误' }
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
// 登录成功后自动加载记录
watch(
  () => userStore.isLoggedIn(),
  (v) => {
    if (v) {
      loadRecords(initialized)
      initialized = true
    }
  },
)
onShow(() => {
  if (loggedIn.value) {
    loadRecords(initialized)
    initialized = true
  }
})

// N06 修复：页面卸载时清理详情关闭延迟定时器，避免卸载后才误置 detailOpen
onUnload(() => {
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = null
})
</script>

<style scoped>
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

/* scroll-view 作为 flex 子项只需 min-height:0 允许收缩；不能再设 height:0（微信小程序下
   scroll-view 高度会被锁死不参与 flex 拉伸，导致内容区下部分被裁剪、底栏错位）。
   顶部间距由内容首元素 .seg-tabs 的 margin 提供，scroll-wrap 不再设 padding-top 避免叠加 */
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; }
/* 提交按钮已内嵌内容区（无固定底栏）：两视图统一仅留安全白，不再预留 action-bar 避让 */
.write-scroll { padding-bottom: env(safe-area-inset-bottom); }
.mine-scroll { padding-bottom: env(safe-area-inset-bottom); }
/* 卡片纵向节奏：不用 flex gap（会与 CardSection 自带 margin 叠加成 56rpx 大缝），
   改由下方 .card-section 的 margin 单源控制，卡距收敛到 8pt（16rpx） */
.tab-pane { display: flex; flex-direction: column; }
.tab-pane .card-section { margin: 0 var(--spacing-md) var(--spacing-sm); }
.tab-pane .card-section:first-child { margin-top: 0; }
/* 我的反馈：筛选条/登录引导与列表间补回 8pt 间距（gap 已移除） */
.filter-tabs, .login-hint { margin-bottom: var(--spacing-sm); }

/* 顶部双 Tab 分段（参考 ApplySheet .seg 范式；白卡内含两个等宽分段）。
   上下 margin 收窄到 8rpx，避免与 Header 底边、内容区顶部间距累加把内容往下挤 */
.seg-tabs {
  flex-shrink: 0;
  display: flex;
  gap: var(--spacing-xs);
  margin: var(--spacing-xs) var(--spacing-md);
  padding: var(--spacing-2xs);
  background: var(--bg-soft);
  border-radius: var(--radius-pill);
}
.seg {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  border-radius: var(--radius-pill);
  background: transparent;
  transition: var(--press-transition), background 0.25s ease, color 0.25s ease;
  -webkit-tap-highlight-color: transparent;
}
.seg:active { transform: scale(var(--press-scale)); }
.seg.pressed { background: var(--bg-card); }
.seg.on { background: var(--bg-card); box-shadow: var(--shadow-card); }
.seg-text { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.seg.on .seg-text { color: var(--color-primary); }

/* 我的反馈：状态筛选条 */
.filter-tabs { flex-shrink: 0; display: flex; gap: var(--spacing-sm); flex-wrap: wrap; }
.filter-chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  border: 2rpx solid transparent;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.filter-chip:active { transform: scale(var(--press-scale)); }
.filter-chip.pressed { background: var(--bg-card); }
.filter-chip.on { background: var(--color-primary-soft); border-color: var(--color-primary); }
.filter-text { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.filter-chip.on .filter-text { color: var(--color-primary); }
.filter-count {
  font-size: var(--font-aux);
  font-weight: var(--weight-bold);
  color: var(--text-tertiary);
  background: var(--bg-card);
  min-width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 999rpx;
  padding: 0 var(--spacing-2xs);
}
.filter-chip.on .filter-count { color: var(--color-primary); background: var(--bg-card); }

/* 登录引导（文档：bg-soft 圆角卡 + arrow 图标） */
.login-hint { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-card); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.login-hint:active { transform: scale(var(--press-scale)); }
.login-hint.pressed { background: var(--bg-card); }
.login-hint-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.login-hint-action { display: flex; align-items: center; gap: var(--spacing-xs); flex-shrink: 0; }
.login-hint-action-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }

/* 记录骨架 */
.sk-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-item { height: 120rpx; border-radius: var(--radius-card); background: var(--bg-soft); }

/* 记录列表：白底圆角卡片堆叠（文档 §细节设计） */
.rec-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.rec-item { background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); padding: var(--spacing-md); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.rec-item:active { transform: scale(var(--press-scale)); background: var(--bg-soft); }
/* 真机按压（微信 hover-class；背景转 bg-soft 与设计文档 §按压态 一致） */
.rec-item.pressed { background: var(--bg-soft); }
.rec-top { display: flex; align-items: center; gap: var(--spacing-sm); }
.rec-type-tag { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); background: var(--bg-soft); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); }
.rec-arrow { flex-shrink: 0; margin-left: auto; }
.rec-content { margin-top: var(--spacing-sm); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; overflow: hidden; }
.rec-time { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }

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

/* 内容输入：与发布菜品/评价 textarea 同款；min-height 240rpx 让核心字段视觉权重最高 */
.content-input { width: 100%; min-height: 240rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; padding: var(--spacing-md); background: var(--bg-input); border-radius: var(--radius-card); box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
/* 字数接近上限转警示色（≥900/1000） */
.counter.warn { color: var(--color-warning); }
.contact-input { width: 100%; height: 88rpx; background: var(--bg-input); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.contact-tip { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }
.contact-tip.error { color: var(--color-warning); }

/* 提交栏：半透玻璃材质 + 圆角顶边；padding 撑高（88rpx 按钮 + 上下 16rpx = 120rpx，对齐 --action-bar-height），
   safe-area 并入底部 padding，避免固定 height 挤压按钮；背景须半透，否则 backdrop-filter 无毛玻璃效果 */
/* 提交按钮：内容内嵌（随表单流动，避免固定底栏的误触风险/内容遮挡/切Tab布局跳变）。
   水平 margin 与卡片对齐（24rpx），底部留白含安全区；按钮走 AppButton 默认 width:100% 全宽 */
.submit-inline {
  margin: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
}

/* 详情 BottomSheet（复用 ApplySheet/RelatedPickerSheet 范式） */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); opacity: 0; transition: opacity 0.3s ease; z-index: 90; }
.sheet-mask.show { opacity: 1; }
.bottom-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  max-height: calc(100dvh - var(--spacing-lg));
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }
.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
.detail-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.detail-block:last-child { border-bottom: none; }
.detail-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-bold); color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.detail-value { font-size: var(--font-body); color: var(--text-primary); }
.detail-content { font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; overflow-wrap: break-word; word-break: break-word; }
.detail-reply { color: var(--text-secondary); }

/* 详情底部操作行：复制内容 / 再反馈一次 */
.sheet-actions { flex-shrink: 0; display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-lg) 0; border-top: 2rpx solid var(--border-color); }
.sheet-action { flex: 1; display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); height: 88rpx; border-radius: var(--radius-btn); background: var(--bg-soft); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.sheet-action:active { transform: scale(var(--press-scale)); }
.sheet-action.pressed { background: var(--bg-card); }
.sheet-action.primary { background: var(--color-primary-soft); }
.sheet-action-text { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.sheet-action-text.primary { color: var(--color-primary); }

/* 轻量状态徽标（待处理/已处理，避免污染全局 StatusBadge 文案） */
.fb-status { display: inline-flex; align-items: center; flex-shrink: 0; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); font-size: var(--font-aux); font-weight: var(--weight-bold); }
.fb-status.pending { background: var(--color-warning-soft); color: var(--color-warning); }
.fb-status.handled { background: var(--color-success-soft); color: var(--color-success); }

@media (prefers-reduced-motion: reduce) {
  .login-hint, .rec-item, .type-chip, .preset-tip-clear, .seg, .filter-chip, .sheet-action { transition: none !important; }
  .login-hint:active, .rec-item:active, .type-chip:active, .preset-tip-clear:active, .seg:active, .filter-chip:active, .sheet-action:active { transform: none !important; }
  .sheet-mask { transition: opacity 0.2s ease; }
  /* 仅去掉开合过渡动画，transform 是弹层显隐的载体，不能禁（否则详情常驻遮挡内容） */
  .bottom-sheet { transition: none !important; }
}
</style>
