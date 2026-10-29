/**
 * 定时器统一管理 Hook。
 * 集中创建与清理所有 setTimeout，页面/组件卸载时自动清空，避免定时器泄漏。
 */

import { onUnmounted } from 'vue';

export function useTimers() {
  const timers = new Set<ReturnType<typeof setTimeout>>();

  function clearTimer(timer: ReturnType<typeof setTimeout> | null): null {
    if (timer) {
      clearTimeout(timer);
      timers.delete(timer);
    }
    return null;
  }

  function setTimer(
    callback: () => void,
    delay: number,
  ): ReturnType<typeof setTimeout> {
    const timer = setTimeout(() => {
      timers.delete(timer);
      callback();
    }, delay);
    timers.add(timer);
    return timer;
  }

  function clearAll(): void {
    timers.forEach((t) => clearTimeout(t));
    timers.clear();
  }

  onUnmounted(() => {
    clearAll();
  });

  return {
    setTimer,
    clearTimer,
    clearAll,
  };
}
