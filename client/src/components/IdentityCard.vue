<template>
  <!-- 身份卡（跨页唯一实现，口径见 docs/ui/client/公共组件与形态基线.md §三）：
       「我的」页用户信息模块与「我的主页」身份卡是**同一身份版面的两种形态**，内容同源同字符串。
       · 两态同结构：顶部 6rpx 条纹 / 头像 / 主行 / 副行 / 右侧动作位；
       · 顶部条纹两态恒在（游客态 transparent ⇒ 两态等高），由卡壳圆角裁切；
       · 动作位由 `mode` 决定（**均仅认证态渲染**）：`entry` = 进入箭头、`edit` = 「编辑个人信息」胶囊；
         游客态两页都**不渲染动作位**（整段仍可点，无认证拦截）。 -->
  <view
    class="identity-card"
    :class="{ 'identity-card--verified': verified, 'identity-card--tappable': mode === 'entry' }"
    :role="mode === 'entry' ? 'button' : undefined"
    :aria-label="mode === 'entry' ? '查看我的主页' : undefined"
    @tap="onCardTap"
  >
    <!-- 顶部 6rpx 主色软条纹：通栏贴顶、由卡壳圆角裁切；游客态 transparent（占位保留，两态等高） -->
    <view class="identity-card-bar" />
    <view class="identity-card-body">
      <view class="identity-card-avatar-wrap">
        <ImageFallback v-if="avatar" :src="avatar" class="identity-card-avatar" />
        <view v-else class="identity-card-avatar">
          <ImagePlaceholder name="user" :size="60" />
        </view>
      </view>
      <view class="identity-card-meta">
        <text class="identity-card-name">{{ displayNickname(nickname, verified) }}</text>
        <text class="identity-card-sub">{{ displaySubLine(bindEmail, verified) }}</text>
      </view>
      <!-- 动作位：两页**均仅认证态渲染**（基线 §三：「游客态两页均不渲染动作位」）
           —— 游客态整段仍可点（无认证拦截），只是不展示入口暗示 -->
      <IconSvg
        v-if="verified && mode === 'entry'"
        name="arrow"
        :size="28"
        :color="COLOR_MAP['text-tertiary']"
        class="identity-card-arrow"
      />
      <view
        v-else-if="verified && mode === 'edit'"
        class="identity-card-edit"
        role="button"
        aria-label="编辑个人信息"
        hover-class="pressed"
        @tap.stop="emit('edit')"
      >
        <text class="identity-card-edit-text">编辑个人信息</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * IdentityCard —— 身份卡（「我的」页 / 「我的主页」共用）。
 *
 * · 文案口径（昵称兜底「食客 / 游客」、副行「校园邮箱 / 未完成校园认证」）由 `utils/userDisplay` 单点派生；
 * · 排版参数走 `styles/_identity-card.scss` 共享 partial（两页同值）；
 * · `mode`：`entry` = 整段可点（`@tap`）、`edit` = 右侧渲染「编辑个人信息」胶囊（`@edit`，仅认证态）。
 */
import IconSvg from '@/components/IconSvg.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { displayNickname, displaySubLine } from '@/utils/userDisplay'

const props = withDefaults(defineProps<{
  /** 昵称（空值按认证态「食客」/ 游客态「游客」兜底） */
  nickname?: string
  /** 校园邮箱（认证态副行；空值回落占位符） */
  bindEmail?: string
  /** 头像地址（空值 / 失败 → 统一人形占位） */
  avatar?: string
  /** 是否已认证（决定条纹色、副行文案、动作位） */
  verified: boolean
  /** `entry` = 整段可点进入「我的主页」；`edit` = 右侧「编辑个人信息」胶囊 */
  mode?: 'entry' | 'edit'
}>(), {
  nickname: '',
  bindEmail: '',
  avatar: '',
  mode: 'entry',
})

const emit = defineEmits<{
  /** `entry` 模式整段点击 */
  (e: 'tap'): void
  /** `edit` 模式点「编辑个人信息」 */
  (e: 'edit'): void
}>()

function onCardTap() {
  if (props.mode === 'entry') emit('tap')
}
</script>

<style scoped lang="scss">
@use '../styles/identity-card' as idcard;

.identity-card {
  /* `width: 100%`：mp-weixin 下自定义组件宿主节点非块级盒，显式撑满避免内容收缩 */
  width: 100%;
  display: flex;
  flex-direction: column;
}
/* 条纹：通栏贴顶（游客态 transparent ⇒ 两态等高），由卡壳 `overflow: hidden` 裁到圆角内 */
.identity-card-bar {
  height: var(--strip-height);
  background: transparent;
}
.identity-card--verified .identity-card-bar {
  background: var(--color-primary-soft);
}
/* 卡内主体：头像 / 主副行 / 右侧动作位 —— 排版参数见基线 §三（共享 partial） */
.identity-card-body {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
}
.identity-card-avatar-wrap {
  flex-shrink: 0;
}
.identity-card-avatar {
  @include idcard.avatar;
}
.identity-card-meta {
  @include idcard.meta;
}
.identity-card-name {
  @include idcard.name;
}
.identity-card-sub {
  @include idcard.sub;
}
.identity-card-arrow {
  flex-shrink: 0;
}

/* 「编辑个人信息」胶囊：主色描边 + 胶囊圆角；命中区经 ::after **仅纵向**扩至 ≥88rpx（视觉尺寸不变） */
.identity-card-edit {
  position: relative;
  flex-shrink: 0;
  padding: var(--spacing-2xs) var(--spacing-md);
  border-radius: var(--radius-pill);
  border: 1rpx solid var(--color-primary);
  transition: background-color var(--duration-fast) var(--ease-out);
}
.identity-card-edit::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
.identity-card-edit.pressed {
  background-color: var(--bg-soft);
}
.identity-card-edit-text {
  font-size: var(--font-tiny);
  color: var(--color-primary-text);
  font-weight: var(--weight-medium);
}

/* entry 模式：整段为可点件（按压反馈 = 浅底色，过渡与无障碍降级同其他列表项） */
.identity-card--tappable {
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.identity-card--tappable:active {
  background-color: var(--bg-soft);
}

@media (prefers-reduced-motion: reduce) {
  .identity-card-edit,
  .identity-card--tappable {
    transition: none;
  }
}
</style>
