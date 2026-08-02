# 设置 (settings)
- 路由: /pages/settings/index
- 分包: 主包
- 源文件: src/pages/settings/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
设置页：通知开关（前端本地存储占位）、关于/隐私政策/清除缓存（弹窗）、退出登录、账号注销（二次确认）。

## 2. 布局结构
- 顶部：`Header title="设置" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`。
- 三个 `SettingGroup`：
  1. 通知：`cell`（bell 图标 + 标签 + 自绘 `switch` 开关）。
  2. 通用：`SettingCell` 关于食在交大(logo)/隐私政策(lock)/清除缓存(delete)。
  3. 账号：`SettingCell` 退出登录(profile)/账号注销(delete, danger)。
- 版本行「食在交大 v1.0.0」。
- 账号注销为 danger 语义（红），与常规项空间隔离（独立分组末项）。

## 3. 核心组件与用法
- `SettingGroup` / `SettingCell`：分组与项（icon 走 IconSvg，均注册 key）。
- 自绘 `switch`：通知开关（`.switch` + `.switch-knob`，`notifyOn` 状态，transition 可中断）。
- `IconSvg`：`bell`(通知)、`logo`(关于)、`lock`(隐私)、`delete`(清除/注销)、`profile`(退出)。
- 原生 `uni.showModal`：关于/隐私/清除/退出/注销确认（`confirmColor` 例外见 §8⑤⑦）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调/状态：`--color-primary`(开关开/激活)、`--color-error`(注销 danger，未直接用色值，由 SettingCell danger 映射语义 token)。
- 边框：`--border-bold`(开关关态底)、`--border-color`(cell 分割线)。
- 圆角：`--radius-tag`(开关)、`--radius-card`。
- 间距：`--spacing-md/sm/xl`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`。
- 动效：`--press-scale`(`.cell:active`)、`--ease-out`(开关 knob 过渡)。
- 布局：scroll-wrap `padding: var(--spacing-md) 0 0`。

## 5. 交互与动效
- 通知开关：点击 toggle 并写 `uni.setStorageSync('setting_notify')`（前端占位，真推送留三期）。
- 关于/隐私：`uni.showModal` 展示说明（无 confirmColor 定制）。
- 清除缓存：`showModal` 确认 → `clearStorageSync` + `restoreFromCache` 保留登录态。
- 退出登录：`showModal` → `logout` + `reLaunch` profile。
- 账号注销：`showModal` 二次确认（`confirmColor: '#e54d42'`，**原生 API 例外已登记**）→ `deleteAccount` + 清 token + reLaunch。
- 开关 knob 用 `--ease-out` transform 过渡（<300ms）；`.cell` 按压 `scale(var(--press-scale))`。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ bell/logo/lock/delete/profile 均 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：➖ 设置页为静态分组，无加载/空态需求，合规。
- ⑤Sheet 规范：➖ 用 uni.showModal 而非自定义 Sheet；showModal 为原生组件，不涉及 spring/Sheet 规范，合规。
- ⑥按压 0.97：✅ `.cell:active scale(var(--press-scale))`（本目录一致性红线 §0.3 记录此处原 `scale(0.99)` 待改为 `--press-scale`；当前代码已为 `scale(var(--press-scale))`）。
- ⑦颜色 token：⚠️ `uni.showModal` 的 `confirmColor: '#e54d42'` 为原生属性不支持 `var()`，已在 `src/uni.scss` 登记为例外（对应 `--color-error`），不计违规。其余无裸 hex。
- ⑧SectionTitle：➖ 用 SettingGroup title 渲染分组标题，非内容分区手写标题，合规。
- ⑨底部避让：➖ 无底部固定栏，scroll-wrap 无 tabbar 避让需求，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：通知开关是否接真实推送通道（当前前端占位）
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | cell/开关点击区 ≥44pt；分组间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.cell:active scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | bell/logo/lock/delete/profile 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 部分 | `uni.showModal` `confirmColor:'#e54d42'` 为原生例外（已登记）；其余 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；注销 danger 文本对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 注销有文字「账号注销」+ danger 语义；开关有开/关态 |
| 8 | prefers-reduced-motion 处理 | 合规 | 开关 knob transition 在 reduced-motion 可降为 opacity；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | 开关 <300ms；press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 开关 `--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 开关 transform/opacity；无 `transition:all` |
| 14 | 可中断动效 | 合规 | 开关 transition 可中断 |
| 15 | 数字 tabular-nums | ➖ | 本页无数字计数 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 账号注销为 danger 语义、独立末项、二次确认；其余为常规 |
| 18 | loading/empty/error 三态 | ➖ | 静态分组，无三态需求 |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 非 tab 页；返回由 Header |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；开关 knob 动效同源；danger 视觉隔离 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：三分组不溢出；注销项与常规项视觉隔离清晰。
- [ ] reduced-motion：开关无突兀位移动画。
- [ ] 动态字号：cell 标签不截断。
- [ ] 暗色对比：注销 danger 文本、开关开态 `--color-primary` 对比达标。
- [ ] 44pt：cell / 开关命中区 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡；showModal 原生居中不受影响。
