import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserLocation } from '@/utils/location'

/**
 * 用户坐标会话缓存：首页首屏不再每次进入都请求定位/弹授权，
 * 仅在会话内首次缺失时异步补齐。坐标来源见 utils/location.ts。
 */
export const useLocationStore = defineStore('location', () => {
  const location = ref<UserLocation | null>(null)
  function setLocation(loc: UserLocation | null) {
    location.value = loc
  }
  return { location, setLocation }
})
