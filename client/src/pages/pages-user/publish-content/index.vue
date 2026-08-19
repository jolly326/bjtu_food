<template>
  <view class="page publish-page" :class="{ 'theme-dark': theme.isDark }">
    <Header :title="pageTitle" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 关联对象：
           - 评价入口（菜品详情带入 dishId）：锁定所属菜品，只读不可改（必选）
           - 动态入口：自由选择（仅关联菜品 / 不关联，产品决策：动态不支持关联档口），评分选填 -->
      <view class="block">
        <SectionTitle title="关联对象">
          <template #extra><text class="section-sub">{{ lockedDish ? '必选' : '选填' }}</text></template>
        </SectionTitle>
        <view v-if="lockedDish" class="dish-locked">
          <IconSvg name="dish" :size="32" color="var(--color-primary)" />
          <text class="dish-locked-name">{{ lockedDishName }}</text>
          <text class="dish-locked-badge">已关联</text>
        </view>
        <view v-else class="related-picker" @tap="relatedSheetOpen = true" role="button" :aria-label="relatedLabel">
          <IconSvg name="dish" :size="30" color="var(--color-primary)" class="related-picker-icon" />
          <text class="related-label">{{ relatedLabel }}</text>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
        </view>
      </view>

      <!-- 评分：有菜品关联时才显示（评价入口必选默认 5 星；动态入口选填 0=未评分） -->
      <view v-if="showRating && !isEdit" class="block">
        <SectionTitle title="评分">
          <template #extra><text class="section-sub">{{ lockedDish ? '必选' : '选填' }}</text></template>
        </SectionTitle>
        <view class="rating-panel">
          <Rating v-model="rating" :readonly="false" :show-text="true" :star-size="48" />
        </view>
      </view>

      <!-- 正文 -->
      <view class="block">
        <textarea
          class="content-input"
          v-model="content"
          placeholder="分享你的美食体验、探店灵感…"
          maxlength="500"
          :auto-height="true"
          :cursor-spacing="20"
        />
        <text class="counter">{{ content.length }}/500</text>
      </view>

      <!-- 图片上传（复用 ImageUploader：选图即上传、右上角删除、3 张计数） -->
      <view class="block">
        <SectionTitle title="图片">
          <template #extra><text class="section-sub">最多 3 张</text></template>
        </SectionTitle>
        <ImageUploader v-model="images" :max="3" />
      </view>

      <!-- 提交按钮：页内流式（不吸底），随内容滚动到表单末尾 -->
      <view class="submit-area">
        <AppButton :text="submitText" type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit" />
      </view>
    </scroll-view>

    <!-- 关联对象选择 Sheet（W5：走正式 API，返回真实 id） -->
    <RelatedPickerSheet
      :open="relatedSheetOpen"
      :selected="selectedRelated"
      @close="relatedSheetOpen = false"
      @clear="clearRelated"
      @select="onRelatedSelect"
      @confirm="onRelatedConfirm"
    />

    <!-- 认证弹层（未登录提交 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment, RelatedType } from '@/types/moment'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import RelatedPickerSheet from '@/components/RelatedPickerSheet.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import Rating from '@/components/Rating.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import type { RelatedItem } from '@/components/related-item'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()

const content = ref('')
const images = ref<string[]>([])
const submitting = ref(false)

// 评价入口：菜品详情带入 dishId → 锁定所属菜品（只读）
const dishId = ref(0)
const lockedDishName = ref('')
const lockedDish = computed(() => dishId.value > 0 && editId.value == null)

// 动态入口：自由关联（菜品 / 档口 / 不关联）
const relatedSheetOpen = ref(false)
const selectedRelated = ref<RelatedItem | null>(null)

// 评分：0 = 未评分（动态）；1-5 = 评分（有菜 + 评分 → 评价）
const rating = ref(0)

// 编辑态（仅动态可编辑，评价不可编辑）
const editId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)

const pageTitle = computed(() => (isEdit.value ? '编辑动态' : lockedDish.value ? '发表评价' : '发布动态'))
const submitText = computed(() => {
  if (isEdit.value) return '保存并重新提交'
  return lockedDish.value ? '提交评价' : '发布'
})

/** 当前是否已关联菜品（锁定菜品或自由选择菜品） */
const currentDishId = computed(() => {
  if (lockedDish.value) return dishId.value
  return selectedRelated.value && selectedRelated.value.type === 'dish' ? selectedRelated.value.id : 0
})
const hasDish = computed(() => currentDishId.value > 0)
const showRating = computed(() => hasDish.value || lockedDish.value)

/** 评价态：关联菜品 + 已评分 → 提交走评价接口并自动同步动态 */
const willBeReview = computed(() => hasDish.value && rating.value > 0)

const relatedLabel = computed(() => {
  if (!selectedRelated.value) return '不关联（自由动态）'
  const prefix = selectedRelated.value.type === 'dish' ? '菜品' : '档口'
  return `${prefix}·${selectedRelated.value.name}`
})

const canSubmit = computed(() => {
  if (!content.value.trim()) return false
  // 评价入口：评分必选（锁定菜品但未评分时阻止提交）
  if (!isEdit.value && lockedDish.value && rating.value <= 0) return false
  return true
})

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

function clearRelated() {
  // 弹窗内「不关联」为列表首项：仅清空选中，弹窗保持打开，由「完成」统一关闭（与其他选项一致）
  selectedRelated.value = null
}

/** 确定：组件回传当前选中项（selected），关闭弹层 */
function onRelatedConfirm(item: RelatedItem | null) {
  selectedRelated.value = item
  relatedSheetOpen.value = false
}

async function submit() {
  // 游客点发布：登录成功后由 AuthSheet 自动继续提交（与动态发布 requireAuth(action) 行为一致）
  if (!userStore.requireAuth(submit)) return
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写内容', icon: 'none' })
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

  // 评价态：关联菜品 + 已评分 → 评价（自动同步动态）；其余 → 发布动态
  if (willBeReview.value) {
    if (!canSubmit.value || submitting.value) return
    submitting.value = true
    try {
      // 图片已在 ImageUploader 选图时上传为 URL，这里直接提交
      await dishStore.submitReview({
        dishId: currentDishId.value,
        rating: rating.value,
        content: text,
        images: images.value,
        shareToMoment: true,
      })
      uni.showToast({ title: '评价成功，已同步动态', icon: 'success' })
      // 置脏标记：返回菜品详情页 onShow 时据此刷新评价列表与综合评分卡
      dishStore.reviewsDirty = true
      if (navTimer) clearTimeout(navTimer)
      if (lockedDish.value) {
        // 从菜品详情进入：返回详情并刷新评价列表
        navTimer = setTimeout(() => uni.navigateBack(), 1500)
      } else {
        navTimer = setTimeout(() => uni.reLaunch({ url: '/pages/community/index' }), 1500)
      }
    } catch (e: any) {
      // 同一用户对同一菜品重复评价：展示后端 400 冲突提示（uk_review_user_dish）
      uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
    } finally {
      submitting.value = false
    }
    return
  }

  // 动态态
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
      if (m.relatedType && m.relatedType !== 'none' && m.relatedId) {
        selectedRelated.value = { id: m.relatedId, name: m.relatedName || '', image: '', type: m.relatedType as 'dish' | 'stall' }
      }
    } catch {
      uni.showToast({ title: '加载动态失败', icon: 'none' })
    }
    return
  }

  if (query?.dishId) {
    // 评价入口：锁定所属菜品（只读展示），评分默认 5 星
    dishId.value = Number(query.dishId)
    rating.value = 5
    try {
      await dishStore.fetchDetail(dishId.value)
      lockedDishName.value = dishStore.currentDish?.name || ''
    } catch (e) {
      console.error('[publish-content] 菜名加载失败', e)
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
.related-picker {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  transition: transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.related-picker:active { transform: scale(var(--press-scale)); }
.related-picker-icon { flex-shrink: 0; }
.related-label {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
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
  min-height: 220rpx;
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
/* 提交按钮：页内流式（不吸底），随内容滚动到表单末尾 */
.submit-area {
  padding: var(--spacing-sm) var(--spacing-md) 0;
}
</style>
