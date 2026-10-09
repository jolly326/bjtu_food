<template>
  <view class="page profile-edit-page" :style="{ '--kb-px': keyboardHeight + 'px' }">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="个人信息" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 极简无卡片：信息区收进**一块** `.module-wrap`（暖奶米半透，规格由全局类承担）；
           行与行之间**不设分割线**，靠留白区分。昵称行的底线是**输入控件自身边界**，予以保留。 -->
      <view class="info-card module-wrap">
        <!-- 头像 -->
        <view class="info-row info-tappable" hover-class="pressed" role="button" aria-label="更换头像" @tap="changeAvatar">
          <text class="info-label">头像</text>
          <view class="avatar-wrap">
            <image v-if="avatar" :src="getThumbImageUrl(avatar)" class="avatar" :class="{ uploading: avatarUploading }" />
            <view v-else class="avatar" :class="{ uploading: avatarUploading }">
              <ImagePlaceholder name="user" :size="52" />
            </view>
            <AppIcon name="arrow" :size="28" :color="COLOR_MAP['text-tertiary']" class="row-arrow" />
          </view>
        </view>

        <!-- 昵称：可改行取全站表单**下划线语言** —— 本行 1rpx 底线即输入项底线，聚焦转主色 -->
        <view class="info-row info-row--field" :class="{ 'is-focused': nicknameFocused }">
          <text class="info-label">昵称</text>
          <!-- 键盘避让：`:adjust-position="false"` 关掉整页上顶，改由底部操作栏按键盘高度自行抬升（防双位移） -->
          <input
            v-model="nickname"
            class="nickname-input"
            aria-label="昵称"
            :aria-required="true"
            placeholder="请输入昵称"
            maxlength="20"
            :adjust-position="false"
            placeholder-class="input-placeholder"
            @focus="nicknameFocused = true"
            @blur="nicknameFocused = false"
          />
        </view>

        <!-- 校园邮箱（只读）：唯一来源 bindEmail（认证判据同源；未认证以 '--' 占位） -->
        <view class="info-row">
          <text class="info-label">校园邮箱</text>
          <text class="info-value info-value-email">{{ bindEmail || EMPTY_FIELD_TEXT }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 主操作**常驻底部**（基线 §1.1 通则：页头恒不承载业务操作）：
         外层热区承接「置灰态点击」——`AppButton` 在禁用 / 在途时不 emit press，由这里兜底提示。 -->
    <view class="submit-bar" @tap="onSaveAreaTap">
      <AppButton text="保存" :disabled="!canSave" :loading="saving" @press="onSavePress" />
    </view>

  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { getThumbImageUrl } from '@/utils/image'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { uploadAvatarImage } from '@/api/upload'
import { backToHome } from '@/utils/back'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppIcon from '@/components/AppIcon.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import AppButton from '@/components/AppButton.vue'
// 图标色须传**实色**（AppIcon 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import { EMPTY_FIELD_TEXT } from '@/constants/copy'

/* ===== 键盘避让（基线 §1.7 ①：固定底栏页必声明） =====
   微信对 `position: fixed` 底栏的默认顶起表现不稳定，故显式接管：监听键盘高度 →
   底栏 `bottom` 抬到键盘之上（页面根的 `--kb-px`）、滚动区底部留白同步加高；
   昵称输入框同步 `:adjust-position="false"`，避免「整页上顶 + 本条抬升」双位移。 */
const keyboardHeight = ref(0)
/** 键盘高度回调：`uni` 事件参数按结构类型收窄（与 BaseSheet 的 keyboardLift 同源） */
function onKeyboardHeight(res: { height?: number }) {
  keyboardHeight.value = res?.height ?? 0
}
/** 监听注册标记（`off` 必须与 `on` 成对） */
let keyboardWatching = false
onMounted(() => {
  uni.onKeyboardHeightChange(onKeyboardHeight)
  keyboardWatching = true
})

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
/** 校园邮箱展示值：唯一来源 bindEmail（未认证为空，行内以 '--' 占位，不空白） */
const bindEmail = computed(() => userInfo.value?.bindEmail || '')

// N07/审计#2 修复：userInfo 在 setup 时可能仍为 null（静默登录异步回填），
// 直接用快照会导致头像/昵称永久空白且回写空值。改为响应式派生 + watch immediate 回填。
const avatar = ref('')
const nickname = ref('')
const saving = ref(false)
/** 昵称输入聚焦态（驱动该行底线转主色） */
const nicknameFocused = ref(false)
/** 回填基线：进入 / 外部刷新资料时同步，用于「无改动禁用」判据 */
const initialNickname = ref('')
const initialAvatar = ref('')

watch(
  () => userInfo.value,
  (u) => {
    if (!u) return
    if (u.avatar) avatar.value = u.avatar
    if (u.nickname) nickname.value = u.nickname
    initialNickname.value = nickname.value
    initialAvatar.value = avatar.value
  },
  { immediate: true },
)

/** 有改动才可保存（昵称与头像均与回填基线一致 ⇒ 主按钮禁用） */
const dirty = computed(() => nickname.value.trim() !== initialNickname.value || avatar.value !== initialAvatar.value)
/** 头像上传中：禁用重复选择 + 头像半透明反馈 */
const avatarUploading = ref(false)

// N07 修复：保存后延迟返回定时器句柄，离开页面时清理，避免手动返回后多退一层
let navTimer: ReturnType<typeof setTimeout> | null = null
onUnload(() => {
  if (navTimer) clearTimeout(navTimer)
  navTimer = null
  // 键盘监听与注册成对注销：页面返回 / 重进不得残留回调
  if (keyboardWatching) {
    uni.offKeyboardHeightChange(onKeyboardHeight)
    keyboardWatching = false
  }
})

/**
 * 头像选图（单张、压缩）+ 上传：只写本地态，落库随「保存」。
 *
 * 不复用 `ImagePicker`（多图 + 逐张压缩，`pick()` 为组件内私有）：头像是单图，且**与 UGC 配图同走
 * 一条上传链路**（`uploadUgcImage`：云存储 → 后端安检转存 COS）；故保留 `uni.chooseImage`
 * （`sizeType: 'compressed'` 已等价压缩口径）。
 */
function changeAvatar() {
  if (avatarUploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      avatarUploading.value = true
      try {
        const { url } = await uploadAvatarImage(res.tempFilePaths[0])
        if (!url) throw new Error('上传失败，请重试')
        avatar.value = url
        // MP-003：上传仅写本地态，落库需点「保存」，文案避免误导已保存
        toastInfo('上传成功，请点击保存')
      } catch (err) {
        // 违规 / 超限等由后端 message 直透（如「图片包含违规内容，无法上传」）；保留原头像
        toastError(err, '上传失败')
      } finally {
        avatarUploading.value = false
      }
    },
  })
}

async function save() {
  const name = nickname.value.trim()
  if (!name) {
    toastInfo('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await userStore.updateProfile({ nickname: name, avatar: avatar.value })
    toastSuccess('已保存')
    if (navTimer) clearTimeout(navTimer)
    navTimer = setTimeout(backToHome, 400)
  } catch (e) {
    // 后端业务 400 message 直透（如昵称违规「内容包含违规信息，请修改后重试」），网络失败回落固定文案
    toastError(e, '保存失败')
  } finally {
    saving.value = false
  }
}
/** 可保存 = 有改动且不在途（驱动底部操作栏内 `AppButton` 的禁用档） */
const canSave = computed(() => dirty.value && !saving.value)

/** 底栏「保存」按下：`AppButton` 在禁用 / 在途时不 emit ⇒ 此处只处理可点路径 */
function onSavePress() {
  save()
}

/** 底栏外层热区：承接「置灰态点击」的可见反馈（点击不静默失效；在途由按钮自身忽略点击） */
function onSaveAreaTap() {
  if (saving.value) return
  if (!dirty.value) toastInfo('没有可保存的改动')
}
</script>

<style scoped lang="scss">
/* 常驻底部操作条：唯一实现见 styles/_action-bar.scss */
@use '../../styles/action-bar' as action;
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.profile-edit-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.profile-edit-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
/* Round 26 修复：① 补 `min-height: 0` —— flex 子项默认 `min-height: auto` ⇒ 不收缩 ⇒ 内容把容器撑高 ⇒
   与页根形成双层滚动（多余滚动 + 底部空白）；② 去掉 `overflow-y: auto` —— 本容器是 `scroll-view`，
   滚动由组件内部实现，外挂 CSS 只会在 H5 叠出第二根滚动条。 */
/* 底部内距 = 操作条等高 + 安全区 + 键盘高度（键盘弹起时内容不被底栏 / 键盘遮住） */
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-md) 0 calc(var(--action-bar-height) + env(safe-area-inset-bottom) + var(--spacing-lg) + var(--kb-px, 0px)); }
/* 信息模块：底色 / 圆角 / 阴影 / 内距由全局 `.module-wrap` 承担。
   行间**不设分割线**，改用 flex gap 留白区分（昵称行除外 —— 它的底线是输入控件自身边界）。 */
.info-card {
  margin: 0 var(--page-gutter);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.info-row {
  display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md);
  padding: var(--spacing-sm) 0;
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
/* 可改行（昵称）：全站表单**下划线语言** —— 本行 1rpx 底线即输入项底线，聚焦转主色 */
.info-row--field { border-bottom: 1rpx solid var(--border-color); }
.info-row--field.is-focused { border-bottom-color: var(--color-primary); }
/* 可点行（头像）按压反馈：走 hover-class="pressed"，底色语言同「我的」页列表行 */
.info-row.info-tappable.pressed { background-color: var(--bg-soft); opacity: 1; }
.info-label { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); flex-shrink: 0; }
.avatar-wrap { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 大头像（104rpx）圆角正方形：与「我的」页 hero 头像**同语言**（尺寸按各自区块定：本页 104rpx /
   「我的」页 120rpx）；`overflow: hidden` 用于把头像占位（`ImagePlaceholder`）裁到圆角内 */
.avatar { width: var(--avatar-size-md); height: var(--avatar-size-md); border-radius: var(--radius-icon); overflow: hidden; background: var(--bg-page); transition: opacity var(--duration-fast) var(--ease-out); }
.avatar.uploading { opacity: 0.6; }
.row-arrow { flex-shrink: 0; }
/* 昵称输入：无可改行的浅底容器，取全站表单**下划线语言**（透明底、无边框、右对齐；
   底线由所在行的行分隔线承担，聚焦转主色见 `.info-row--field.is-focused`） */
.nickname-input { flex: 1; min-width: 0; text-align: right; padding-right: var(--spacing-xs); font-size: var(--font-body); color: var(--text-primary); background: transparent; border: none; }
.input-placeholder { color: var(--text-tertiary); }
.info-value { font-size: var(--font-body); color: var(--text-secondary); }
/* 邮箱较长：允许右对齐但自动换行不溢出 */
.info-value-email { max-width: 62%; text-align: right; word-break: break-all; }
/* ===== 保存：**常驻底部操作栏**（基线 §1.1 通则：页头恒不承载业务操作） ===== */
.submit-bar {
  @include action.action-bar;
  /* 键盘抬起时整条抬到键盘之上（`--kb-px` 由页面根按键盘高度写入）；收起归零，不残留悬空 */
  bottom: var(--kb-px, 0px);
  /* 左右与页面 gutter 同轴（上下内距由 mixin 承担） */
  padding-left: var(--page-gutter);
  padding-right: var(--page-gutter);
}
</style>
