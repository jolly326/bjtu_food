<template>
  <view class="page activity-page">
    <Header title="最新活动" @back="backToHome" />
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :scroll-with-animation="false"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="activity-list" v-if="list.length > 0">
        <view
          v-for="act in list"
          :key="act.id"
          class="activity-card"
          hover-class="pressed"
          hover-stay-time="80"
          @tap="openActivity(act)"
        >
          <!-- 公众号文章卡片：来源标识 + 日期 / 标题 / 摘要 / 阅读原文 -->
          <view class="activity-card-head">
            <view class="activity-source">
              <view class="activity-source-icon">
                <IconSvg name="broadcast-fill" :size="26" color="var(--color-primary)" />
              </view>
              <text class="activity-source-text">食堂公众号</text>
            </view>
            <text v-if="act.publishTime" class="activity-time">{{ formatTime(act.publishTime) }}</text>
          </view>
          <text class="activity-title">{{ act.title }}</text>
          <text v-if="act.description" class="activity-desc">{{ act.description }}</text>
          <view class="activity-card-foot">
            <!-- 有外链：阅读原文小胶囊（方案 A），整卡 @tap 跳转 -->
            <view v-if="act.articleUrl" class="activity-link-pill">
              <text class="activity-link-pill-text">阅读原文</text>
              <IconSvg name="arrow-fat" :size="24" color="var(--color-primary)" />
            </view>
            <!-- 无外链：弱化「敬请关注」 -->
            <text v-else class="activity-link activity-link--muted">敬请关注</text>
          </view>
        </view>
      </view>
      <!-- 空状态：无活动且非加载/刷新中 → 居中友好提示（Q 版圆润，避免白屏） -->
      <view v-else-if="!loading && !refreshing" class="activity-empty">
        <view class="activity-empty-icon">
          <IconSvg name="broadcast-fill" :size="76" color="var(--color-primary)" />
        </view>
        <text class="activity-empty-text">暂时还没有新活动，敬请期待~</text>
      </view>
      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getActivities, type ActivityItem } from '@/api/activity'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/nav'
import { activityWebviewUrl } from '@/utils/routes'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'


const page = ref(1)
const pageSize = 20
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const list = ref<ActivityItem[]>([])


function formatTime(t: string) {
  return formatDateTime(t)
}

async function fetchPage(reset: boolean) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getActivities({ page: page.value, pageSize })
    if (reset) list.value = res
    else list.value = list.value.concat(res)
    finished.value = res.length < pageSize
    if (!reset && res.length > 0) page.value += 1
  } catch (e) {
    console.error('[activity] 加载失败', e)
    if (reset) list.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  if (!finished.value) fetchPage(false)
}

function onRefresh() {
  page.value = 1
  finished.value = false
  refreshing.value = true
  fetchPage(true)
}

function openActivity(act: ActivityItem) {
  if (act.articleUrl) {
    uni.navigateTo({ url: activityWebviewUrl(act.articleUrl) })
  }
}

onLoad(() => {
  page.value = 1
  fetchPage(true)
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* activity-page-q-style：奶油米白暖底 */
  background: var(--bg-warm);
}
.scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
}
.activity-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-md) var(--spacing-lg);
}
/* Q 版卡片：白底大圆角 + 柔和暖调投影 + 宽松内边距（activity-page-q-style） */
.activity-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-warm);
  transition: background-color var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈：底色弱化（遵循全局按压语言，不引入 transform scale） */
.activity-card.pressed { background-color: var(--bg-soft); }

.activity-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
/* 来源标识：公众号小图标 + 文字 */
.activity-source {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}
.activity-source-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: var(--radius-circle);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-source-text {
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}
.activity-title {
  margin-top: var(--spacing-sm);
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.5;
}
.activity-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.activity-desc {
  margin-top: var(--spacing-sm);
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.7;
}
.activity-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-md);
}
.activity-link {
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}
.activity-link--muted {
  color: var(--text-tertiary);
}
/* 阅读原文：浅主题红小胶囊（方案 A；随整卡 @tap 跳转） */
.activity-link-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 56rpx;
  padding: 0 var(--spacing-md);
  background: var(--color-primary-soft);
  border-radius: var(--radius-pill);
}
.activity-link-pill-text {
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
}

/* 空状态：居中广播图标 + 治愈文案 */
.activity-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: 180rpx var(--spacing-lg);
}
.activity-empty-icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-circle);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-empty-text {
  font-size: var(--font-body);
  color: var(--text-tertiary);
  text-align: center;
}
</style>
