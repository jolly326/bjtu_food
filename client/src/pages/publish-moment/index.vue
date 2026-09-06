<template>
  <view class="page publish-page">
    <Header :title="pageTitle" @back="confirmLeave" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 关联对象：单行紧凑表单项（不再内嵌选择行，高度与普通列表行一致），整行点击弹选择器 -->
      <view class="block block--row">
        <view class="form-row" @tap="relatedSheetOpen = true" role="button" :aria-label="relatedLabel">
          <text class="form-label">关联对象</text>
          <text class="form-hint">选填</text>
          <text class="form-value" :class="{ 'form-value--empty': !selectedRelated }">{{ relatedLabel }}</text>
          <IconSvg name="arrow" :size="24" color="var(--text-tertiary)" class="form-arrow" />
        </view>
      </view>

      <!-- 正文 -->
      <view class="block">
        <SectionTitle title="正文" />
        <textarea
          class="content-input"
          v-model="content"
          placeholder="分享你的美食体验、探店灵感…"
          maxlength="500"
          :adjust-position="true"
          :cursor-spacing="120"
          @keyboardheightchange="onKeyboardHeightChange"
        />
        <text class="counter">{{ content.length }}/500</text>
      </view>

      <!-- 图片上传（复用 ImageUploader：选图即上传、右上角删除；计数并入模块标题右侧） -->
      <view class="block">
        <SectionTitle title="图片">
          <template #extra><text class="section-sub">最多 3 张 · {{ images.length }}/3</text></template>
        </SectionTitle>
        <ImageUploader v-model="images" :max="3" :show-counter="false" />
      </view>

      <!-- 提交按钮：页内流式（不吸底），随内容滚动到表单末尾。
           外层热区承接「置灰态点击」——AppButton 在 disabled 时不 emit press，由这里兜底给出必填提示 -->
      <view id="submit-anchor" class="submit-area" @tap="onSubmitAreaTap">
        <AppButton :text="submitText" type="primary" :disabled="!canSubmit" :loading="submitting" @press="submit" />
      </view>
      <!-- 软键盘占位：键盘弹起时在内容尾部补等高空隙，保证发布按钮可滚动到键盘之上不被遮挡 -->
      <view class="keyboard-spacer" :style="{ height: keyboardInset + 'px' }" />
    </scroll-view>

    <!-- 关联对象选择 Sheet（ListPickerSheet：搜索 + 列表 + 完成） -->
    <ListPickerSheet
      :open="relatedSheetOpen"
      title="选择关联菜品"
      searchable
      confirmable
      :options="relatedOptions"
      :selected-key="relatedSelectedKey"
      @close="relatedSheetOpen = false"
      @search="loadRelatedCandidates"
      @select="onRelatedOptionPick"
      @confirm="onRelatedConfirmSheet"
    >
      <!-- 空数据轻提示：未搜索「暂无菜品可关联」；搜索无结果「未找到对应菜品」 -->
      <template #empty>
        <text class="lp-empty-text">{{ relatedSheetEmptyText }}</text>
      </template>
    </ListPickerSheet>

    <!-- 认证弹层（未登录提交 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useAuthSheetStore } from '@/stores/auth-sheet'
import * as momentApi from '@/api/moment'
import * as dishApi from '@/api/dish'
import { getImageUrl } from '@/utils/image'
import type { Moment, RelatedType } from '@/types/moment'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import AppButton from '@/components/AppButton.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ListPickerSheet from '@/components/ListPickerSheet.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import type { RelatedItem } from '@/types/related-item'

const dishStore = useDishStore()
const userStore = useUserStore()
const authSheetStore = useAuthSheetStore()

const content = ref('')
const images = ref<string[]>([])
const submitting = ref(false)

/** 软键盘高度（px）：键盘弹起时于内容尾部补等高空隙，使发布按钮可滚动到键盘之上 */
const keyboardInset = ref(0)
function onKeyboardHeightChange(e: any) {
  keyboardInset.value = Number(e?.detail?.height) || 0
}

// 动态入口：自由关联（菜品 / 档口 / 不关联）；dishId 仅表示预选关联菜品（分享探店）
const relatedSheetOpen = ref(false)
const selectedRelated = ref<RelatedItem | null>(null)

/**
 * 关联选择（ListPickerSheet：搜索列表由本页供给，缓存上限一次拉 10 条）
 * 无「不关联」选项：一项都未选即代表不关联（自由动态）；再次点击已选菜品可取消关联
 */
const relatedOptions = ref<{ key: string; label: string; sub?: string; image?: string }[]>([])
const relatedSelectedKey = computed(() => (selectedRelated.value ? `dish-${selectedRelated.value.id}` : null))
/** 关联搜索关键词（moment-detail-publish-ux：#empty 空态文案区分「暂无菜品可关联」/「未找到对应菜品」） */
const relatedKeyword = ref('')
const relatedSheetEmptyText = computed(() =>
  relatedKeyword.value.trim() ? '未找到对应菜品' : '暂无菜品可关联',
)
let relatedSeq = 0

async function loadRelatedCandidates(kw: string) {
  relatedKeyword.value = kw
  const seq = ++relatedSeq
  try {
    const res = await dishApi.searchDishesPage({ keyword: kw, page: 1, pageSize: 10 })
    if (seq !== relatedSeq) return
    relatedOptions.value = res.list.map(d => ({
      key: `dish-${d.id}`,
      label: d.name,
      sub: '关联菜品',
      image: getImageUrl(d.image),
    }))
  } catch (err) {
    if (seq !== relatedSeq) return
    // 静默：请求失败不呈现任何占位，异常仅记录
    console.error('[publish] 搜索关联菜品失败', err)
    relatedOptions.value = []
  }
}

watch(() => relatedSheetOpen.value, (v) => { if (v) loadRelatedCandidates('') })

/** 关联选择项点击：菜品 toggle（再次点击同一项即取消关联＝回到不关联） */
function onRelatedOptionPick(opt: { key: string; label: string; image?: string }) {
  const id = Number(opt.key.replace('dish-', ''))
  if (!Number.isFinite(id)) return
  onRelatedSelect({ id, type: 'dish', name: opt.label, image: opt.image || '' })
}

/** 完成：应用当前选中并关闭 */
function onRelatedConfirmSheet() {
  relatedSheetOpen.value = false
}

// 编辑态（仅动态可编辑）
const editId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)
/** 编辑回填基线（moment-detail-publish-ux：返回二次确认相对其判定改动） */
const baselineContent = ref('')
const baselineImages = ref<string[]>([])

const pageTitle = computed(() => (isEdit.value ? '编辑动态' : '发布动态'))
const submitText = computed(() => (isEdit.value ? '保存并重新提交' : '发布'))

const relatedLabel = computed(() => {
  if (!selectedRelated.value) return '不关联（自由动态）'
  // 动态仅可关联菜品（产品决策）：不再显示「菜品·」前缀
  return selectedRelated.value.name
})

/** 发布按钮使能：正文非空才可发布；仅上传图片而正文空白时仍置灰（产品口径：必须填写文字内容） */
const canSubmit = computed(() => !!content.value.trim())

/** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由外层热区兜底 toast */
function onSubmitAreaTap() {
  if (!canSubmit.value) {
    uni.showToast({ title: '请填写动态正文内容', icon: 'none' })
  }
}

/**
 * 页面级认证拦截：未认证（游客）不得停留在发布表单——进入即弹认证引导；
 * 关闭弹层且仍未完成认证则退出页面（§5.y：只有完成学号邮箱认证才可写动态）。
 */
let authGuardArmed = false
function guardVerifiedOrLeave(): boolean {
  if (userStore.isVerified()) return true
  authGuardArmed = true
  userStore.requireAuth()
  return false
}
watch(
  () => authSheetStore.visible,
  (v, prev) => {
    if (!v && prev && authGuardArmed && !userStore.isVerified()) {
      authGuardArmed = false
      backToHome()
    }
  },
)

/** 返回二次确认判定：新发布=正文/图片任一非空；编辑=相对回填基线有改动（moment-detail-publish-ux） */
const imagesEqual = (a: string[], b: string[]) => a.length === b.length && a.every((x, i) => x === b[i])
const isDirty = computed(() => {
  if (editId.value != null) {
    return content.value !== baselineContent.value || !imagesEqual(images.value, baselineImages.value)
  }
  return !!content.value.trim() || images.value.length > 0
})

function confirmLeave() {
  if (!isDirty.value) {
    backToHome()
    return
  }
  uni.showModal({
    title: '提示',
    content: '内容尚未保存，确定要离开吗？',
    confirmText: '离开',
    cancelText: '继续编辑',
    success: (res) => {
      if (res.confirm) backToHome()
    },
  })
}

// N07 修复：提交后延迟返回定时器句柄，离开页面时清理，避免手动返回后多退一层
let navTimer: ReturnType<typeof setTimeout> | null = null
onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  navTimer = null
})

function onRelatedSelect(item: RelatedItem) {
  // 二次点击同一项取消关联（toggle）
  if (selectedRelated.value && selectedRelated.value.id === item.id && selectedRelated.value.type === item.type) {
    selectedRelated.value = null
  } else {
    selectedRelated.value = item
  }
}

async function submit() {
  // 游客点发布：登录成功后由 AuthSheet 自动继续提交（与动态发布 requireAuth(action) 行为一致）
  if (!userStore.requireAuth(submit)) return
  const text = content.value.trim()
  // 正文必填：仅图片不可发布（与 canSubmit 同一口径，兜底直接调用 submit 的场景）
  if (!text) {
    uni.showToast({ title: '请填写动态正文内容', icon: 'none' })
    return
  }
  if (isEdit.value) {
    // 编辑动态：走动态更新
    if (editId.value == null) return
    submitting.value = true
    try {
      await momentApi.updateMoment(editId.value, {
        content: text,
        images: images.value,
        relatedType: selectedRelated.value ? (selectedRelated.value.type as RelatedType) : 'none',
        relatedId: selectedRelated.value ? selectedRelated.value.id : null,
      })
      uni.showToast({ title: '已重新提交审核', icon: 'success' })
      if (navTimer) clearTimeout(navTimer)
      navTimer = setTimeout(() => uni.navigateBack(), 600)
    } catch (e: any) {
      uni.showToast({ title: e.message || '提交失败', icon: 'none' })
    } finally {
      submitting.value = false
    }
    return
  }

  // 动态态（朋友圈式：图文/关联菜品，无评分、不同步评价）
  submitting.value = true
  try {
    await momentApi.publishMoment({
      content: text,
      images: images.value,
      relatedType: selectedRelated.value ? (selectedRelated.value.type as RelatedType) : 'none',
      relatedId: selectedRelated.value ? selectedRelated.value.id : null,
    })
    uni.showToast({ title: '发布成功，审核中', icon: 'success' })
    if (navTimer) clearTimeout(navTimer)
    navTimer = setTimeout(() => uni.navigateBack(), 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad(async (query) => {
  // 先确保登录态就绪（避免静默登录竞态把已认证用户误判为游客），再执行页面级认证拦截
  await userStore.silentLogin()
  guardVerifiedOrLeave()

  if (query?.id) {
    // 编辑动态（仅动态可编辑）：回填
    editId.value = Number(query.id)
    try {
      const m: Moment | null = await momentApi.getMomentDetail(Number(query.id))
      if (!m) {
        uni.showToast({ title: '动态不存在或已删除', icon: 'none' })
        return
      }
      content.value = m.content
      images.value = [...m.images]
      baselineContent.value = m.content
      baselineImages.value = [...m.images]
      // 编辑态回填关联信息：动态无独立 rating 字段（评价与动态已扁平化打通），
      // 评分仅在「评价入口」（query.dishId）下才有意义，编辑动态不涉及评分回填
      if (m.relatedType && m.relatedType !== 'none' && m.relatedId) {
        selectedRelated.value = { id: m.relatedId, name: m.relatedName || '', image: '', type: m.relatedType as 'dish' | 'stall' }
      }
    } catch {
      uni.showToast({ title: '加载动态失败', icon: 'none' })
    }
    return
  }

  if (query?.dishId) {
    // 分享菜品动态：预选关联菜品（朋友圈式，无评分、不同步评价）
    const id = Number(query.dishId)
    try {
      await dishStore.fetchDetail(id)
      selectedRelated.value = { id, name: dishStore.currentDish?.name || '', image: '', type: 'dish' }
    } catch (e) {
      console.error('[publish-moment] 菜名加载失败', e)
    }
  }
})
</script>

<style scoped>
.publish-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap {
  flex: 1;
  overflow-y: auto;
  padding-top: var(--spacing-md);
  /* 底部安全区留白：避免提交按钮被 iOS 底部横条 / 键盘遮挡（全局 .scroll-wrap 底部留白被本页覆盖，须补齐） */
  padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom));
}
.block {
  background: var(--bg-card);
  padding: var(--spacing-md);
  margin: 0 var(--spacing-md) var(--spacing-md);
  box-shadow: var(--shadow-card);
  border-radius: var(--radius-card);
}
.section-sub { font-size: var(--font-aux); color: var(--text-tertiary); margin-left: var(--spacing-xs); }
/* 锁定菜品：只读展示（不可重选/清除），无点击态 */
.dish-locked {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-soft);
  border-radius: var(--radius-card);
}
.dish-locked-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-locked-badge {
  flex-shrink: 0;
  font-size: var(--font-tiny);
  color: var(--color-primary);
  background: var(--bg-card);
  border-radius: var(--radius-tag);
  padding: 4rpx 16rpx;
}
/* 关联对象：单行紧凑表单项（标题 + 选填 + 当前值 + 箭头），高度与普通列表行一致（96rpx）。
   不再沿用「大卡片内嵌选择行」结构，释放的纵向空间补给正文输入区 */
.block--row { padding: 0 var(--spacing-md); }
.form-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 96rpx;
  -webkit-tap-highlight-color: transparent;
}
.form-row:active { opacity: 0.7; }
.form-label { flex-shrink: 0; font-size: var(--font-body); font-weight: var(--weight-medium); color: var(--text-primary); }
.form-hint { flex-shrink: 0; font-size: var(--font-tiny); color: var(--text-tertiary); }
.form-value {
  margin-left: auto;
  min-width: 0;
  max-width: 340rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 未关联态：降为三级灰字，不作为「已选信息」呈现 */
.form-value--empty { color: var(--text-tertiary); }
.form-arrow { flex-shrink: 0; }
.rating-panel {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0;
}
/* 正文输入：与写评价/反馈弹窗 textarea 同款（bg-input 浅底 + radius-card + 无边框）。
   mp-weixin 下 textarea 背景若用 --bg-page（与页面背景同色）会与卡片/页面背景混淆，
   出现“内容区域上下被背景色遮挡”的观感——统一使用 --bg-input 浅底与页面背景区分 */
.content-input {
  width: 100%;
  /* 增高：承接关联对象压缩后释放的纵向空间，打字时可见更多预览内容 */
  height: 520rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  line-height: 1.6;
  padding: var(--spacing-sm);
  background: var(--bg-input);
  border-radius: var(--radius-card);
  border: none;
  box-sizing: border-box;
}
.counter {
  display: block;
  text-align: right;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  margin-top: var(--spacing-xs);
  font-variant-numeric: tabular-nums;
}
/* 提交按钮：页内流式（不吸底），随内容滚动到表单末尾；
   moment-detail-publish-ux：与图片模块间加大留白，底部安全区由 .scroll-wrap padding 承载 */
.submit-area {
  padding: var(--spacing-md) var(--spacing-md) 0;
}
/* 键盘占位：高度由 keyboardInset 动态注入（px），仅在键盘弹起时占空间 */
.keyboard-spacer { flex-shrink: 0; }
</style>
