/**
 * 暂缓开放功能集中登记表（F1：2026-09-07）。
 *
 * 背景：mine 宫格「最新活动」「我发布的」入口自 2026-09-07 起暂缓开放，
 * 但对应页面（pages/activity/*、pages/me/publish-mine）与 pages.json/routes.ts 注册保留，
 * 仅无导航可达。集中登记避免后续审计把「暂缓页」误判为孤儿路由；
 * 恢复开放时只改本文件（open: true），跳转仍走 @/utils/routes 常量。
 *
 * 语义：open=false 时入口点击仅 toast（title 提示），不导航；
 *       open=true 时入口正常导航至对应路由。审计 grep「无引用页面」时按本表区分「暂缓」与「孤儿」。
 */
import { PATH } from './routes'

export const FEATURE_GATES = {
  /** 活动中心：pages/activity/index + pages/activity/webview（首页万能区/我的宫格入口，暂缓开放） */
  activity: { open: false, toast: '功能暂未实现' },
  /** 我发布的：pages/me/publish-mine（我的宫格入口，暂缓开放） */
  publishMine: { open: false, toast: '功能暂未实现' },
} as const

/** gate key → 恢复开放时的目标路由（供调用方在 open=true 时导航；集中一处便于联动核对） */
export const GATE_TARGET_URL: Record<keyof typeof FEATURE_GATES, string> = {
  activity: PATH.activity,
  publishMine: PATH.publishMine,
}

/** 读取 gate 的开放状态（open ? 返回跳转 url 供导航 : 返回 toast 提示） */
export function resolveGate(gateKey: keyof typeof FEATURE_GATES):
  | { open: true; url: string }
  | { open: false; toast: string } {
  const gate = FEATURE_GATES[gateKey]
  return gate.open
    ? { open: true, url: GATE_TARGET_URL[gateKey] }
    : { open: false, toast: gate.toast }
}
