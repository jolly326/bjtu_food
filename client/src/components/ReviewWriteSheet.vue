<template>
  <view class="rw-mask" @tap="close">
    <view class="rw-sheet" @tap.stop>
      <view class="rw-handle" />
      <view class="rw-head">
        <text class="rw-title">写评价</text>
        <view class="rw-close" @tap="close">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>

      <view v-if="dishName" class="rw-dish">菜品：{{ dishName }}</view>

      <view class="rw-body">
        <view class="rw-field">
          <text class="rw-label">总体评分<text class="rw-req">*</text></text>
          <view class="rw-stars">
            <view
              v-for="n in 5"
              :key="n"
              class="rw-star"
              :class="{ active: star >= n }"
              @tap="star = n"
            >
              <IconSvg :name="star >= n ? 'star-filled' : 'star'" :size="52" :color="star >= n ? 'var(--color-warning)' : 'var(--text-quaternary)'" />
            </view>
          </view>
        </view>

        <view class="rw-field">
          <text class="rw-label">标签</text>
          <view class="rw-tags">
            <view
              v-for="t in tagOptions"
              :key="t"
              class="rw-tag"
              :class="{ on: selectedTags.includes(t) }"
              @tap="toggleTag(t)"
            >{{ t }}</view>
          </view>
        </view>

        <view class="rw-field">
          <text class="rw-label">评价内容</text>
          <textarea
            v-model="content"
            class="rw-text"
            :maxlength="500"
            placeholder="说说这道菜的味道、分量、环境…"
            placeholder-class="rw-ph"
          />
        </view>

        <view class="rw-field">
          <text class="rw-label">图片</text>
          <ImageUploader v-model="images" :max="9" />
        </view>
      </view>

      <view class="rw-foot">
        <button class="rw-submit" :class="{ disabled: star < 1 || submitting }" @tap="onSubmit">
          {{ submitting ? '提交中…' : '提交评价' }}
        </button>
      </view>

      <AuthSheet v-model="showAuth" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from './IconSvg.vue'
import ImageUploader from './ImageUploader.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import { useUserStore } from '@/stores/user'
import { useDishStore } from '@/stores/dish'

const props = defineProps<{
  dishId: number
  dishName?: string
}>()

const emit = defineEmits<{
  submitted: []
  close: []
}>()

const userStore = useUserStore()
const dishStore = useDishStore()

const star = ref(0)
const content = ref('')
const images = ref<string[]>([])
const submitting = ref(false)
const showAuth = ref(false)

const tagOptions = ['分量足', '口味赞', '性价比高', '环境好', '上菜快', '干净卫生', '偏咸', '偏辣', '值得回购']
const selectedTags = ref<string[]>([])

function toggleTag(t: string) {
  const i = selectedTags.value.indexOf(t)
  if (i >= 0) selectedTags.value.splice(i, 1)
  else selectedTags.value.push(t)
}

function close() {
  emit('close')
}

async function onSubmit() {
  if (star.value < 1) {
    uni.showToast({ title: '请先选择评分', icon: 'none' })
    return
  }
  if (!userStore.isVerified()) {
    showAuth.value = true
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await dishStore.submitReview({
      dishId: props.dishId,
      rating: star.value,
      content: content.value.trim(),
      images: images.value,
      // 美团式写评：动态/评价已隔离，绝不回写动态
      tags: selectedTags.value,
    })
    uni.showToast({ title: '评价已提交，审核中', icon: 'none' })
    emit('submitted')
    close()
  } catch (e: any) {
    if (e?.code === 4031 || e?.code === 403) {
      showAuth.value = true
      return
    }
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.rw-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}
.rw-sheet {
  width: 100%;
  background: var(--bg-card);
  border-radius: var(--radius-sheet) var(--radius-sheet) 0 0;
  padding: var(--spacing-lg) var(--spacing-lg) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.rw-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: var(--radius-pill);
  background: var(--border-bold);
  margin: 0 auto var(--spacing-md);
}
.rw-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
}
.rw-title { font-size: var(--font-title); font-weight: var(--weight-bold); color: var(--text-primary); }
.rw-close {
  width: 56rpx; height: 56rpx;
  display: flex; align-items: center; justify-content: center;
  transition: transform var(--duration-fast) var(--ease-out);
}
.rw-close:active { transform: scale(var(--press-scale)); }
.rw-dish { font-size: var(--font-aux); color: var(--text-tertiary); margin-bottom: var(--spacing-md); }
.rw-body { overflow-y: auto; }
.rw-field { margin-bottom: var(--spacing-lg); }
.rw-label { display: block; font-size: var(--font-body); color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.rw-req { color: var(--color-error); margin-left: 4rpx; }
.rw-stars { display: flex; gap: var(--spacing-sm); }
.rw-star { transition: transform var(--duration-fast) var(--ease-out); }
.rw-star:active { transform: scale(var(--press-scale)); }
.rw-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.rw-tag {
  padding: 10rpx 24rpx;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-aux);
  border: 2rpx solid transparent;
  transition: transform var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out);
}
.rw-tag:active { transform: scale(var(--press-scale)); }
.rw-tag.on {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.rw-text {
  width: 100%;
  min-height: 180rpx;
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  padding: var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  box-sizing: border-box;
}
.rw-ph { color: var(--text-quaternary); }
.rw-foot { margin-top: var(--spacing-md); }
.rw-submit {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  border: none;
  transition: transform var(--duration-fast) var(--ease-out), opacity var(--duration-fast) var(--ease-out);
}
.rw-submit:active { transform: scale(var(--press-scale)); }
.rw-submit.disabled { opacity: 0.5; }
@media (prefers-reduced-motion: reduce) {
  .rw-close, .rw-star, .rw-tag, .rw-submit { transition: none !important; }
  .rw-close:active, .rw-star:active, .rw-tag:active, .rw-submit:active { transform: none !important; }
}
</style>
