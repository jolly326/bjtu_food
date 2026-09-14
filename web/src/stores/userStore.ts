import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '@/types'
import { userApi } from '@/api'

/**
 * 管理端「学生用户」数据源（列表 + 状态切换）。
 * 按 project_spec.md §7.10 A：管理端为单口令单人后台，不再维护操作人身份
 * （无 adminId / role 缓存、无 profile 回源、无本地登录态清理），故本 store 只管数据。
 */
export const useUserStore = defineStore('user', () => {
  const list = ref<User[]>([])

  async function loadAll() {
    list.value = await userApi.getAll()
  }

  async function toggleUserStatus(id: number, targetStatus: 'active' | 'disabled') {
    await userApi.toggleUserStatusById(id, targetStatus)
    await loadAll()
  }

  // 注意：不再在 setup 顶层自动调用 loadAll()，避免未登录（无 token）时触发 getAll() 请求
  // 导致 http 拦截层 401 清 token 跳登录的副作用。list 由实际需要的页面显式调用 loadAll() 加载。
  return { list, loadAll, toggleUserStatus }
})
