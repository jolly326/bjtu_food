// 主题->颜色 token 映射（唯一事实源）。
// WXSS 不接受 var() 的原生 API 常量兜底色登记于此；其余一律走 CSS 变量 var(--xxx)。
export const COLOR_MAP = {
  /* 主色橙 —— 两档语义（2026-09-21 §7.34 定稿；对比度于 2026-09-21 C1 复核重算，只订正注释、不改取值）：
     · primary（填充/图形档）#C2410C：品牌填充底 + 图标图形 + 白字承载，全站主力用途
       实测（WCAG 2.x 相对亮度）：其上白字 5.18:1（正文级 ≥4.5:1 ✅）、对白卡 5.18:1、
       对页面底色 #F7F3EF 4.69:1（图形级 ≥3:1 ✅）；
       ⚠️ 但对**主色浅底** #FCE8D6 仅 4.35:1（< 4.5:1 ✗）——故浅底上的主色文字一律走文字档。
     · primary-text（文字档）#B93A0A：主色浅底 / 白卡上的主色文字与价格
       实测：对白卡 5.72:1、对页面底色 5.18:1、对主色浅底 #FCE8D6 4.81:1（正文级 ≥4.5:1 ✅）
     为何不取更亮的橙（如 #EA580C）：它作填充底时白字仅 3.56:1、作文字仅 3.56:1，**两个门槛都不达**；
     而全站 21 处以 primary 作「填充底 + 白字」（按钮 / 胶囊 / 徽标），故 primary 必须取填充安全档。 */
  primary: '#C2410C',
  'primary-text': '#B93A0A',
  'on-primary': '#FFFFFF',
  'primary-soft': '#FCE8D6',
  accent: '#B93A0A',
  'accent-soft': '#FAE0CE',
  error: '#FF3B30',
  'error-soft': '#FFECEB',
  warning: '#F5A623',
  'warning-soft': '#FFF8E1',
  /* 价格色 = 主色「文字档」同值（不另立第三主色；浅底可读性由文字档保证） */
  price: '#B93A0A',
  /* 评分星级（2026-09-21 拍板：撤销「星级填充语义收敛到 primary」（content-flow-visual-polish），
     恢复独立星色——黄色实心星，与 Web 管理端 --color-star 对齐；星色 SHALL NOT 随主色换肤） */
  star: '#FBBF24',
  'star-empty': '#E5E5EA',
  'text-white': '#FFFFFF',
  'text-white-edge': 'rgba(255,255,255,0.24)',
  /* 详情页返回钮黑箭头（微信原生胶囊同款配色；WXSS 可直接消费 var(--nav-back-icon)） */
  'nav-back-icon': '#1A1A1A',
  'text-primary': '#262626',
  'text-secondary': '#595959',
  'text-tertiary': '#999999',
  'bg-page': '#F7F3EF',
  /* 页面顶部淡橙渐变（2026-09-21 §7.34 定稿）：from 浅米白 → to 淡橙；
     取较浅淡橙端（#FFEFE0），保证图形档（主色 #C2410C）对渐变最深处仍 ≥3:1（实测 4.61:1） */
  'bg-page-grad-from': '#FFF9F3',
  'bg-page-grad-to': '#FFEFE0',
  /* feedback-forms-ux-polish：意见反馈页奶油米白底（Q 版暖调表面） */
  'bg-warm': '#FAF6F0',
  'bg-card': '#FFFFFF',
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
  /* 主色通道随新主色 #C2410C（RGB 194, 65, 12）同步（2026-09-21 §7.34；ui-theme-shadows 要求品牌通道与主色一致） */
  'shadow-bar-primary': '0 12rpx 28rpx rgba(194, 65, 12, 0.28)',
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
 * 收录口径：仅收录「以 CSS 变量形态被 WXSS 消费」的色值 token；
 * 仅原生 API 兜底直取实色的键（如 success 历史键已删）不在页面级声明，不收录。
 * 派生引用（--text-hint 等值为 var() 组合、不含裸色值）不属色值真源范畴，仍手写在 App.vue 派生区。
 */
export const CSS_VARS: Record<string, string> = {
  /* 品牌主色（填充/图形档，暖橙） */
  '--color-primary': COLOR_MAP.primary,
  '--color-on-primary': COLOR_MAP['on-primary'],
  '--color-primary-soft': COLOR_MAP['primary-soft'],
  /* 主色「文字档」（浅底上的主色文字 / 价格；填充与图形用 --color-primary） */
  '--color-primary-text': COLOR_MAP['primary-text'],
  /* 强调色（热卖/热搜/新品） */
  '--color-accent': COLOR_MAP.accent,
  '--color-accent-soft': COLOR_MAP['accent-soft'],
  /* 语义色（error/warning/price/star 深浅对称）。原 `--color-like` 已随「评价有用」全链下线删除
     （2026-09-21 资产专项：零消费 token 不留存，PR-05） */
  '--color-error': COLOR_MAP.error,
  '--color-error-soft': COLOR_MAP['error-soft'],
  '--color-warning': COLOR_MAP.warning,
  '--color-warning-soft': COLOR_MAP['warning-soft'],
  '--color-price': COLOR_MAP.price,
  /* 评分星级（黄色实心星；未选/空星见 --color-star-empty） */
  '--color-star': COLOR_MAP.star,
  '--color-star-empty': COLOR_MAP['star-empty'],
  /* 文字三阶层级（--text-hint 等派生引用见 App.vue） */
  '--text-white': COLOR_MAP['text-white'],
  '--text-primary': COLOR_MAP['text-primary'],
  '--text-secondary': COLOR_MAP['text-secondary'],
  '--text-tertiary': COLOR_MAP['text-tertiary'],
  /* 背景 / 边框 */
  '--bg-page': COLOR_MAP['bg-page'],
  '--bg-page-grad-from': COLOR_MAP['bg-page-grad-from'],
  '--bg-page-grad-to': COLOR_MAP['bg-page-grad-to'],
  '--bg-warm': COLOR_MAP['bg-warm'],
  '--bg-card': COLOR_MAP['bg-card'],
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
export const MODAL_CONFIRM_DANGER_COLOR = '#FF3B30'
// uni.showModal 的 confirmColor 不接受 var()，必须用真实色值（重要操作确认按钮）
// ⚠️ 本值 #B93A0A 实为「文字档」--color-primary-text 的字面量（主色两档定稿前的旧主色残留），
//    非当前填充/图形档 --color-primary(#C2410C)；取值本轮不动（避免未评审的行为变更），待技术负责人裁定是否改填充档
// （见 pages/mine/index.vue 注销账号确认弹窗）
export const MODAL_CONFIRM_PRIMARY_COLOR = '#B93A0A'
// pages.json globalStyle 导航栏/窗口底色（JSON 无法引用 TS 常量，此处登记为色值事实源，改动须与 pages.json 同步）
export const NAVIGATION_BAR_BACKGROUND = '#F5F5F7'
// IconSvg 描边唯一兜底/兜底色（MP-11：resolveColor/COLOR_MAP 死机制删除后，var() 形态与空值统一落到本常量；见 components/IconSvg.vue）
export const ICON_FALLBACK_COLOR = '#1C1C1E'
