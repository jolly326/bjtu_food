<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '@/components/Modal.vue'
import { uploadImage } from '@/api/upload'
import { userApi } from '@/api'
import { usePageStore } from '@/stores/pageStore'
import { useToastStore } from '@/stores/toastStore'
import type { User } from '@/types'

const page = usePageStore()
const toast = useToastStore()
page.setPage({ breadcrumbs: [{ label: '账号设置' }] })

const currentUser = ref<User | null>(null)
const loading = ref(false)
const editing = ref(false)
const form = ref({ nickname: '' })
const formErrors = ref<Record<string, string>>({})
const fileInput = ref<HTMLInputElement>()

const showPwdModal = ref(false)
const pwdForm = ref({ oldPwd: '', newPwd: '', confirmPwd: '' })
const pwdError = ref('')

const avatarLetter = computed(() =>
  (currentUser.value?.nickname || currentUser.value?.username || 'A').charAt(0).toUpperCase()
)

const roleLabel = computed(() => currentUser.value?.role === 'admin' ? '管理员' : '普通用户')

const pwdStrength = computed(() => {
  const pwd = pwdForm.value.newPwd
  if (!pwd) return { label: '', color: '#ddd', percent: 0 }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  const levels = [
    { label: '弱', color: 'var(--color-error)' },
    { label: '中', color: 'var(--color-warning)' },
    { label: '强', color: '#4CAF50' },
    { label: '很好', color: 'var(--color-success)' },
  ]
  return { ...levels[Math.min(Math.max(score - 1, 0), 3)]!, percent: (score / 5) * 100 }
})

const pwdMatch = computed(() => {
  if (!pwdForm.value.confirmPwd) return null
  return pwdForm.value.newPwd === pwdForm.value.confirmPwd
})

async function loadProfile() {
  loading.value = true
  try {
    currentUser.value = await userApi.getProfile()
    if (currentUser.value?.username) localStorage.setItem('username', currentUser.value.username)
  } catch (err: any) {
    toast.error(err.message || '账号信息加载失败')
  } finally {
    loading.value = false
  }
}

function toggleEdit() {
  if (!currentUser.value) return
  editing.value = true
  form.value = { nickname: currentUser.value.nickname || '' }
  formErrors.value = {}
}

function cancelEdit() {
  editing.value = false
  formErrors.value = {}
}

async function confirmEdit() {
  const nickname = form.value.nickname.trim()
  if (!nickname) {
    formErrors.value = { nickname: '昵称不能为空' }
    return
  }
  try {
    currentUser.value = await userApi.updateProfile({ nickname })
    toast.success('账号信息已更新')
    editing.value = false
    formErrors.value = {}
  } catch (err: any) {
    toast.error(err.message || '账号信息更新失败')
  }
}

function triggerAvatarUpload() {
  fileInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    toast.error('图片大小不能超过 2MB')
    return
  }

  try {
    const result = await uploadImage(file)
    currentUser.value = await userApi.updateProfile({ avatar: result.relativeUrl })
    toast.success('头像已更新')
  } catch (err: any) {
    toast.error(err.message || '头像上传失败')
  }
}

function openPwdModal() {
  pwdForm.value = { oldPwd: '', newPwd: '', confirmPwd: '' }
  pwdError.value = ''
  showPwdModal.value = true
}

async function changePassword() {
  pwdError.value = ''
  if (!pwdForm.value.oldPwd) {
    pwdError.value = '请输入当前密码'
    return
  }
  if (pwdForm.value.newPwd.length < 6) {
    pwdError.value = '新密码至少 6 位'
    return
  }
  if (pwdForm.value.newPwd !== pwdForm.value.confirmPwd) {
    pwdError.value = '两次密码输入不一致'
    return
  }

  try {
    await userApi.updatePassword({ oldPassword: pwdForm.value.oldPwd, newPassword: pwdForm.value.newPwd })
    toast.success('密码已修改')
    showPwdModal.value = false
  } catch (err: any) {
    pwdError.value = err.message || '密码修改失败'
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="page">
    <div v-if="loading" class="card empty-card">正在加载账号信息...</div>
    <div v-else-if="!currentUser" class="card empty-card">账号信息加载失败，请重新登录后再试</div>

    <template v-else>
      <div class="card">
        <div class="card-hd">
          <h4 class="panel-title">账号信息</h4>
          <div class="panel-actions">
            <template v-if="!editing">
              <button class="btn-primary btn-sm" @click="toggleEdit">编辑</button>
            </template>
            <template v-else>
              <button class="btn-cancel btn-sm" @click="cancelEdit">取消</button>
              <button class="btn-primary btn-sm" @click="confirmEdit">保存</button>
            </template>
          </div>
        </div>

        <div class="detail-body">
          <div class="avatar-wrap" title="点击更换头像" @click="triggerAvatarUpload">
            <div v-if="currentUser.avatar" class="avatar-img">
              <img :src="currentUser.avatar" alt="" />
            </div>
            <div v-else class="avatar-letter">{{ avatarLetter }}</div>
            <div class="avatar-overlay">更换</div>
          </div>
          <input ref="fileInput" type="file" accept="image/*" hidden @change="onAvatarChange" />

          <div class="detail-fields">
            <div class="detail-row">
              <span class="detail-label">昵称</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ currentUser.nickname || currentUser.username }}</span>
                <input
                  v-else
                  v-model="form.nickname"
                  class="form-input"
                  :class="{ 'input-error': formErrors.nickname }"
                  placeholder="昵称"
                  maxlength="20"
                />
                <p v-if="editing && formErrors.nickname" class="field-error">{{ formErrors.nickname }}</p>
              </div>
            </div>

            <div class="detail-row">
              <span class="detail-label">用户名</span>
              <div class="detail-control">
                <span class="detail-value">{{ currentUser.username }}</span>
              </div>
            </div>

            <div class="detail-row">
              <span class="detail-label">角色</span>
              <div class="detail-control">
                <span class="role-tag">{{ roleLabel }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-hd">
          <h4 class="panel-title">安全设置</h4>
          <div class="panel-actions">
            <button class="btn-primary btn-sm" @click="openPwdModal">修改密码</button>
          </div>
        </div>
        <div class="detail-body">
          <div class="detail-fields no-indent">
            <div class="detail-row">
              <span class="detail-label">密码</span>
              <div class="detail-control">
                <span class="detail-value pwd-dots">••••••••</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <Modal :show="showPwdModal" title="修改密码" :width="420" @close="showPwdModal = false">
      <div class="pwd-field">
        <label>当前密码</label>
        <input v-model="pwdForm.oldPwd" type="password" placeholder="输入当前密码" @keyup.enter="changePassword" />
      </div>
      <div class="pwd-field">
        <label>新密码</label>
        <input v-model="pwdForm.newPwd" type="password" placeholder="至少 6 位" @keyup.enter="changePassword" />
        <div v-if="pwdForm.newPwd" class="pwd-strength">
          <div class="strength-bar">
            <div class="strength-fill" :style="{ width: pwdStrength.percent + '%', background: pwdStrength.color }"></div>
          </div>
          <span class="strength-label" :style="{ color: pwdStrength.color }">{{ pwdStrength.label }}</span>
        </div>
      </div>
      <div class="pwd-field">
        <label>确认新密码</label>
        <input v-model="pwdForm.confirmPwd" type="password" placeholder="再次输入新密码" @keyup.enter="changePassword" />
        <span v-if="pwdMatch !== null" class="pwd-match-hint" :class="{ ok: pwdMatch, err: !pwdMatch }">
          {{ pwdMatch ? '密码一致' : '密码不一致' }}
        </span>
      </div>
      <p v-if="pwdError" class="field-error">{{ pwdError }}</p>
      <div class="modal-actions">
        <button class="btn-cancel" @click="showPwdModal = false">取消</button>
        <button class="btn-primary" @click="changePassword">确认修改</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.page { max-width: 1400px; }
.card { margin-bottom: 20px; }
.empty-card { padding: 30px; color: var(--text-muted); text-align: center; }
.panel-title { margin: 0; font-size: 15px; color: var(--text-primary); font-weight: 600; }
.panel-actions { display: flex; gap: 8px; }
.detail-body { display: flex; gap: 24px; align-items: flex-start; padding: 24px 20px; }
.detail-fields { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.detail-fields.no-indent { padding-left: 0; }
.detail-row { display: flex; align-items: center; gap: 12px; }
.detail-label { font-size: 13px; color: var(--text-muted); width: 56px; flex-shrink: 0; line-height: 28px; }
.detail-control { flex: 1; min-width: 0; }
.detail-value { font-size: 15px; color: var(--text-primary); font-weight: 500; line-height: 28px; }
.pwd-dots { font-size: 18px; letter-spacing: 2px; }
.form-input { padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: var(--radius); font-size: 14px; font-weight: 500; color: var(--text-primary); outline: none; background: #fff; width: 100%; max-width: 300px; box-sizing: border-box; }
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.15); }
.input-error { border-color: var(--color-error) !important; }
.field-error { margin: 4px 0 0; font-size: 12px; color: var(--color-error); }
.avatar-wrap { position: relative; width: 80px; height: 80px; border-radius: 50%; cursor: pointer; overflow: hidden; flex-shrink: 0; box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.avatar-letter { width: 100%; height: 100%; border-radius: 50%; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 30px; font-weight: 700; }
.avatar-img { width: 100%; height: 100%; border-radius: 50%; overflow: hidden; }
.avatar-img img { width: 100%; height: 100%; object-fit: cover; }
.avatar-overlay { position: absolute; inset: 0; border-radius: 50%; background: rgba(0,0,0,.45); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 500; opacity: 0; transition: opacity .2s; }
.avatar-wrap:hover .avatar-overlay { opacity: 1; }
.role-tag { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 12px; font-weight: 500; background: var(--color-primary-bg); color: var(--color-primary); line-height: 20px; }
.pwd-field { margin-bottom: 18px; }
.pwd-field label { display: block; margin-bottom: 6px; font-size: 13px; color: var(--text-secondary); font-weight: 500; }
.pwd-field input { width: 100%; padding: 8px 12px; border: 1px solid var(--border-color); border-radius: var(--radius); font-size: 14px; outline: none; box-sizing: border-box; }
.pwd-field input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.12); }
.pwd-strength { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.strength-bar { flex: 1; height: 4px; background: #eee; border-radius: 4px; overflow: hidden; }
.strength-fill { height: 100%; border-radius: 4px; transition: width .3s, background .3s; }
.strength-label { font-size: 12px; font-weight: 500; width: 40px; text-align: right; }
.pwd-match-hint { font-size: 12px; margin-top: 4px; display: block; }
.pwd-match-hint.ok { color: var(--color-success); }
.pwd-match-hint.err { color: var(--color-error); }
.btn-cancel { padding: 6px 16px; border: 1px solid #d9d9d9; border-radius: var(--radius); background: #fff; color: var(--text-primary); font-size: 13px; cursor: pointer; }
.btn-cancel:hover { border-color: var(--color-primary); }
.btn-primary { padding: 6px 16px; border: none; border-radius: var(--radius); background: var(--color-primary); color: #fff; font-size: 13px; cursor: pointer; }
.btn-primary:hover { background: var(--color-primary-light); }
.btn-sm { padding: 4px 14px; font-size: 13px; }
</style>
