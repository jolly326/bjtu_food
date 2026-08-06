import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { AdminUser } from '@/types'
import { adminApi } from '@/api'

export const useAdminUserStore = defineStore('adminUser', () => {
  const list = ref<AdminUser[]>([])
  const myRole = ref<string>('admin')

  async function loadAll() { list.value = await adminApi.getAll() }
  async function loadMyRole() { try { myRole.value = await adminApi.getMyRole() } catch { myRole.value = 'admin' } }
  async function add(data: { username: string; password: string; nickname?: string }) { await adminApi.create(data); await loadAll() }
  async function update(id: number, data: { nickname?: string; password?: string }) { await adminApi.updateById(id, data); await loadAll() }
  async function setStatus(id: number, status: 'active' | 'disabled') { await adminApi.setStatus(id, status); await loadAll() }
  async function remove(id: number) { await adminApi.deleteById(id); await loadAll() }

  loadAll()
  loadMyRole()
  return { list, myRole, add, update, setStatus, remove, loadAll, loadMyRole }
})
