// 主题->颜色 token 映射（唯一事实源）。
// WXSS 不接受 var() 的原生 API 常量兜底色登记于此；其余一律走 CSS 变量 var(--xxx)。
//
// ===== 2026-09-23 全站色板改「暖橙黄」（§7.39 裁决，真源 = docs/ui/client-首页菜品浏览.md §4.1）=====
// 旧「暖砖红橙档」（primary #C2410C / primary-text #B93A0A / primary-bright #EA580C /
// 页底 #F7F3EF / 渐变 #FFF9F3→#FFEFE0 / 文字 #262626 三阶）**整体退役**。
// 主色由「单档」细分为语义分档（填充 / 文字 / 图形 / 浅底 / 点击态），并补齐 §4.1 的全部 token 名
// （`--color-primary-fill` / `--card-bg` / `--text-title` / `--text-body` / `--bg-soft-yellow` …），
// 使页面文档可直接引用。
export const COLOR_MAP = {
  /* ===== 主色（暖橙黄）=====
     填充 / 文字档 #B4531A（「白字安全橙」）：白字 on 它 5.01:1 ✅；作 #FFF8EF 上的文字 4.75:1 ✅。
     ⚠️ 取色边界（§4.1）：`--color-primary-orange` #E67E22 白字仅 2.85:1、作文字亦 2.85:1，
        **不得**作填充底或正文色；`--color-orange-deep` #D35400 白字 3.88:1，仅可作点击态 / 图形。 */
  primary: '#B4531A',
  'primary-fill': '#B4531A',
  /* 主色「文字档」= 填充档同值（浅底上的主色文字与价格；两档同色、语义不同） */
  'primary-text': '#B4531A',
  /* 主色「图形档亮橙」：仅用于纯图形（标签下划线、TabBar 激活图标等），不作文字 / 填充底 */
  'primary-bright': '#F5A623',
  'primary-amber': '#F5A623',
  /* 主色「橙档」：关键图标 / 重点操作（**不得**作填充底白字 / 正文色，见上取色边界） */
  'primary-orange': '#E67E22',
  /* 轻量高亮 / 推荐角标 / 装饰（**只作浅底**） */
  'primary-yellow': '#FFD166',
  /* 主色压暗：按钮点击态、图形级强调 */
  'orange-deep': '#D35400',
  /* 标题深棕（暖橙黄主题下替代纯黑的点缀色） */
  'brown-deep': '#7B4F2A',
  'on-primary': '#FFFFFF',
  /* 主色浅底（标签 / chip 底）；与 `--bg-soft-yellow` 同值、同源 */
  'primary-soft': '#FFF3D6',
  accent: '#B4531A',
  'accent-soft': '#FAE0CE',
  /* ===== 功能色（§4.1）===== */
  error: '#C62828',
  'error-soft': '#FFECEB',
  warning: '#E67E22',
  'warning-soft': '#FFF8E1',
  success: '#2E7D32',
  info: '#1565C0',
  /* 价格色 = 主色「文字档」同值（不另立第三主色；浅底可读性由文字档保证） */
  price: '#B4531A',
  /* 评分星级（独立语义色，**SHALL NOT 随主色换肤**；与 Web 管理端既有 --color-star 对齐）。
     未选 / 空星见 --color-star-empty。 */
  star: '#FBBF24',
  'star-empty': '#E5E5EA',
  'text-white': '#FFFFFF',
  'text-white-edge': 'rgba(255,255,255,0.24)',
  /* 详情页返回钮黑箭头（微信原生胶囊同款配色；WXSS 可直接消费 var(--nav-back-icon)） */
  'nav-back-icon': '#1A1A1A',
  /* ===== 文字四档（§4.1；全部 ≥4.5:1，达标）=====
     title 15.1:1 / body 10.9:1 / subtitle 4.86:1；placeholder 2.39:1 仅作「输入框占位」（从宽）。
     ⚠️ 旧三阶（#262626 / #595959 / #999999）中 `--text-tertiary` #999999 对白仅 **2.85:1**，
        **不达** WCAG AA 4.5:1 —— 本次替换同时修掉该可访问性缺陷。 */
  'text-primary': '#2D1F14',
  'text-title': '#2D1F14',
  'text-body': '#4A3520',
  'text-secondary': '#4A3520',
  'text-subtitle': '#7F6A55',
  'text-tertiary': '#7F6A55',
  'text-placeholder': '#B5A594',
  /* ===== 背景（§4.1）===== */
  'bg-page': '#FFF8EF',
  /* 页面顶部渐变（真源）：顶部 `--bg-soft-orange`（淡橙）→ 页底 `--bg-page`。
     ⚠️ 该渐变是**首页吸顶容器背景切片的唯一真源**（§7.38），改值后容器表面自动跟随、无需另改。 */
  'bg-page-grad-from': '#FFE8D1',
  'bg-page-grad-to': '#FFF8EF',
  'bg-soft-orange': '#FFE8D1',
  'bg-soft-yellow': '#FFF3D6',
  /* feedback-forms-ux-polish：意见反馈页奶油米白底（Q 版暖调表面） */
  'bg-warm': '#FAF6F0',
  'bg-card': '#FFFFFF',
  'card-bg': '#FFFFFF',
  'bg-input': '#F7F5F2',
  'bg-soft': '#EDE9E5',
  'bg-placeholder': '#F0ECE8',
  /* 白卡底部渐隐端色（home-ui-refresh D9：首页筛选面板食堂列表溢出提示）：
     白色 0 透明度端，必须与 --bg-card 配对使用，避免渐隐端出现灰边；端色值集中登记于此，样式侧只引 var() */
  'grad-fade-white': 'rgba(255,255,255,0)',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',
  'overlay-dark-strong': 'rgba(0,0,0,0.6)',
  'overlay-dark-soft': 'rgba(0,0,0,0.15)',
  'overlay-scrim': 'rgba(0,0,0,0.4)',
  'shadow-card': '0 2px 12px rgba(0,0,0,0.04)',
  /* feedback-forms-ux-polish：Q 版暖调柔和投影（卡片暖调分层；中性阴影仍走 shadow-card） */
  'shadow-warm': '0 4rpx 12rpx rgba(180, 140, 120, 0.08)',
  'shadow-modal': '0 18rpx 54rpx rgba(0,0,0,0.18)',
  /* 详情页返回钮胶囊（微信右上角原生胶囊同款：中性浅灰透底 + 细边；原散落 App.vue，UI-03 收口） */
  'bg-nav-back-chip': 'rgba(0, 0, 0, 0.08)',
  'border-nav-back-chip': 'rgba(0, 0, 0, 0.1)',
  /* 底栏/浮层阴影族（原散落 App.vue，UI-03 收口入唯一真源） */
  'shadow-bar': '0 -4rpx 20rpx rgba(56, 42, 34, 0.08)',
  'shadow-bar-soft': '0 -4rpx 12rpx rgba(0, 0, 0, 0.06)',
  /* 主色通道随新主色 #B4531A（RGB 180, 83, 26）同步（§4.1 / §7.39） */
  'shadow-bar-primary': '0 12rpx 28rpx rgba(180, 83, 26, 0.28)',
  'shadow-float': '0 6rpx 16rpx rgba(0, 0, 0, 0.12)',
  /* 长条删除按钮（图片移除）暗底（原散落 App.vue，UI-03 收口） */
  'badge-dark-bg': 'rgba(0, 0, 0, 0.5)',
} as const

export type IconColorName = keyof typeof COLOR_MAP

/**
 * CSS 变量注册表（UI-03 色值唯一真源，spec §4.2）。
 *
 * 键 = 全局 CSS 变量名，值**一律引用上方 COLOR_MAP 同语义键**（同键同值，不重复写字面量），
 * 由 `scripts/gen-css-vars.ts` 遍历生成 `src/theme/generated-colors.css` 的 `page{…}` 颜色块，
 * App.vue 仅 `@import` 该生成物，禁止手工维护颜色变量。
 *
 * ⚠️ **2026-09-23 对齐现状**：生成脚本 `scripts/gen-css-vars.ts` 与 `npm run gen:tokens` 已删除，
 * `generated-colors.css` 改为**手工维护**（内容仍须与本表保持一致）；`npm run type-check` 的前置
 * 绑定随之失效，须直接执行 `npx vue-tsc --noEmit`（见 spec §4.2 / §7.39）。
 *
 * 收录口径：仅收录「以 CSS 变量形态被 WXSS 消费」的色值 token；
 * 仅原生 API 兜底直取实色的键（如 success 历史键已删）不在页面级声明，不收录。
 * 派生引用（--text-hint 等值为 var() 组合、不含裸色值）不属色值真源范畴，仍手写在 App.vue 派生区。
 */
export const CSS_VARS: Record<string, string> = {
  /* 品牌主色（填充档 / 文字档 / 图形档 / 橙档 / 浅底档） */
  '--color-primary': COLOR_MAP.primary,
  '--color-primary-fill': COLOR_MAP['primary-fill'],
  '--color-primary-text': COLOR_MAP['primary-text'],
  '--color-primary-bright': COLOR_MAP['primary-bright'],
  '--color-primary-amber': COLOR_MAP['primary-amber'],
  '--color-primary-orange': COLOR_MAP['primary-orange'],
  '--color-primary-yellow': COLOR_MAP['primary-yellow'],
  '--color-orange-deep': COLOR_MAP['orange-deep'],
  '--color-brown-deep': COLOR_MAP['brown-deep'],
  '--color-on-primary': COLOR_MAP['on-primary'],
  '--color-primary-soft': COLOR_MAP['primary-soft'],
  /* 强调色（热卖/热搜/新品） */
  '--color-accent': COLOR_MAP.accent,
  '--color-accent-soft': COLOR_MAP['accent-soft'],
  /* 语义色（error/warning/success/info/price/star 深浅对称）。
     原 `--color-like` 已随「评价有用」全链下线删除（2026-09-21 资产专项，PR-05） */
  '--color-error': COLOR_MAP.error,
  '--color-error-soft': COLOR_MAP['error-soft'],
  '--color-warning': COLOR_MAP.warning,
  '--color-warning-soft': COLOR_MAP['warning-soft'],
  '--color-success': COLOR_MAP.success,
  '--color-info': COLOR_MAP.info,
  '--color-price': COLOR_MAP.price,
  /* 评分星级（黄色实心星；未选/空星见 --color-star-empty） */
  '--color-star': COLOR_MAP.star,
  '--color-star-empty': COLOR_MAP['star-empty'],
  /* 文字四档（--text-hint 派生引用见 App.vue） */
  '--text-white': COLOR_MAP['text-white'],
  '--text-primary': COLOR_MAP['text-primary'],
  '--text-title': COLOR_MAP['text-title'],
  '--text-body': COLOR_MAP['text-body'],
  '--text-secondary': COLOR_MAP['text-secondary'],
  '--text-subtitle': COLOR_MAP['text-subtitle'],
  '--text-tertiary': COLOR_MAP['text-tertiary'],
  '--text-placeholder': COLOR_MAP['text-placeholder'],
  /* 背景 / 边框 */
  '--bg-page': COLOR_MAP['bg-page'],
  '--bg-page-grad-from': COLOR_MAP['bg-page-grad-from'],
  '--bg-page-grad-to': COLOR_MAP['bg-page-grad-to'],
  '--bg-soft-orange': COLOR_MAP['bg-soft-orange'],
  '--bg-soft-yellow': COLOR_MAP['bg-soft-yellow'],
  '--bg-warm': COLOR_MAP['bg-warm'],
  '--bg-card': COLOR_MAP['bg-card'],
  '--card-bg': COLOR_MAP['card-bg'],
  '--bg-input': COLOR_MAP['bg-input'],
  '--bg-soft': COLOR_MAP['bg-soft'],
  '--bg-placeholder': COLOR_MAP['bg-placeholder'],
  '--grad-fade-white': COLOR_MAP['grad-fade-white'],
  '--border-color': COLOR_MAP['border-color'],
  '--border-bold': COLOR_MAP['border-bold'],
  /* 阴影（卡片 / 底栏 / 浮层） */
  '--shadow-card': COLOR_MAP['shadow-card'],
  '--shadow-warm': COLOR_MAP['shadow-warm'],
  '--shadow-modal': COLOR_MAP['shadow-modal'],
  '--shadow-bar': COLOR_MAP['shadow-bar'],
  '--shadow-bar-soft': COLOR_MAP['shadow-bar-soft'],
  '--shadow-bar-primary': COLOR_MAP['shadow-bar-primary'],
  '--shadow-float': COLOR_MAP['shadow-float'],
  /* 暗化遮罩 */
  '--text-white-edge': COLOR_MAP['text-white-edge'],
  '--overlay-dark-strong': COLOR_MAP['overlay-dark-strong'],
  '--overlay-dark-soft': COLOR_MAP['overlay-dark-soft'],
  '--overlay-scrim': COLOR_MAP['overlay-scrim'],
  /* 详情页返回钮胶囊 */
  '--bg-nav-back-chip': COLOR_MAP['bg-nav-back-chip'],
  '--border-nav-back-chip': COLOR_MAP['border-nav-back-chip'],
  '--nav-back-icon': COLOR_MAP['nav-back-icon'],
  /* 长条删除按钮暗底 */
  '--badge-dark-bg': COLOR_MAP['badge-dark-bg'],
}

// ========== 原生属性例外登记（uni-app 限制） ==========
// 微信原生 <swiper> 的 indicator-active-color / indicator-color 不接受 var()，
// 必须用真实色值（见 pages/detail/dish/ImageSwiper.vue）。删除 uni.scss 后，原例外说明迁此。
export const SWIPER_INDICATOR_ACTIVE_COLOR = '#ffffff'
export const SWIPER_INDICATOR_COLOR = 'rgba(255,255,255,0.4)'
// uni.showModal 的 confirmColor 不接受 var()，必须用真实色值（危险操作确认按钮，与 --color-error 同值）
// （见 pages/detail/dish/useDishPage.ts、pages/my-reviews/index.vue、pages/find/index.vue）
export const MODAL_CONFIRM_DANGER_COLOR = '#C62828'
// uni.showModal 的 confirmColor 不接受 var()，必须用真实色值（重要操作确认按钮）
// 取「文字档」--color-primary-text 的字面量（§4.1 起两档同值 #B4531A）
// （见 pages/mine/index.vue 注销账号确认弹窗）
export const MODAL_CONFIRM_PRIMARY_COLOR = '#B4531A'
// pages.json globalStyle 导航栏/窗口底色（JSON 无法引用 TS 常量，此处登记为色值事实源，改动须与 pages.json 同步）
export const NAVIGATION_BAR_BACKGROUND = '#F5F5F7'
// IconSvg 描边唯一兜底/兜底色（MP-11：resolveColor/COLOR_MAP 死机制删除后，var() 形态与空值统一落到本常量；见 components/IconSvg.vue）
export const ICON_FALLBACK_COLOR = '#1C1C1E'
