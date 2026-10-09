<template>
  <view class="page mine-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <!-- 「我的」是 TabBar 主根页（TabBar 经 reLaunch 切换，无带参跳转），恒不需要返回箭头：
         showBack 显式传 false（AppHeader 默认值为 true，不能省略） -->
    <Header title="我的" :show-back="false" />

    <scroll-view class="mine-scroll" scroll-y>
      <!-- ===== 极简无卡片：两块独立 `.module-wrap` = ① 个人信息 + 快捷功能 → ② 账号设置 =====
           分组依据 = Apple HIG 语义分组：身份信息与快捷操作同组；账号协议/注销属独立设置组。
           两块之间**只靠页面留白分组**（容器 flex gap；小程序 WXSS 不支持 `+` 兄弟选择器），模块内不设分割线。
           **仅容器结构与视觉** —— 业务逻辑 / 跳转 / 弹窗 / 角标 / 无障碍不变。 -->
      <view class="mine-modules">
        <!-- 模块 A：个人信息 + 快捷功能 -->
        <view class="module-wrap mine-card">
        <!-- 用户信息模块（模块 A 内第 1 段）：认证态 = 昵称 + 校园邮箱；游客态 = 昵称 +「未完成校园认证」。
             内容与排版真源见 docs/ui/client/公共组件与形态基线.md §三。
             整段点击进入「我的主页」（游客与认证态同达，无认证拦截）；
             认证动作的单一入口为宫格「身份认证」格，本段不放「去认证」按钮 -->
        <IdentityCard
          mode="entry"
          :nickname="userInfo?.nickname"
          :bind-email="bindEmail"
          :avatar="userInfo?.avatar"
          :verified="isVerified"
          @enter="onUserCardTap"
        />

        <!-- 功能宫格（模块 A 内第 2 段）：一行 3 格（意见反馈 / 系统通知 / 身份认证），每格整格热区；
             每格无独立外壳（无边框 / 圆角 / 阴影），仅保留图标外层圆形浅底；
             个人信息编辑在「我的主页」页（用户信息模块点击直接进入，无认证拦截） -->
        <view class="grid">
          <view
            v-for="cell in gridCells"
            :key="cell.key"
            class="grid-cell"
            role="button"
            :aria-label="cell.label"
            hover-class="pressed"
            @tap="cell.action"
          >
            <view class="grid-cell-icon">
              <AppIcon :name="cell.icon" :size="44" :color="COLOR_MAP['primary']" />
              <!-- 系统通知：存在未读时右上角标（无未读不显示）。
                   角标唯一语义 = 「有未读/待处理」；未读统一用橙（红仅留错误与危险）。
                   无障碍：角标承载信息，故给 aria-label 而非 aria-hidden。
                   「身份认证」格**不设角标** —— 认证完成是状态而非待办，用角标会被误读为待处理且点击不可消除；
                   认证状态由用户卡副行与条纹着色表达。 -->
              <view
                v-if="cell.key === 'notify' && notifyStore.unreadCount > 0"
                class="badge badge-dot"
                aria-label="有未读通知"
              />
            </view>
            <text class="grid-cell-label">{{ cell.label }}</text>
          </view>
        </view>
        </view>

        <!-- 模块 B：账号设置（独立第二块，底部不加分隔线） -->
        <view class="module-wrap more-group">
        <!-- 三行独立入口（用户协议 / 隐私政策 / 注销账号），行间细分隔线；
             每行整行热区（role="button"），注销行为危险弱化色 -->
        <view class="more-card">
          <view
            v-for="row in moreRows"
            :key="row.key"
            class="more-row"
            :class="{ 'more-row--danger': row.danger }"
            role="button"
            :aria-label="row.label"
            hover-class="pressed"
            @tap="row.action"
          >
            <text class="more-row-text">{{ row.label }}</text>
            <AppIcon name="arrow" :size="28" :color="COLOR_MAP['text-tertiary']" class="more-row-arrow" />
          </view>
        </view>
        </view>
      </view>

      <!-- 版本行：纯展示（aria-hidden），**位于模块之外**、居中 -->
      <view class="app-footer" aria-hidden="true">
        <text class="app-footer-line">知行食记 v{{ appVersion }}</text>
        <text class="app-footer-line">北京交通大学 · 校园美食分享圈</text>
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页/我的 两主区切换（仅主根页显示，恒透明：背后即页底壁纸） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useRouteStore } from '@/stores/route'
const routeStore = useRouteStore()
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppIcon from '@/components/AppIcon.vue'
import IdentityCard from '@/components/IdentityCard.vue'
import TabBar from '@/components/TabBar.vue'
import { useUserStore } from '@/stores/user'
import { toastError, toastSuccess } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'
import { PATH, TAB_PROFILE } from '@/utils/routes'
import { deleteAccount } from '@/api/user'
import { COLOR_MAP, MODAL_CONFIRM_PRIMARY_COLOR } from '@/theme/tokens'

const userStore = useUserStore()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
/** 已认证（bindEmail 非空）——微信静默登录后恒有登录态，游客 / 认证由 isVerified() 单点派生区分 */
const isVerified = computed(() => userStore.isVerified())
/** 校园邮箱展示值：唯一来源 `bindEmail`（认证态副行） */
const bindEmail = computed(() => userInfo.value?.bindEmail || '')
/** 版本号：构建期由 vite.config.ts 从 manifest.json versionName 注入（小程序运行时读不到 manifest） */
const appVersion = __APP_VERSION__

onLoad(() => {
  // 进入「我的」确保静默登录已就绪（游客态才有认证前提）
  userStore.silentLogin()
})

// 每次进入「我的」刷新未读通知数（宫格红点角标；消息中心为登录级能力，游客与认证态同权刷新）
onShow(() => {
  // 锚定底部菜单栏：我的页始终显示并高亮
  routeStore.showTab(TAB_PROFILE)
  notifyStore.fetchUnread()
})

/** 用户卡点击：查看我的评价（游客直接进入——页内信息条与空态自洽，认证要求仅在评论操作时） */
function onUserCardTap() {
  uni.navigateTo({ url: PATH.myReviews })
}

/** 身份认证格（认证动作的**单一入口**）：未认证跳转独立认证页；已认证轻提示 */
function onCertTap() {
  if (!userStore.isVerified()) {
    authStore.requestAuth()
    return
  }
  toastSuccess('已完成身份认证')
}

/** 功能宫格数据（一行 3 格，顺序固定：意见反馈 / 系统通知 / 身份认证）；每格整格热区 */
interface GridCell {
  key: string
  icon: string
  label: string
  action: () => void
}

/** 合规入口：隐私政策（应用内页面，不依赖外部域名） */
function goPrivacy() {
  uni.navigateTo({ url: PATH.privacy })
}

/** 合规入口：用户协议（与隐私政策各自独立页面，入口分叉） */
function goAgreement() {
  uni.navigateTo({ url: PATH.agreement })
}

/** 注销账号（合规硬需求）：二次确认 → 后端匿名化 → 清本地态（旧 token 已被后端拉黑，静默登录建新游客号） */
function onAccountDelete() {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号将匿名化且不可恢复：重新登录将创建全新账号，你的评价与反馈会保留但不再关联身份，也不会回到新账号。',
    confirmText: '确认注销',
    confirmColor: MODAL_CONFIRM_PRIMARY_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteAccount()
        // 仅在服务端确认注销成功后才清本地态：失败时保留登录态与资料，
        // 否则用户会看到「注销失败」的提示却已被登出、资料被清空，且无恢复入口
        userStore.forceLogout()
        toastSuccess('账号已注销')
      } catch (e) {
        // 失败文案走统一出口 utils/error（e 为 null 时不会崩）
        toastError(e, '注销失败，请稍后重试')
      }
    },
  })
}

const gridCells: GridCell[] = [
  { key: 'feedback', icon: 'lightbulb', label: '意见反馈', action: () => uni.navigateTo({ url: PATH.feedback }) },
  { key: 'notify', icon: 'bell', label: '系统通知', action: () => uni.navigateTo({ url: PATH.notifications }) },
  { key: 'cert', icon: 'certificate', label: '身份认证', action: onCertTap },
]

/** 「其他」分组列表（三行固定：合规两行 + 账号危险操作一行；注销为 danger 弱化） */
const moreRows = [
  { key: 'agreement', label: '用户协议', danger: false, action: goAgreement },
  { key: 'privacy', label: '隐私政策', danger: false, action: goPrivacy },
  { key: 'deleteAccount', label: '注销账号', danger: true, action: onAccountDelete },
]
</script>

<style scoped lang="scss">
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层。
   结构：页根固定高（`height: 100vh / 100dvh`）+ 内部 `scroll-view`（`.mine-scroll`）
   —— 滚动区 `flex: 1` 自带裁剪，内容**不会**从透明的标题带背后经过（与首页同一结构性原则）。 */
.mine-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口，含地址栏）通常 **大于** `100dvh`（当前视口）。二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
.mine-page { min-height: 0; }
.mine-scroll { flex: 1; min-height: 0; }
/* 本页不再额外加容器级 tabbar 留白 —— 页脚（`.app-footer`，滚动区最后一块）已自带
   `calc(--tabbar-height + safe + --spacing-md)` 底部避让；若再叠加会多出 ≈100rpx 死空白，
   且短内容会被这层 padding 顶出滚动条（"空白滚动区域"根因之一）。 */

/* ===== 两块模块（`.module-wrap`）+ 容器留白 =====
   极简无卡片：模块底色 / 圆角 / 阴影 / 内距全部由全局 `.module-wrap` 承担，
   本页只负责「模块之间的页面留白（flex gap）+ 左右 gutter + 圆角裁切」。 */
.mine-modules {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-md) var(--page-gutter) 0;
}
.mine-card,
.more-group {
  /* 两块都是「内距由内容自持」的模块变体（`padding: 0`）：
     身份卡顶部 6rpx 条纹通栏贴顶 —— 要求内容通栏，不能再叠一层模块内距；列表行内距由行自身承担。
     `.mine-card` 同时承担条纹的圆角裁切。 */
  padding: 0;
  overflow: hidden;
}

/* 用户信息模块（模块 A 内第 1 段）**由公共组件 `IdentityCard`**（与「我的主页」同源，基线 §三）承载：
   条纹 / 头像 / 主副行 / 间距 / 按压反馈全部由组件自持。 */

/* 功能宫格（模块 A 内第 2 段）：一行三列内联布局，每格无独立外壳，整格热区，
   按压反馈 = `--bg-soft` 底色（保留 `--radius-btn` 小圆角，使按压底色不露直角）。 */
.grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); padding: var(--spacing-lg); }
.grid-cell { flex: 0 0 calc((100% - 2 * var(--spacing-md)) / 3); min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  border-radius: var(--radius-btn);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.grid-cell.pressed { background-color: var(--bg-soft); }
/* 浅粉圆底 + 2px 线性线条图标（复用既有 feature 卡 chip 基线，图标在上、标题在下） */
.grid-cell-icon {
  position: relative;
  width: var(--chip-size);
  height: var(--chip-size);
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 宫格标签：单行居中，**超长（>4 字）省略**而非换行 ——
   宫格是固定三列结构，标签换行会顶高该格、使同行三格的图标-文字节奏不齐；
   省略只影响极端长标签（当前三个标签恒为 4 字），是兜底而非常态。
   ⚠️ `align-self: stretch` 必须有：格容器是纵向 flex + `align-items: center`，
   标签在**交叉轴**上会被收成内容宽（`nowrap` ⇒ 内容宽 = 文本全宽）⇒ 省略号不触发、直接溢出格宽。 */
.grid-cell-label {
  display: block;
  align-self: stretch;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  text-align: center;
}
/* 角标：贴卡片（图标 chip）右上角，不遮蔽图标主体 */
.badge { position: absolute; top: -6rpx; right: -6rpx; z-index: 1; }
/* 未读角标：14rpx 圆点。未读态**统一用橙**（与系统通知未读竖条同色），红仅留错误与危险 */
.badge-dot { width: 14rpx; height: 14rpx; border-radius: var(--radius-circle); background: var(--color-primary-amber); }

/* 「其他」列表 = 模块 B 的内部列表容器（`.more-card` 只承担圆角裁切）：
   三行独立列表项，**行间不设分割线**，靠上下内距留白区分（极简无卡片口径）。 */
.more-card { overflow: hidden; }
.more-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--tap-target-size);
  padding: var(--spacing-sm) var(--page-gutter);
  -webkit-tap-highlight-color: transparent;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.more-row.pressed { background-color: var(--bg-soft); }
.more-row-text { font-size: var(--font-body); color: var(--text-body); }
.more-row--danger .more-row-text { color: var(--color-error); }
.more-row-arrow { flex-shrink: 0; }

/* 版本行：独立于列表之外的居中纯展示 */
.app-footer {
  padding: var(--spacing-xl) 0 calc(var(--tabbar-height) + env(safe-area-inset-bottom) + var(--spacing-md));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xs);
}
.app-footer-line {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  line-height: 1.5;
}

@media (prefers-reduced-motion: reduce) {
  .grid-cell, .more-row { transition: none; }
}
</style>
