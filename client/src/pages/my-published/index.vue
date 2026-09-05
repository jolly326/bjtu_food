<template>
  <view class="page my-published-page">
    <Header title="我发布的" @back="backToHome" />

    <!-- 直接展示一列我发布的动态（无分类 tab；被退回的会通过系统通知提醒） -->
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 加载/失败/空态统一由 StateView 一次承载（ui-feed-loading：空态复用 EmptyState，不在列表区重复放置加载态） -->
      <StateView
        v-if="moments.length === 0"
        :loading="loading"
        :failed="loadFailed"
        :empty="true"
        error-text="加载失败，请重试"
        empty-text="你还没有发布动态"
        empty-icon="comment"
        :empty-retry="true"
        @retry="loadData"
      />

      <view v-else class="moment-list">
        <view v-for="m in moments" :key="m.id">
          <MomentCard
            :moment="m"
            @select="goDetail"
            @go-related="goRelated"
            @more="openMore"
            @edit="goEditMoment"
          />
        </view>
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 三点菜单：仅分享（作者自己的动态不提供举报；allow-report=false） -->
    <MomentActionSheet
      :open="moreOpen"
      :moment="moreMoment"
      :allow-report="false"
      @update:open="moreOpen = $event"
    />

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import MomentCard from './MomentCard.vue'
import MomentActionSheet from '@/components/MomentActionSheet.vue'
import StateView from '@/components/StateView.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const userStore = useUserStore()
const moments = ref<Moment[]>([])
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/dish/index?id=${id}` })
}

/* ===== 三点菜单（MomentCard @more → 页面级 ActionSheet，作者列表仅分享） ===== */
const moreOpen = ref(false)
const moreMoment = ref<Moment | null>(null)

function openMore(m: Moment) {
  moreMoment.value = m
  moreOpen.value = true
}

/** 已退回动态的「编辑重提」主入口 */
function goEditMoment(m: Moment) {
  if (m.auditStatus === 'rejected' && m.id) {
    uni.navigateTo({ url: `/pages/publish-content/index?id=${m.id}` })
  }
}

const loading = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

async function loadData() {
  if (!userStore.requireAuth()) return
  loading.value = true
  loadFailed.value = false
  try {
    moments.value = await momentApi.getMyMoments()
  } catch (e: any) {
    // 网络/业务错误 http 层已统一 toast，页面仅置失败态（空态展示重试），避免重复提示
    loadFailed.value = true
    moments.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/publish-content/index?id=${m.id}` })
  } else {
    uni.navigateTo({ url: `/pages/moment/index?id=${m.id}` })
  }
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    openDishDetail(m.relatedId)
  }
  // 档口详情页已下线（2026-08-09）：相关档口不再展示跳转入口
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

// 游客直访时弹认证（loadData 内 requireAuth）；认证成功后（isVerified 由 false→true）自动加载
watch(
  () => userStore.isVerified(),
  () => loadData(),
  { immediate: true },
)
onShow(() => {
  clearShareState()
  // #5 修复：从编辑/发布页返回时刷新列表，否则编辑内容或重新提交后的变更不展示（除非手动下拉）。
  // 用 requireAuth 判断避免游客重复触发；loading 防重入避免与 watch 首载重叠。
  if (userStore.isVerified() && !loading.value) loadData()
})
onShareAppMessage(() => buildSharePayload())
</script>

<style scoped>
.my-published-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0; }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }

</style>
