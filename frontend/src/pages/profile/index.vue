<template>
  <view class="page profile-page">
    <Header title="我的" />

    <scroll-view class="scroll-wrap" scroll-y>
      <template v-if="!isLoggedIn">
        <view class="auth-shell">
          <view class="auth-hero">
            <view class="hero-badge">
              <text class="hero-logo">{{ EMOJI.dishPlaceholder }}</text>
            </view>
            <text class="hero-title">{{ authTitle }}</text>
            <text class="hero-subtitle">{{ authSubtitle }}</text>
          </view>

          <view class="auth-panel">
            <view v-if="formError" class="form-error" @tap="clearError">
              <text class="form-error-icon">{{ EMOJI.warning }}</text>
              <text class="form-error-text">{{ formError }}</text>
            </view>
            <template v-if="mode === 'login'">
              <view class="form-head">
                <text class="form-title">欢迎回来</text>
                <text class="form-note">使用账号密码登录，发现和评价校园美食</text>
              </view>

              <view class="group-card">
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.profile }}</text>
                  <input v-model="loginForm.account" class="input-control" placeholder="账号 / 学号 / 校园邮箱" @input="clearError" />
                </view>

                <view v-if="loginType === 'password'" class="input-field">
                  <text class="input-icon">{{ EMOJI.lock }}</text>
                  <input v-model="loginForm.password" class="input-control" placeholder="密码" password @input="clearError" />
                </view>

                <template v-else>
                  <view class="input-field">
                    <text class="input-icon">{{ EMOJI.profile }}</text>
                    <input v-model="loginCodeForm.email" class="input-control" placeholder="校园邮箱" @input="clearError" />
                  </view>
                  <view class="input-field code-field">
                    <text class="input-icon">{{ EMOJI.lock }}</text>
                    <input v-model="loginCodeForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
                    <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('login')">{{ codeButtonText }}</text>
                  </view>
                </template>
              </view>

              <view class="row-actions">
                <text class="link-text" @tap="toggleLoginType">{{ loginType === 'password' ? '验证码登录' : '密码登录' }}</text>
                <text class="link-text" @tap="setMode('reset')">忘记密码</text>
              </view>

              <view class="primary-action" :class="{ disabled: isBusy }" @tap="loginType === 'password' ? handlePasswordLogin() : handleEmailLogin()">
                <text class="primary-action-text">{{ isBusy ? '正在登录...' : '登录' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">还没有账号？</text>
                <text class="prompt-link" @tap="setMode('register')">使用校园邮箱注册</text>
              </view>
            </template>

            <template v-else-if="mode === 'register'">
              <view class="form-head">
                <text class="form-title">创建账号</text>
                <text class="form-note">首次注册需要绑定校园邮箱，并设置之后登录使用的账号密码</text>
              </view>

              <view class="group-card">
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.profile }}</text>
                  <input v-model="registerForm.username" class="input-control" placeholder="账号 / 学号" @input="clearError" />
                </view>
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.profile }}</text>
                  <input v-model="registerForm.email" class="input-control" placeholder="校园邮箱，如 20240002@bjtu.edu.cn" @input="clearError" />
                </view>
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.dishPlaceholder }}</text>
                  <input v-model="registerForm.nickname" class="input-control" placeholder="昵称" @input="clearError" />
                </view>
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.lock }}</text>
                  <input v-model="registerForm.password" class="input-control" placeholder="设置密码，至少 6 位" password @input="clearError" />
                </view>
                <view class="input-field code-field">
                  <text class="input-icon">{{ EMOJI.lock }}</text>
                  <input v-model="registerForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
                  <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('register')">{{ codeButtonText }}</text>
                </view>
              </view>

              <view class="primary-action" :class="{ disabled: isBusy }" @tap="handleRegister">
                <text class="primary-action-text">{{ isBusy ? '正在注册...' : '注册' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">已有账号？</text>
                <text class="prompt-link" @tap="setMode('login')">返回登录</text>
              </view>
            </template>

            <template v-else>
              <view class="form-head">
                <text class="form-title">找回密码</text>
                <text class="form-note">通过已绑定的校园邮箱验证码重新设置密码</text>
              </view>

              <view class="group-card">
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.profile }}</text>
                  <input v-model="resetForm.email" class="input-control" placeholder="已绑定的校园邮箱" @input="clearError" />
                </view>
                <view class="input-field">
                  <text class="input-icon">{{ EMOJI.lock }}</text>
                  <input v-model="resetForm.newPassword" class="input-control" placeholder="新密码，至少 6 位" password @input="clearError" />
                </view>
                <view class="input-field code-field">
                  <text class="input-icon">{{ EMOJI.lock }}</text>
                  <input v-model="resetForm.code" class="input-control" placeholder="邮箱验证码" @input="clearError" />
                  <text class="code-action" :class="{ disabled: codeCountdown > 0 }" @tap="sendCode('reset')">{{ codeButtonText }}</text>
                </view>
              </view>


              <view class="primary-action" :class="{ disabled: isBusy }" @tap="handleResetPassword">
                <text class="primary-action-text">{{ isBusy ? '正在重置...' : '重置密码' }}</text>
              </view>

              <view class="bottom-prompt">
                <text class="prompt-muted">想起来了？</text>
                <text class="prompt-link" @tap="setMode('login')">返回登录</text>
              </view>
            </template>
          </view>
        </view>
      </template>

      <template v-else>
        <view class="user-header enter-up" :style="{ '--enter-i': 0 }">
          <view class="user-info-row">
            <view class="avatar-wrap" @tap="handleEditAvatar">
              <image v-if="userInfo?.avatar" :src="getImageUrl(userInfo.avatar)" class="avatar" />
              <view v-else class="avatar avatar-empty">
                <text class="avatar-fallback">{{ EMOJI.dishPlaceholder }}</text>
              </view>
            </view>
            <view class="user-meta">
              <view class="nickname-row" @tap="handleEditNickname">
                <text class="nickname">{{ userInfo?.nickname || '未知用户' }}</text>
              </view>
              <text class="user-id">用户 ID {{ userInfo?.id }}</text>
            </view>
          </view>
        </view>

        <view class="stats-row enter-up" :style="{ '--enter-i': 1 }">
          <view class="stat-item">
            <text class="stat-value">{{ userStore.userStats.reviewCount }}</text>
            <text class="stat-label">评价</text>
          </view>
        </view>

        <!-- 我要贡献（task-12.1 统一入口） -->
        <view class="contribute-card enter-up" :style="{ '--enter-i': 2 }" @tap="goContribute">
          <view class="contribute-icon">{{ EMOJI.plus }}</view>
          <view class="contribute-body">
            <text class="contribute-title">我要贡献</text>
            <text class="contribute-sub">发布菜品 / 提交档口 / 申请下架纠错</text>
          </view>
          <text class="contribute-arrow">{{ EMOJI.arrowRight }}</text>
        </view>

        <view class="menu-section">
          <view class="menu-group">
            <text class="menu-group-title">我的内容</text>
            <view class="menu-card">
              <view class="menu-item enter-up" :style="{ '--enter-i': 3 }" @tap="goToMySubmissions">
                <text class="menu-icon">{{ EMOJI.list }}</text>
                <text class="menu-label">我的提交</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
              <view class="menu-item enter-up" :style="{ '--enter-i': 4 }" @tap="goToMyMoments">
                <text class="menu-icon">{{ EMOJI.review }}</text>
                <text class="menu-label">我的动态</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
              <view class="menu-item enter-up" :style="{ '--enter-i': 6 }" @tap="goToReviews">
                <text class="menu-icon">{{ EMOJI.starFilled }}</text>
                <text class="menu-label">我的评价</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
              <view class="menu-item enter-up" :style="{ '--enter-i': 7 }" @tap="goToMyPublish">
                <text class="menu-icon">{{ EMOJI.edit }}</text>
                <text class="menu-label">我的发布</text>
                <text class="menu-hint">菜品/档口·审核状态</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
            </view>
          </view>

          <view class="menu-group">
            <text class="menu-group-title">消息与服务</text>
            <view class="menu-card">
              <view class="menu-item enter-up" :style="{ '--enter-i': 3 }" @tap="goToNotify">
                <text class="menu-icon">{{ EMOJI.bell }}</text>
                <text class="menu-label">消息中心</text>
                <view v-if="notifyStore.unreadCount > 0" class="menu-badge" />
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
              <view class="menu-item enter-up" :style="{ '--enter-i': 8 }" @tap="goToFeedback">
                <text class="menu-icon">{{ EMOJI.contact }}</text>
                <text class="menu-label">意见反馈</text>
                <text class="menu-hint">建议/Bug反馈</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
              <view class="menu-item enter-up" :style="{ '--enter-i': 9 }" @tap="goToSettings">
                <text class="menu-icon">{{ EMOJI.settings }}</text>
                <text class="menu-label">设置</text>
                <text class="menu-arrow">{{ EMOJI.arrowRight }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="version-row">
          <text class="version-text">食在交大 v1.0.0</text>
          <text class="version-sub">校园美食分享评价与社交内容平台</text>
        </view>

        <view class="logout-wrap">
          <AppButton text="退出登录" type="outline" @click="handleLogout" />
        </view>
      </template>

      <view v-if="showNicknameEditor" class="modal-mask" @tap="showNicknameEditor = false">
        <view class="modal-content" @tap.stop>
          <text class="modal-title">修改昵称</text>
          <input v-model="editingNickname" class="modal-input" placeholder="输入新昵称" maxlength="20" confirm-type="done" @confirm="confirmEditNickname" />
          <view class="modal-actions">
            <text class="modal-btn modal-btn-cancel" @tap="showNicknameEditor = false">取消</text>
            <text class="modal-btn modal-btn-confirm" @tap="confirmEditNickname">确认</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <CustomTabBar current="/pages/profile/index" />

    <!-- 我要贡献 Sheet（task-12.1） -->
    <view v-if="contributeOpen" class="sheet-mask" @tap="contributeOpen = false" />
    <view class="bottom-sheet" :class="{ open: contributeOpen }">
      <view class="sheet-head">
        <text class="sheet-title">我要贡献</text>
        <text class="sheet-close" @tap="contributeOpen = false">✕</text>
      </view>
      <view class="sheet-option" @tap="goPublishDish">
        <text class="sheet-option-icon">{{ EMOJI.dishPlaceholder }}</text>
        <view class="sheet-option-body">
          <text class="sheet-option-title">发布菜品</text>
          <text class="sheet-option-sub">新增一道菜品供大家发现</text>
        </view>
        <text class="sheet-option-arrow">{{ EMOJI.arrowRight }}</text>
      </view>
      <view class="sheet-option" @tap="goSubmitStall">
        <text class="sheet-option-icon">{{ EMOJI.location }}</text>
        <view class="sheet-option-body">
          <text class="sheet-option-title">提交档口</text>
          <text class="sheet-option-sub">新增你常去的档口</text>
        </view>
        <text class="sheet-option-arrow">{{ EMOJI.arrowRight }}</text>
      </view>
      <view class="sheet-option" @tap="goSubmitCanteen">
        <text class="sheet-option-icon">{{ EMOJI.home }}</text>
        <view class="sheet-option-body">
          <text class="sheet-option-title">提交食堂</text>
          <text class="sheet-option-sub">新增一个食堂（如新校区）</text>
        </view>
        <text class="sheet-option-arrow">{{ EMOJI.arrowRight }}</text>
      </view>
      <view class="sheet-option" @tap="openApplySheet">
        <text class="sheet-option-icon">{{ EMOJI.edit }}</text>
        <view class="sheet-option-body">
          <text class="sheet-option-title">申请下架 / 纠错</text>
          <text class="sheet-option-sub">对已存在菜品·档口·食堂发起申请</text>
        </view>
        <text class="sheet-option-arrow">{{ EMOJI.arrowRight }}</text>
      </view>
    </view>

    <!-- 申请下架/纠错 Sheet（task-12.1，POST /my/apply） -->
    <view v-if="applySheetOpen" class="sheet-mask" @tap="applySheetOpen = false" />
    <view class="bottom-sheet" :class="{ open: applySheetOpen }">
      <view class="sheet-head">
        <text class="sheet-title">申请下架 / 纠错</text>
        <text class="sheet-close" @tap="applySheetOpen = false">✕</text>
      </view>
      <view class="form-block">
        <text class="form-label">实体类型</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyEntityType === 'DISH' }" @tap="applyEntityType = 'DISH'">菜品</view>
          <view class="seg" :class="{ on: applyEntityType === 'STALL' }" @tap="applyEntityType = 'STALL'">档口</view>
          <view class="seg" :class="{ on: applyEntityType === 'CANTEEN' }" @tap="applyEntityType = 'CANTEEN'">食堂</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">申请动作</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyAction === 'CLOSE' }" @tap="applyAction = 'CLOSE'">下架 / 关闭</view>
          <view class="seg" :class="{ on: applyAction === 'CHANGE' }" @tap="applyAction = 'CHANGE'">纠错 / 变更</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">实体 ID</text>
        <input class="form-input" v-model="applyEntityId" type="number" placeholder="填写要申请的对象 ID" />
      </view>
      <view class="form-block">
        <text class="form-label">说明（选填）</text>
        <textarea class="form-textarea" v-model="applyReason" placeholder="请描述下架/纠错原因…" maxlength="500" :auto-height="true" />
      </view>
      <view class="sheet-submit">
        <AppButton text="提交申请" :loading="applySubmitting" @click="submitEntityApply" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useUserStore } from '@/stores/user'
import { useNotifyStore } from '@/stores/notify'
import { getImageUrl } from '@/utils/image'
import { EMOJI } from '@/utils/emoji'
import { uploadImage } from '@/api/upload'
import { resetPassword, sendEmailCode } from '@/api/user'
import { submitApply } from '@/api/apply'

type Mode = 'login' | 'register' | 'reset'
type LoginType = 'password' | 'email'
type CodePurpose = 'login' | 'register' | 'reset'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => !!userStore.token && !!userStore.userInfo)
const isBusy = computed(() => userStore.loading)

const mode = ref<Mode>('login')
const loginType = ref<LoginType>('password')
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 内联错误提示（Apple §16：错误在校验处附近内联展示，不弹裸 alert） */
const formError = ref('')
function setError(msg: string) {
  formError.value = msg
}
function clearError() {
  formError.value = ''
}

const loginForm = reactive({ account: '', password: '' })
const loginCodeForm = reactive({ email: '', code: '' })
const registerForm = reactive({ username: '', email: '', nickname: '', password: '', code: '' })
const resetForm = reactive({ email: '', newPassword: '', code: '' })

const authTitle = computed(() => {
  if (mode.value === 'register') return '加入食在交大'
  if (mode.value === 'reset') return '重设你的密码'
  return '食在交大'
})

const authSubtitle = computed(() => {
  if (mode.value === 'register') return '校园邮箱认证，只为校内用户开放'
  if (mode.value === 'reset') return '验证码确认身份后即可设置新密码'
  return '发现食堂美食、分享用餐体验'
})

const codeButtonText = computed(() => codeCountdown.value > 0 ? `${codeCountdown.value}s` : '获取验证码')

function setMode(next: Mode) {
  mode.value = next
  codeCountdown.value = 0
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function toggleLoginType() {
  loginType.value = loginType.value === 'password' ? 'email' : 'password'
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

function getEmailForPurpose(purpose: CodePurpose) {
  if (purpose === 'register') return registerForm.email.trim()
  if (purpose === 'reset') return resetForm.email.trim()
  return loginCodeForm.email.trim()
}

function isCampusEmail(email: string) {
  return /^[^\s@]+@bjtu\.edu\.cn$/i.test(email)
}

async function sendCode(purpose: CodePurpose) {
  if (codeCountdown.value > 0) return
  const email = getEmailForPurpose(purpose)
  if (!isCampusEmail(email)) {
    setError('请填写 @bjtu.edu.cn 校园邮箱')
    return
  }
  clearError()
  try {
    await sendEmailCode(email, purpose)
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCountdown()
  } catch (e: any) {
    setError(e.message || '验证码发送失败')
  }
}

async function handlePasswordLogin() {
  console.log('[profile] password login tapped')
  if (isBusy.value) return
  if (!loginForm.account.trim() || !loginForm.password) {
    setError('请填写账号和密码')
    return
  }
  clearError()
  try {
    await userStore.loginByPassword(loginForm.account.trim(), loginForm.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    setError(e.message || '登录失败')
  }
}

async function handleEmailLogin() {
  if (isBusy.value) return
  if (!isCampusEmail(loginCodeForm.email) || !loginCodeForm.code.trim()) {
    setError('请填写校园邮箱和验证码')
    return
  }
  clearError()
  try {
    await userStore.loginByEmailCode(loginCodeForm.email.trim(), loginCodeForm.code.trim())
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e: any) {
    setError(e.message || '登录失败')
  }
}

async function handleRegister() {
  if (isBusy.value) return
  if (!registerForm.username.trim() || !registerForm.nickname.trim() || !registerForm.password || !registerForm.code.trim()) {
    setError('请完整填写注册信息')
    return
  }
  if (!isCampusEmail(registerForm.email)) {
    setError('请填写 @bjtu.edu.cn 校园邮箱')
    return
  }
  if (registerForm.password.length < 6) {
    setError('密码至少 6 位')
    return
  }
  clearError()
  try {
    await userStore.register({
      username: registerForm.username.trim(),
      email: registerForm.email.trim(),
      nickname: registerForm.nickname.trim(),
      password: registerForm.password,
      code: registerForm.code.trim(),
    })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    loginForm.account = registerForm.username.trim()
    setMode('login')
    loginType.value = 'password'
  } catch (e: any) {
    setError(e.message || '注册失败')
  }
}

async function handleResetPassword() {
  if (isBusy.value) return
  if (!isCampusEmail(resetForm.email) || !resetForm.code.trim() || !resetForm.newPassword) {
    setError('请完整填写找回密码信息')
    return
  }
  if (resetForm.newPassword.length < 6) {
    setError('新密码至少 6 位')
    return
  }
  clearError()
  try {
    await resetPassword({
      email: resetForm.email.trim(),
      code: resetForm.code.trim(),
      newPassword: resetForm.newPassword,
    })
    uni.showToast({ title: '密码已重置，请登录', icon: 'success' })
    loginForm.account = resetForm.email.trim()
    setMode('login')
    loginType.value = 'password'
  } catch (e: any) {
    setError(e.message || '重置失败')
  }
}

const showNicknameEditor = ref(false)
const editingNickname = ref('')

function handleEditAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0]
      try {
        const url = await uploadImage(tempPath)
        await userStore.updateProfile({ avatar: url })
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch {
        uni.showToast({ title: '更新失败', icon: 'none' })
      }
    },
  })
}

function handleEditNickname() {
  editingNickname.value = userInfo.value?.nickname || ''
  showNicknameEditor.value = true
}

async function confirmEditNickname() {
  const name = editingNickname.value.trim()
  if (!name) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  try {
    await userStore.updateProfile({ nickname: name })
    showNicknameEditor.value = false
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '更新失败', icon: 'none' })
  }
}

function goToReviews() {
  // 我的评价：跳评价列表页（无 dishId，由后端 /my/reviews 支撑时显示我的评价）
  uni.navigateTo({ url: '/pages/pages-detail/review-list' })
}

function goToMyPublish() {
  uni.navigateTo({ url: '/pages/profile/my-publish' })
}

function goToMyMoments() {
  uni.navigateTo({ url: '/pages/my-moments/index' })
}

/** 我要贡献：统一入口 Sheet（task-12.1） */
function goContribute() {
  contributeOpen.value = true
}

const contributeOpen = ref(false)
const contributeApplying = ref(false)

/** Sheet 选项：发布菜品 / 提交档口 / 提交食堂 直接跳现有发布页（NEW 类，复用已验证链路） */
function goPublishDish() {
  contributeOpen.value = false
  uni.navigateTo({ url: '/pages/profile/publish-dish' })
}
function goSubmitStall() {
  contributeOpen.value = false
  uni.navigateTo({ url: '/pages/profile/submit-stall' })
}
function goSubmitCanteen() {
  contributeOpen.value = false
  uni.navigateTo({ url: '/pages/profile/submit-stall?type=canteen' })
}

/** 申请下架/纠错：打开二级申请 Sheet（走 POST /my/apply，CLOSE/CHANGE + entityId） */
const applySheetOpen = ref(false)
const applyEntityType = ref<'DISH' | 'STALL' | 'CANTEEN'>('DISH')
const applyAction = ref<'CLOSE' | 'CHANGE'>('CLOSE')
const applyEntityId = ref<number | ''>('')
const applyReason = ref('')
const applySubmitting = ref(false)

function openApplySheet() {
  contributeOpen.value = false
  applyEntityType.value = 'DISH'
  applyAction.value = 'CLOSE'
  applyEntityId.value = ''
  applyReason.value = ''
  applySheetOpen.value = true
}

async function submitEntityApply() {
  if (!userStore.requireAuth()) return
  const entityId = Number(applyEntityId.value)
  if (!entityId) {
    uni.showToast({ title: '请填写实体 ID', icon: 'none' })
    return
  }
  applySubmitting.value = true
  try {
    await submitApply({
      entityType: applyEntityType.value,
      applyType: applyAction.value,
      entityId,
      payload: { reason: applyReason.value.trim() },
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    applySheetOpen.value = false
    setTimeout(() => uni.navigateTo({ url: '/pages/profile/my-submissions' }), 500)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    applySubmitting.value = false
  }
}

/** 我的提交聚合页（task-12.1） */
function goToMySubmissions() {
  uni.navigateTo({ url: '/pages/profile/my-submissions' })
}

function goToNotify() {
  uni.navigateTo({ url: '/pages/notify/index' })
}

function goToFeedback() {
  uni.navigateTo({ url: '/pages/feedback/index' })
}

function goToSettings() {
  uni.navigateTo({ url: '/pages/settings/index' })
}

function goToContact() {
  uni.navigateTo({ url: '/pages/pages-detail/contact' })
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) userStore.logout()
    },
  })
}

onMounted(() => {
  if (userStore.isLoggedIn()) {
    userStore.fetchStats()
    notifyStore.fetchUnread()
  }
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + var(--spacing-lg) + env(safe-area-inset-bottom)); }
.auth-shell { min-height: calc(100vh - var(--tabbar-height)); padding: var(--spacing-md) var(--spacing-lg) var(--spacing-xl); box-sizing: border-box; }
.auth-hero { min-height: 330rpx; padding: var(--spacing-lg) var(--spacing-lg) calc(var(--spacing-xl) + var(--spacing-xl) + var(--spacing-md) + var(--spacing-xs)); border-radius: var(--radius-modal); background: var(--color-gradient); box-sizing: border-box; color: var(--text-white); }
.hero-badge { width: 96rpx; height: 96rpx; border-radius: var(--radius-modal); background: var(--text-white-faint); display: flex; align-items: center; justify-content: center; margin-bottom: var(--spacing-sm); border: 1rpx solid var(--text-white-edge); }
.hero-logo { font-size: 58rpx; line-height: 1; }
.hero-title { display: block; font-size: var(--font-h1); line-height: 1.15; font-weight: 800; color: var(--text-white); letter-spacing: -0.02em; }
.hero-subtitle { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-white-soft); }
.auth-panel { position: relative; margin: calc(-1 * (var(--spacing-lg) + var(--spacing-lg) + var(--spacing-md))) var(--spacing-sm) 0; padding: var(--spacing-lg) var(--spacing-lg) var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-modal); box-sizing: border-box; }
.form-head { margin-bottom: var(--spacing-md); }
.form-title { display: block; font-size: var(--font-h2); line-height: 1.25; font-weight: 760; color: var(--text-primary); }
.form-note { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); line-height: 1.5; color: var(--text-secondary); }
/* 内联错误提示（Apple §16：校验处附近内联，不弹裸 alert） */
.form-error { display: flex; align-items: center; gap: var(--spacing-xs); margin-bottom: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); -webkit-tap-highlight-color: transparent; }
.form-error-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.form-error-text { flex: 1; font-size: var(--font-aux); color: var(--color-error); font-weight: 600; }
/* 输入框分组卡片（Apple 风 inset group：浅底圆角，行间分隔线） */
.group-card { background: var(--bg-soft); border-radius: var(--radius-card); overflow: hidden; box-shadow: var(--shadow-card); }
.input-field { min-height: 92rpx; display: flex; align-items: center; gap: var(--spacing-md); padding: 0 var(--spacing-md); background: transparent; border-bottom: 2rpx solid var(--border-color); box-sizing: border-box; }
.input-field:last-child { border-bottom: none; }
.input-icon { font-size: 32rpx; line-height: 1; opacity: .52; flex-shrink: 0; }
.input-control { flex: 1; height: 90rpx; font-size: 28rpx; color: var(--text-primary); min-width: 0; }
.code-field { padding-right: 0; }
.code-action { min-width: 154rpx; height: 90rpx; padding: 0 var(--spacing-sm); display: flex; align-items: center; justify-content: center; border-left: 2rpx solid var(--border-color); color: var(--color-primary); font-size: 24rpx; font-weight: 650; white-space: nowrap; }
.code-action.disabled { color: var(--text-quaternary); }
.row-actions { display: flex; align-items: center; justify-content: space-between; margin: var(--spacing-md) var(--spacing-xs) 0; }
.link-text { font-size: var(--font-aux); color: var(--text-secondary); }
.primary-action { height: 92rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); display: flex; align-items: center; justify-content: center; box-shadow: var(--shadow-bar-primary); }
.primary-action { transition: transform 120ms var(--ease-out), opacity 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.primary-action:active { transform: scale(var(--press-scale)); opacity: .92; }
.primary-action.disabled { opacity: .58; }
.primary-action-text { color: var(--text-white); font-size: var(--font-card); font-weight: 720; }
.bottom-prompt { display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); }
.prompt-muted { font-size: var(--font-aux); color: var(--text-tertiary); }
.prompt-link { font-size: var(--font-aux); color: var(--color-accent); font-weight: 680; }
.user-header { margin: var(--spacing-md) var(--spacing-md) 0; }
.user-info-row { display: flex; align-items: center; padding: var(--spacing-md); gap: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.avatar-wrap { flex-shrink: 0; }
.avatar { width: 100rpx; height: 100rpx; border-radius: 50%; background: var(--bg-page); }
.avatar-empty { display: flex; align-items: center; justify-content: center; }
.avatar-fallback { font-size: 50rpx; line-height: 1; }
.user-meta { flex: 1; min-width: 0; }
.nickname-row { margin-bottom: var(--spacing-xs); }
.nickname { font-size: var(--font-subtitle); font-weight: 600; color: var(--text-primary); }
.user-id { font-size: var(--font-aux); color: var(--text-tertiary); }
.stats-row { display: flex; align-items: center; background: var(--bg-card); margin: var(--spacing-sm) var(--spacing-md) 0; padding: var(--spacing-md) 0; border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.stat-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: var(--spacing-xs); }
.stat-value { font-size: var(--font-h2); font-weight: 700; color: var(--text-primary); }
.stat-label { font-size: var(--font-aux); color: var(--text-tertiary); }
.stat-divider { width: 2rpx; height: 40rpx; background: var(--border-color); }
.menu-section { margin: var(--spacing-sm) var(--spacing-md); }
.menu-group { margin-bottom: var(--spacing-lg); }
.menu-group-title { display: block; padding: 0 var(--spacing-sm) var(--spacing-xs); font-size: var(--font-aux); font-weight: 600; color: var(--text-tertiary); letter-spacing: 0.02em; }
.menu-card { background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; }
.menu-item { display: flex; align-items: center; padding: var(--spacing-md) var(--spacing-lg); gap: var(--spacing-sm); border-bottom: 2rpx solid var(--border-color); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.menu-item:active { transform: scale(var(--press-scale)); }
.menu-item:last-child { border-bottom: none; }
.avatar-wrap, .nickname-row, .code-action, .link-text, .modal-btn { transition: transform 120ms var(--ease-out), opacity 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.avatar-wrap:active, .nickname-row:active, .code-action:active, .link-text:active, .modal-btn:active { transform: scale(var(--press-scale)); opacity: 0.92; }
.menu-icon { font-size: 36rpx; line-height: 1; flex-shrink: 0; opacity: 0.6; }
.menu-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.menu-arrow { font-size: 28rpx; line-height: 1; opacity: 0.3; flex-shrink: 0; }
.menu-badge { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--color-error); flex-shrink: 0; margin-right: var(--spacing-xs); }
.version-row { text-align: center; padding: var(--spacing-xl) var(--spacing-lg) var(--spacing-md); }
.version-text { display: block; font-size: 24rpx; font-weight: 600; color: var(--text-tertiary); }
.version-sub { display: block; font-size: 20rpx; color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.logout-wrap { padding: var(--spacing-md); }
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal-content { width: 560rpx; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); }
.modal-title { display: block; font-size: var(--font-card); font-weight: 600; color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
.modal-input { width: 100%; height: 80rpx; border: 2rpx solid var(--border-color); border-radius: var(--radius-card); padding: 0 var(--spacing-md); font-size: var(--font-body); box-sizing: border-box; }
.modal-actions { display: flex; justify-content: space-between; margin-top: var(--spacing-lg); gap: var(--spacing-sm); }
.modal-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-card); font-size: var(--font-body); font-weight: 500; }
.modal-btn-cancel { background: var(--bg-page); color: var(--text-secondary); }
.modal-btn-confirm { background: var(--color-primary); color: var(--text-white); }

/* 我要贡献入口卡（task-12.1） */
.contribute-card { display: flex; align-items: center; gap: var(--spacing-md); margin: var(--spacing-sm) var(--spacing-md) 0; padding: var(--spacing-md); background: var(--color-primary-soft); border-radius: var(--radius-card); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.contribute-card:active { transform: scale(var(--press-scale)); }
.contribute-icon { width: 80rpx; height: 80rpx; border-radius: 50%; background: var(--color-primary); display: flex; align-items: center; justify-content: center; font-size: 44rpx; line-height: 1; flex-shrink: 0; }
.contribute-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.contribute-title { font-size: var(--font-body); font-weight: 700; color: var(--text-primary); }
.contribute-sub { font-size: var(--font-aux); color: var(--text-secondary); }
.contribute-arrow { font-size: 28rpx; color: var(--text-tertiary); flex-shrink: 0; }

/* 底部 Sheet（spring 0.8/0.3） */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; }
.bottom-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 100; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.bottom-sheet.open { transform: translateY(0); }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); }
.sheet-option { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.sheet-option:active { background: var(--bg-soft); transform: scale(0.99); }
.sheet-option-icon { font-size: 44rpx; line-height: 1; flex-shrink: 0; }
.sheet-option-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.sheet-option-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.sheet-option-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-option-arrow { font-size: 28rpx; color: var(--text-tertiary); flex-shrink: 0; }
.form-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.form-label { display: block; font-size: var(--font-aux); font-weight: 700; color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.seg-row { display: flex; gap: var(--spacing-sm); flex-wrap: wrap; }
.seg { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.seg:active { transform: scale(0.97); }
.seg.on { background: var(--color-primary); color: var(--text-white); }
.form-input { width: 100%; height: 88rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.form-textarea { width: 100%; min-height: 160rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.sheet-submit { padding: var(--spacing-md) var(--spacing-lg); }

@media (prefers-reduced-motion: reduce) {
  .bottom-sheet { transition: opacity 0.2s ease; }
}
</style>
