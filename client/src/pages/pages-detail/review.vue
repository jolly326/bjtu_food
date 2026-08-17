<template>
  <view class="page review-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="发表评价" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 菜品选择（必选；从动态页进入需搜索菜名） -->
      <CardSection>
        <text class="section-label">菜品</text>
        <view v-if="selectedDishName" class="dish-selected" @tap="clearDish">
          <IconSvg name="dish" :size="32" color="var(--color-primary)" />
          <text class="dish-selected-name">{{ selectedDishName }}</text>
          <text class="dish-selected-hint">点击重选</text>
        </view>
        <block v-else>
          <input
            class="dish-input"
            :value="dishKeyword"
            placeholder="搜索并选择菜品"
            placeholder-class="dish-input-ph"
            @input="(e: any) => searchDishList(e.detail.value)"
          />
          <view v-if="searching" class="dish-hint">搜索中…</view>
          <view v-else-if="dishCandidates.length > 0" class="dish-candidates">
            <view
              v-for="c in dishCandidates"
              :key="c.id"
              class="dish-candidate"
              :class="{ pressed: dishPressed === c.id }"
              @touchstart="dishPressed = c.id"
              @touchend="dishPressed = null"
              @touchcancel="dishPressed = null"
              @mousedown="dishPressed = c.id"
              @mouseup="dishPressed = null"
              @mouseleave="dishPressed = null"
              @tap="pickDish(c.id, c.name)"
            >
              <text class="dish-candidate-name">{{ c.name }}</text>
              <text v-if="c.canteen" class="dish-candidate-canteen">{{ c.canteen }}</text>
            </view>
          </view>
          <view v-else-if="dishKeyword" class="dish-hint">无匹配菜品，换个关键词试试</view>
        </block>
      </CardSection>

      <!-- 评分 -->
      <CardSection>
        <text class="section-label">评分</text>
        <view class="rating-panel">
          <Rating v-model="form.rating" :readonly="false" :show-text="true" :star-size="48" />
        </view>
      </CardSection>

      <!-- 评论 -->
      <CardSection>
        <text class="section-label">评价内容</text>
        <textarea
          v-model="form.content"
          class="content-input"
          placeholder="分享你的用餐体验..."
          :maxlength="MAX_CONTENT_LENGTH"
        />
        <text class="char-count">{{ form.content.length }}/{{ MAX_CONTENT_LENGTH }}</text>
      </CardSection>

      <!-- 图片上传 -->
      <CardSection>
        <text class="section-label">图片（最多3张）</text>
        <view class="image-list">
          <view v-for="(img, idx) in form.images" :key="idx" class="image-item">
            <image :src="img" mode="aspectFill" class="preview-img" />
            <view class="remove-btn" @tap="removeImage(idx)"><IconSvg name="close" :size="24" color="var(--badge-dark-text)" /></view>
          </view>
          <view v-if="form.images.length < MAX_IMAGES" class="image-upload" @tap="selectImage">
            <IconSvg name="plus" :size="60" color="var(--text-tertiary)" />
          </view>
        </view>
      </CardSection>

      <!-- 同步到社区动态（评价与动态打通：评价可见即动态可见，直接上广场） -->
      <CardSection>
        <view class="share-row" @tap="toggleShare">
          <view class="share-info">
            <text class="share-title">同步到社区动态</text>
            <text class="share-desc">同步后自动生成一条关联本菜品的动态，直接上社区广场</text>
          </view>
          <view class="toggle" :class="{ on: form.shareToMoment }" @tap.stop="toggleShare">
            <view class="toggle-knob" />
          </view>
        </view>
      </CardSection>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 提交按钮（吸底） -->
    <view class="submit-bar">
      <AppButton text="提交评价" type="primary" :disabled="!canSubmit" :loading="uploading" @click="handleSubmit" />
    </view>

    <!-- 认证弹层（未登录提交评价 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { searchDishes } from '@/api/dish'
import { uploadImage as uploadImageApi } from '@/api/upload'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import Rating from '@/components/Rating.vue'
import IconSvg from '@/components/IconSvg.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()
const MAX_CONTENT_LENGTH = 200
const MAX_IMAGES = 3
const dishId = ref(0)
const from = ref('')
const uploading = ref(false)
const form = reactive({
  rating: 5,
  content: '',
  images: [] as string[],
  // 双向联通：评价默认同步到动态（开关默认打开，用户可手动关）
  shareToMoment: true,
})

// 菜品选择（发布动态.md：必选菜品；从动态页进入需搜索菜名）
const selectedDishName = ref('')
const dishKeyword = ref('')
const dishCandidates = ref<{ id: number; name: string; canteen?: string }[]>([])
const searching = ref(false)
const dishPressed = ref<number | null>(null)

// N07 修复：提交后延迟返回定时器句柄，离开页面时清理，避免手动返回后多退一层
let navTimer: ReturnType<typeof setTimeout> | null = null
onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  navTimer = null
})

// 请求序号守卫：快速输入时丢弃过期响应，避免旧请求后返回覆盖最新候选（竞态）
let searchSeq = 0
async function searchDishList(kw: string) {
  dishKeyword.value = kw
  if (!kw.trim()) {
    dishCandidates.value = []
    return
  }
  const seq = ++searchSeq
  searching.value = true
  try {
    const list = await searchDishes({ keyword: kw.trim(), page: 1, pageSize: 10 })
    if (seq !== searchSeq) return // 已有更新的请求发出，丢弃本次过期结果
    dishCandidates.value = list.map((d) => ({ id: d.id, name: d.name, canteen: d.canteen }))
  } catch (e) {
    if (seq !== searchSeq) return
    console.error('[review] 菜品搜索失败', e)
    dishCandidates.value = []
  } finally {
    if (seq === searchSeq) searching.value = false
  }
}

function pickDish(id: number, name: string) {
  dishId.value = id
  selectedDishName.value = name
  dishKeyword.value = ''
  dishCandidates.value = []
}

function clearDish() {
  dishId.value = 0
  selectedDishName.value = ''
  dishKeyword.value = ''
  dishCandidates.value = []
}

function toggleShare() {
  form.shareToMoment = !form.shareToMoment
}

const canSubmit = computed(
  () => dishId.value > 0 && form.rating > 0 && form.content.trim().length > 0
)

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

function selectImage() {
  uni.chooseImage({
    count: 3 - form.images.length,
    success: (res) => {
      form.images.push(...res.tempFilePaths.slice(0, 3 - form.images.length))
    },
  })
}

async function handleSubmit() {
  if (!userStore.requireAuth(() => handleSubmit())) return
  if (!canSubmit.value || uploading.value) return
  uploading.value = true

  try {
    // 将本地图片上传到服务器，获取可访问的 URL
    const uploadedUrls: string[] = []
    for (const localPath of form.images) {
      try {
        const url = await uploadImageApi(localPath)
        uploadedUrls.push(url)
      } catch {
        // 单张上传失败不影响整体提交
        console.warn('图片上传失败，跳过:', localPath)
      }
    }

    await dishStore.submitReview({
      dishId: dishId.value,
      rating: form.rating,
      content: form.content,
      images: uploadedUrls,
      shareToMoment: form.shareToMoment,
    })
    uni.showToast({ title: '评价成功', icon: 'success' })
    // 置脏标记：返回菜品详情页 onShow 时据此刷新评价列表与综合评分卡（#3/#11/#18）
    dishStore.reviewsDirty = true
    // 从菜品详情页进入：返回详情并刷新评价列表；其余默认进入动态广场
    if (navTimer) clearTimeout(navTimer)
    if (from.value === 'dish') {
      navTimer = setTimeout(() => uni.navigateBack(), 1500)
    } else {
      navTimer = setTimeout(() => uni.reLaunch({ url: '/pages/community/index' }), 1500)
    }
  } catch (e: any) {
    // 同一用户对同一菜品重复评价：展示后端 400 冲突提示（uk_review_user_dish）
    const msg = e?.message || '提交失败'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    uploading.value = false
  }
}

onLoad(async (query) => {
  if (query?.from) from.value = String(query.from)
  if (query?.dishId) {
    dishId.value = Number(query.dishId)
    // 取菜名展示（详情页带入，展示只读菜名）
    try {
      await dishStore.fetchDetail(dishId.value)
      selectedDishName.value = dishStore.currentDish?.name || ''
    } catch (e) {
      console.error('[review] 菜名加载失败', e)
    }
  }
})
</script>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
.submit-bar { padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
.section-label {
  font-size: var(--font-body);
  color: var(--text-primary);
  display: block;
  margin-bottom: var(--spacing-sm);
  font-weight: var(--weight-bold);
}
.rating-panel {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0;
}
/* 菜品选择 */
.dish-selected {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-soft);
  border-radius: var(--radius-md);
  -webkit-tap-highlight-color: transparent;
}
.dish-selected-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-selected-hint {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.dish-input {
  width: 100%;
  height: 72rpx;
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  background: var(--bg-page);
  border-radius: var(--radius-md);
  border: none;
  outline: none;
  box-sizing: border-box;
}
.dish-input-ph { color: var(--text-tertiary); }
.dish-hint {
  margin-top: var(--spacing-sm);
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.dish-candidates {
  margin-top: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  background: var(--bg-page);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.dish-candidate {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  padding: var(--spacing-sm) var(--spacing-md);
  transition: background-color 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.dish-candidate.pressed { background-color: var(--bg-soft); }
.dish-candidate-name {
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-candidate-canteen {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.content-input {
  width: 100%;
  height: 200rpx;
  font-size: var(--font-body);
  line-height: 1.6;
  border: none;
  outline: none;
  background: var(--bg-page);
  border-radius: var(--radius-card);
  padding: var(--spacing-sm);
  box-sizing: border-box;
}
.char-count {
  display: block;
  text-align: right;
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  margin-top: var(--spacing-xs);
  font-variant-numeric: tabular-nums;
}
.image-list {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
.image-item {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  border-radius: var(--radius-icon);
  overflow: hidden;
}
.preview-img {
  width: 100%;
  height: 100%;
}
.remove-btn {
  position: absolute;
  top: 0;
  right: 0;
  width: 48rpx;
  height: 48rpx;
  background: var(--badge-dark-bg);
  color: var(--badge-dark-text);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-tiny);
  transition: transform 0.12s var(--ease-out), opacity 0.12s var(--ease-out);
}
.remove-btn:active { transform: scale(var(--press-scale)); opacity: 0.85; }
.image-upload {
  width: 180rpx;
  height: 180rpx;
  border: 4rpx dashed var(--border-color);
  border-radius: var(--radius-icon);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.image-upload:active { transform: scale(var(--press-scale)); }

/* 同步到社区动态（自绘 Apple 风格开关，走 token） */
.share-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  -webkit-tap-highlight-color: transparent;
}
.share-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.share-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.share-desc { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1.5; }
.toggle {
  flex-shrink: 0;
  width: 88rpx;
  height: 52rpx;
  border-radius: 16px;
  background: var(--border-color);
  position: relative;
  transition: background 0.2s var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.toggle.on { background: var(--color-primary); }
.toggle-knob {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: var(--color-on-primary);
  box-shadow: var(--shadow-card);
  transition: transform 0.2s var(--ease-out);
}
.toggle.on .toggle-knob { transform: translateX(36rpx); }

</style>
