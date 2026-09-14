import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 已下线（2026-09-14）：管理员账号 store。
 *
 * 原实现在 store 初始化时就发起 /admin/admins、/admin/admins/me 请求，
 * 而这两个接口在「管理端去登录与角色体系」（2026-09-13 定型）后已不存在，
 * 导致打开后台即产生 404 请求。现改为纯占位：不再发起任何请求。
 *
 * 保留 myRole 仅供布局层读取，待页面收口后随本文件一并物理删除。
 */
export const useAdminUserStore = defineStore('adminUser', () => {
  const list = ref<never[]>([])
  const myRole = ref<string>('admin')

  return { list, myRole }
})
