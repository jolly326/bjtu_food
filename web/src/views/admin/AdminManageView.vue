<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import { uploadImage } from '@/api/upload'

const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '账号设置' }] })

const currentUser = computed(() => {
  const name = localStorage.getItem('username')
  return store.users.find(u => u.username === name) || store.users.find(u => u.role === 'super_admin' || u.role === 'admin')!
})

// ============ 编辑模式 ============
const editing = ref(false)
const form = ref({ nickname: '', username: '' })
const formErrors = ref<Record<string, string>>({})

function toggleEdit() {
  if (!currentUser.value) return
  editing.value = true
  form.value = { nickname: currentUser.value.nickname || '', username: currentUser.value.username }
  formErrors.value = {}
}

function confirmEdit() {
  const errs: Record<string, string> = {}
  if (!form.value.nickname.trim()) errs.nickname = '昵称不能为空'
  if (!form.value.username.trim()) errs.username = '用户名不能为空'
  else if (store.users.some(u => u.username === form.value.username.trim() && Number(u.id) !== Number(currentUser.value!.id))) {
    errs.username = '用户名已被占用'
  }
  formErrors.value = errs
  if (Object.keys(errs).length) return

  const id = Number(currentUser.value!.id)
  if (form.value.nickname !== (currentUser.value!.nickname || '')) {
    store.updateUserProfile(id, { nickname: form.value.nickname.trim() })
  }
  if (form.value.username !== currentUser.value!.username) {
    store.updateUserProfile(id, { username: form.value.username.trim() })
    localStorage.setItem('username', form.value.username.trim())
  }
  toast.success('账号信息已更新')
  formErrors.value = {}
  editing.value = false
}

function cancelEdit() {
  formErrors.value = {}
  editing.value = false
}

// ============ 头像 ============
const fileInput = ref<HTMLInputElement>()
function triggerAvatarUpload() { fileInput.value?.click() }
async function onAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  if (file.size > 2 * 1024 * 1024) { toast.error('图片大小不能超过 2MB'); return }
  try {
    const result = await uploadImage(file)
    store.updateUserProfile(Number(currentUser.value!.id), { avatar: result.relativeUrl })
    toast.success('头像已更新')
  } catch (err: any) {
    toast.error(err.message || '头像上传失败')
  }
}

// ============ 修改密码 ============
const showPwdModal = ref(false)
const pwdForm = ref({ oldPwd: '', newPwd: '', confirmPwd: '' })
const pwdError = ref('')

const pwdStrength = computed(() => {
  const pwd = pwdForm.value.newPwd
  if (!pwd) return { level: 0, label: '', color: '', percent: 0 }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  const map = [
    { level: 1, label: '弱', color: 'var(--color-error)' },
    { level: 2, label: '中', color: 'var(--color-warning)' },
    { level: 3, label: '强', color: '#4CAF50' },
    { level: 4, label: '非常强', color: 'var(--color-success)' },
  ]
  const idx = Math.min(Math.max(score - 1, 0), 3)
  return { ...map[idx]!, percent: (score / 5) * 100 }
})

const pwdMatch = computed(() => {
  if (!pwdForm.value.confirmPwd) return null
  return pwdForm.value.newPwd === pwdForm.value.confirmPwd
})

function openPwdModal() {
  pwdForm.value = { oldPwd: '', newPwd: '', confirmPwd: '' }
  pwdError.value = ''
  showPwdModal.value = true
}
function changePassword() {
  pwdError.value = ''
  if (pwdForm.value.oldPwd !== currentUser.value?.password) { pwdError.value = '当前密码不正确'; return }
  if (pwdForm.value.newPwd.length < 6) { pwdError.value = '新密码至少 6 位'; return }
  if (pwdForm.value.newPwd !== pwdForm.value.confirmPwd) { pwdError.value = '两次密码输入不一致'; return }
  store.updateUserProfile(Number(currentUser.value!.id), { password: pwdForm.value.newPwd })
  toast.success('密码已修改')
  showPwdModal.value = false
}

// ============ 计算属性 ============
const avatarLetter = computed(() =>
  (currentUser.value?.nickname || currentUser.value?.username || 'A').charAt(0).toUpperCase()
)
const roleLabel = computed(() =>
  currentUser.value?.role === 'super_admin' ? '超级管理员' : '管理员'
)
</script>

<template>
  <div class="page" v-if="currentUser">
    <!-- 账号信息 -->
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
        <div class="avatar-wrap" @click="triggerAvatarUpload" title="点击更换头像">
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
              <input v-else v-model="form.nickname" class="form-input" :class="{ 'input-error': formErrors.nickname }" placeholder="昵称" maxlength="20" />
              <p v-if="editing && formErrors.nickname" class="field-error">{{ formErrors.nickname }}</p>
            </div>
          </div>
          <div class="detail-row">
            <span class="detail-label">用户名</span>
            <div class="detail-control">
              <span v-if="!editing" class="detail-value">{{ currentUser.username }}</span>
              <input v-else v-model="form.username" class="form-input" :class="{ 'input-error': formErrors.username }" placeholder="用户名" maxlength="20" />
              <p v-if="editing && formErrors.username" class="field-error">{{ formErrors.username }}</p>
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

    <!-- 安全设置 -->
    <div class="card">
      <div class="card-hd">
        <h4 class="panel-title">安全设置</h4>
        <div class="panel-actions">
          <button class="btn-primary btn-sm" @click="openPwdModal">修改密码</button>
        </div>
      </div>
      <div class="detail-body">
        <div class="detail-fields" style="padding-left:0">
          <div class="detail-row">
            <span class="detail-label">密码</span>
            <div class="detail-control">
              <span class="detail-value pwd-dots">●●●●●●●●</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
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
          {{ pwdMatch ? '✓ 密码一致' : '✗ 密码不一致' }}
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
.page {
  max-width: 1400px;
}

/* ===== Panel Section（同食堂详情页） ===== */
.panel-title {
  margin: 0;
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 600;
}
.panel-actions {
  display: flex;
  gap: 8px;
}

/* ===== 详情体（同食堂详情页） ===== */
.detail-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  padding: 24px 20px;
}
.card { margin-bottom: 20px; }
.detail-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-label {
  font-size: 13px;
  color: var(--text-muted);
  width: 48px;
  flex-shrink: 0;
  line-height: 28px;
}
.detail-control {
  flex: 1;
  min-width: 0;
}
.detail-value {
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 500;
  line-height: 28px;
}
.pwd-dots {
  font-size: 18px;
  letter-spacing: 2px;
}

.form-input {
  padding: 5px 10px;
  border: 1px solid #d9d9d9;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  outline: none;
  transition: border-color .2s;
  background: #fff;
  width: 100%;
  max-width: 300px;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(139,58,43,.15);
}
.input-error {
  border-color: var(--color-error) !important;
}
.field-error {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-error);
}

/* ===== 头像 ===== */
.avatar-wrap {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0,0,0,.08);
}
.avatar-letter {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 700;
}
.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  overflow: hidden;
}
.avatar-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0,0,0,.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 500;
  opacity: 0;
  transition: opacity .2s;
}
.avatar-wrap:hover .avatar-overlay {
  opacity: 1;
}

.role-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  line-height: 20px;
}

/* ===== 密码弹窗 ===== */
.pwd-field {
  margin-bottom: 18px;
}
.pwd-field label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}
.pwd-field input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  transition: border-color .2s;
}
.pwd-field input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(139,58,43,.12);
}

.pwd-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.strength-bar {
  flex: 1;
  height: 4px;
  background: #eee;
  border-radius: 4px;
  overflow: hidden;
}
.strength-fill {
  height: 100%;
  border-radius: 4px;
  transition: width .3s, background .3s;
}
.strength-label {
  font-size: 12px;
  font-weight: 500;
  width: 40px;
  text-align: right;
}
.pwd-match-hint {
  font-size: 12px;
  margin-top: 4px;
  display: block;
}
.pwd-match-hint.ok { color: var(--color-success); }
.pwd-match-hint.err { color: var(--color-error); }

/* ===== 全局按钮覆盖 ===== */
.btn-cancel {
  padding: 6px 16px;
  border: 1px solid #d9d9d9;
  border-radius: var(--radius);
  background: #fff;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all .2s;
}
.btn-cancel:hover {
  border-color: var(--color-primary);
}
.btn-primary {
  padding: 6px 16px;
  border: none;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: background .2s;
}
.btn-primary:hover {
  background: var(--color-primary-light);
}
.btn-sm {
  padding: 4px 14px;
  font-size: 13px;
}
</style>
