// 主题->颜色 token 映射（唯一事实源）。
// 仅 IconSvg 的颜色参数需要真实色值（WXSS 不接受 var()）；其余一律走 CSS 变量 var(--xxx)。
export const COLOR_MAP = {
  primary: '#9B2A1D',
  'on-primary': '#FFFFFF',
  'primary-dark': '#7A1F14',
  'primary-soft': '#E8D0C4',
  'primary-surface': '#9B2A1D',
  'on-primary-surface': '#FFFFFF',
  accent: '#C45A3C',
  'accent-soft': '#E8D0C4',
  error: '#FF3B30',
  'error-soft': '#FFECEB',
  success: '#10B981',
  'success-soft': '#ECFDF5',
  warning: '#F5A623',
  'warning-soft': '#FFF8E1',
  price: '#C45A3C',
  star: '#F5A623',
  'star-empty': '#E5E5EA',
  like: '#B53B2C',
  'like-soft': '#F6E3E0',
  'text-white': '#FFFFFF',
  'text-white-secondary': 'rgba(255,255,255,0.85)',
  'text-white-soft': 'rgba(255,255,255,0.84)',
  'text-white-faint': 'rgba(255,255,255,0.18)',
  'text-white-edge': 'rgba(255,255,255,0.24)',
  'text-primary': '#1D1A18',
  'text-secondary': '#6E6964',
  'text-tertiary': '#8F8A84',
  'text-quaternary': '#ABA59E',
  'bg-page': '#F1ECE6',
  'bg-card': '#FFFFFF',
  'bg-input': '#F7F5F2',
  'bg-soft': '#EDE9E5',
  'bg-placeholder': '#F0ECE8',
  'border-color': '#E8E3DE',
  'border-bold': '#CBC5BE',
  'overlay-dark-strong': 'rgba(0,0,0,0.6)',
  'overlay-dark-deep': 'rgba(0,0,0,0.65)',
  'overlay-dark-mid': 'rgba(0,0,0,0.5)',
  'overlay-dark-soft': 'rgba(0,0,0,0.15)',
  'overlay-dark-faint': 'rgba(0,0,0,0.06)',
  'overlay-scrim': 'rgba(0,0,0,0.4)',
  'color-cell-activity': '#1E5FCE',
  'bg-cell-activity': '#CFE3FA',
  'color-cell-feedback': '#0E9E6E',
  'bg-cell-feedback': '#C4ECDD',
  'shadow-card': '0 2px 12px rgba(0,0,0,0.08)',
  'shadow-card-soft': '0 8rpx 32rpx rgba(0,0,0,0.06)',
  'shadow-modal': '0 18rpx 54rpx rgba(0,0,0,0.18)',
  'blur-bg': 'rgba(255,255,255,0.72)',
  'blur-bg-solid': 'rgba(255,255,255,0.92)',
  'glass-highlight': 'rgba(255,255,255,0.5)',
  'glass-highlight-soft': 'rgba(255,255,255,0.22)',
} as const

export type IconColorName = keyof typeof COLOR_MAP

// ========== 原生属性例外登记（uni-app 限制） ==========
// 微信原生 <swiper> 的 indicator-active-color / indicator-color 不接受 var()，
// 必须用真实色值（见 components/ImageSwiper.vue）。删除 uni.scss 后，原例外说明迁此。
export const SWIPER_INDICATOR_ACTIVE_COLOR = '#ffffff'
export const SWIPER_INDICATOR_COLOR = 'rgba(255,255,255,0.4)'
