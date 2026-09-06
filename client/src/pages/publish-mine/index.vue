<template>
  <view class="page publish-mine-page">
    <Header title="我发布的" @back="backToHome" />

    <!-- 直接展示一列我发布的动态（无分类 tab；被退回的会通过系统通知提醒） -->
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view class="moment-list">
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

    </scroll-view>

    <!-- 三点菜单：仅分享（作者自己的动态不提供举报；通用 ActionSheet） -->
    <ActionSheet
      :open="moreOpen"
      :items="publishMoreItems"
      @close="moreOpen = false"
      @select="onPublishMoreSelect"
    />

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
/** publish-mine —— 「我发布的」列表页（作者视角）
 * - 一列铺排当前认证用户本人发布的动态；游客/未认证进入时 loadData 内 requireAuth 弹认证，
 *   认证成功后（isVerified false→true）watch 自动加载列表；
 * - 已退回（rejected）动态展示退回原因，「编辑重提」与点卡均跳 /pages/publish-moment?id= 重提；
 * - 其余态点击进动态详情页 /pages/moment/index；作者自己的动态三点菜单仅「分享」（ActionSheet，不提供举报）；
 * - 支持下拉刷新；onShow 从编辑/发布页返回时自动刷新（避免编辑或重提后内容变更不展示）。
 */
import { ref, computed, watch } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { buildSharePayload, clearShareState, sharedMoment } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import MomentCard from './MomentCard.vue'
import ActionSheet from '@/components/ActionSheet.vue'
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

/** 菜单项：作者列表仅分享 */
const publishMoreItems = computed(() =>
  moreMoment.value
    ? [{ key: 'share', label: '分享', icon: 'share' }]
    : [],
)

function onPublishMoreSelect(key: string) {
  if (key === 'share' && moreMoment.value) sharedMoment.value = moreMoment.value
}

/** 已退回动态的「编辑重提」主入口 */
function goEditMoment(m: Moment) {
  if (m.auditStatus === 'rejected' && m.id) {
    uni.navigateTo({ url: `/pages/publish-moment/index?id=${m.id}` })
  }
}

const loading = ref(false)
const refresherTriggered = ref(false)

async function loadData() {
  if (!userStore.requireAuth()) return
  loading.value = true
  try {
    moments.value = await momentApi.getMyMoments()
  } catch (err) {
    // 静默：请求失败不呈现任何占位，异常仅记录，恢复靠下拉刷新
    console.error('[publish-mine] 加载我的动态失败', err)
    moments.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/publish-moment/index?id=${m.id}` })
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
.publish-mine-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0 0 var(--spacing-lg); }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }

</style>
