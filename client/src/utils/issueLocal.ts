/**
 * 纠错提交的本地去重记忆（端上软提示的依据）。
 *
 * 后端允许同一用户对同一菜品同一选项重复提交（不做去重幂等，口径见
 * docs/func/client/B-轻量反馈/B2-菜品纠错.md）；端上在提交前查本地记录，
 * 已提交过的组合直接提示并阻断，避免重复提交把运营待办量打虚。
 * 同一设备内有效：换设备 / 清缓存后记录消失，属已知取舍。
 */
import { STORAGE } from '@/constants'

const STORAGE_KEY = STORAGE.ISSUE_SUBMITTED

function readSet(): Set<string> {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    return new Set(Array.isArray(raw) ? raw.map(String) : [])
  } catch {
    return new Set()
  }
}

function key(dishId: number, optionId: number): string {
  return `${dishId}:${optionId}`
}

export function hasSubmitted(dishId: number, optionId: number): boolean {
  return readSet().has(key(dishId, optionId))
}

export function markSubmitted(dishId: number, optionId: number): void {
  const set = readSet()
  set.add(key(dishId, optionId))
  uni.setStorageSync(STORAGE_KEY, [...set])
}
