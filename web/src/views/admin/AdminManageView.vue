<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'

const router = useRouter()
const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '账号设置' }] })

const currentUser = computed(() => {
  const name = localStorage.getItem('username')
  return store.users.find(u => u.username === name) || store.users.find(u => u.role === 'super_admin' || u.role === 'admin')!
})

const editingNickname = ref(false)
const nicknameForm = ref('')

function startEditNick() {
  nicknameForm.value = currentUser.value?.nickname || ''
  editingNickname.value = true
}
function saveNickname() {
  const val = nicknameForm.value.trim()
  if (!val) { toast.error('昵称不能为空'); return }
  store.updateUserProfile(Number(currentUser.value!.id), { nickname: val })
  toast.success('昵称已更新')
  editingNickname.value = false
}
function cancelEditNick() { editingNickname.value = false }

const showPwdModal = ref(false)
const pwdForm = ref({ oldPwd: '', newPwd: '', confirmPwd: '' })
const pwdError = ref('')

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

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<template>
  <div class="page" v-if="currentUser">
    <div class="info-section">
      <div class="section-header">
        <h3>账号信息</h3>
        <div class="header-actions">
          <button class="btn-primary btn-sm" @click="openPwdModal">修改密码</button>
          <button class="btn-danger-outline btn-sm" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="info-main">
        <div class="profile-avatar">{{ (currentUser.nickname || currentUser.username).charAt(0).toUpperCase() }}</div>
        <div class="info-fields">
          <div class="field-row">
            <span class="field-label">昵称</span>
            <div class="field-control">
              <div v-if="!editingNickname" class="inline-group">
                <span class="field-value">{{ currentUser.nickname || currentUser.username }}</span>
                <button class="link" @click="startEditNick">修改</button>
              </div>
              <div v-else class="inline-group">
                <input v-model="nicknameForm" class="inline-input" placeholder="输入昵称" maxlength="20" @keyup.enter="saveNickname" />
                <button class="btn-primary btn-sm" @click="saveNickname">保存</button>
                <button class="btn-cancel btn-sm" @click="cancelEditNick">取消</button>
              </div>
            </div>
          </div>
          <div class="field-row">
            <span class="field-label">用户名</span>
            <div class="field-control"><span class="field-value">{{ currentUser.username }}</span></div>
          </div>
          <div class="field-row">
            <span class="field-label">角色</span>
            <div class="field-control"><span class="field-value">{{ currentUser.role === 'super_admin' ? '超级管理员' : '管理员' }}</span></div>
          </div>
          <div class="field-row">
            <span class="field-label">注册时间</span>
            <div class="field-control"><span class="field-value">{{ currentUser.created_at.toLocaleDateString('zh-CN') }}</span></div>
          </div>
        </div>
      </div>
    </div>

    <Modal :show="showPwdModal" title="修改密码" :width="420" @close="showPwdModal = false">
      <div class="field"><label>当前密码</label><input v-model="pwdForm.oldPwd" type="password" placeholder="输入当前密码" @keyup.enter="changePassword" /></div>
      <div class="field"><label>新密码</label><input v-model="pwdForm.newPwd" type="password" placeholder="至少 6 位" @keyup.enter="changePassword" /></div>
      <div class="field"><label>确认新密码</label><input v-model="pwdForm.confirmPwd" type="password" placeholder="再次输入新密码" @keyup.enter="changePassword" /></div>
      <p v-if="pwdError" class="field-error">{{ pwdError }}</p>
      <div class="modal-actions">
        <button class="btn-cancel" @click="showPwdModal = false">取消</button>
        <button class="btn-primary" @click="changePassword">确认修改</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.info-section {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  margin-bottom: 24px;
  overflow: hidden;
}
.info-section .section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color);
}
.section-header h3 { margin: 0; font-size: 16px; color: var(--text-primary); font-weight: 600; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.info-main {
  display: flex;
  gap: 24px;
  padding: 24px;
}
.profile-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}
.info-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}
.field-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.field-label {
  font-size: 13px;
  color: var(--text-muted);
  width: 64px;
  flex-shrink: 0;
}
.field-control { flex: 1; min-width: 0; }
.field-value {
  font-size: 15px;
  color: var(--text-primary);
  font-weight: 500;
}
.inline-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.inline-input {
  padding: 5px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: 14px;
  outline: none;
  width: 180px;
  box-sizing: border-box;
}
.inline-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(139,58,43,.15);
}
.link {
  background: none;
  border: none;
  color: var(--primary, #1890ff);
  cursor: pointer;
  font-size: 13px;
  padding: 2px 0;
}
.link:hover { opacity: .8; }
.btn-danger-outline {
  padding: 4px 14px;
  border: 1px solid var(--color-error);
  border-radius: 6px;
  background: #fff;
  color: var(--color-error);
  font-size: 13px;
  cursor: pointer;
}
.btn-danger-outline:hover {
  background: var(--color-error);
  color: #fff;
}
</style>
