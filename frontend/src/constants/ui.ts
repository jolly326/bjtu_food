/**
 * UI 常量（非设计 token）
 *
 * 注意：微信小程序原生 <swiper> 的 indicator-active-color 等属性
 * 不接受 CSS var()，因此这里用真实颜色值直接传入，而非 var(--xxx)。
 * 这是已知原生属性限制（native-attr exception），不违反「禁止裸色值」规则。
 */
// Native swiper indicator color exception — var() unsupported by <swiper> indicator-active-color
export const SWIPER_INDICATOR_ACTIVE_COLOR = '#ffffff'
// 非激活小圆点颜色（原生 <swiper> indicator-color 同样不接受 var()，同属原生属性限制例外）
export const SWIPER_INDICATOR_COLOR = 'rgba(255,255,255,0.4)'
