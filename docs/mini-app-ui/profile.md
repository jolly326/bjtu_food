# 我的 (profile)
- 路由: /pages/profile/index
- 分包: 主包
- 源文件: src/pages/profile/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
个人中心。未登录态内嵌登录/注册/找回表单（`AuthForm`）；已登录态展示用户卡（头像·昵称·统计三宫格·我要贡献）+ 菜单组（我的动态/消息中心/我的发布/意见反馈/设置）。

## 2. 布局结构
- 顶部：`Header title="我的"`（含状态栏占位）。
- 滚动区 `scroll-view`。
- **未登录**：`<AuthForm />`（居中卡片式，登录/注册/找回同卡，含可见 label/邮箱/验证码输入）。
- **已登录**：
  1. 用户卡 `.user-card`（enter-up，`--enter-i:0`）：头像+昵称+`StatusBadge` 角色 + `StatsRow`（评价/已发布/待审核，骨架占位）+ `AppButton`「+ 我要贡献」。
  2. `SettingGroup title="我的"`：`SettingCell` 我的动态(comment)/消息中心(bell，带 `badge-count`)。
  3. `SettingGroup title="通用"`：我的发布(list)/意见反馈(contact)/设置(settings)。
  4. 版本行「食在交大 v1.0.0」。
- 底部：`CustomTabBar`。
- 三个 Sheet：`ContributeSheet`（我要贡献）、`ApplySheet`（申请下架/纠错）、`NicknameSheet`（昵称编辑）。

## 3. 核心组件与用法
- `AuthForm`：未登录态登录/注册/找回（含 logo/mail/lock 等 SVG，均 IconSvg 已注册 key；可见 label）。
- `StatsRow`：统计三宫格（融合进用户卡，去内层阴影）。
- `StatusBadge`：`role` → admin/student 角色徽标（图标+文字，非纯色）。
- `SettingGroup` / `SettingCell`：菜单组与项（icon 走 IconSvg）。
- `AppButton`：`+ 我要贡献` 主操作（单主 CTA）。
- `ContributeSheet` / `ApplySheet` / `NicknameSheet`：底部弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- `IconSvg`：`user`(头像兜底)、`edit`(昵称编辑)、`comment`/`bell`/`list`/`contact`/`settings`（菜单，均注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调/状态：`--color-primary`(主操作/选中)、`--color-primary-soft`(历史 chip 按下等)、`--color-primary-soft2`(未用)、`--color-error`(未用)。
- 圆角/阴影：`--radius-card`、`--shadow-card`、`--radius-tag`。
- 间距：`--spacing-xs/md/sm/lg`（4/8pt 节奏）。
- 字号：`--font-subtitle`(昵称)、`--font-aux`(版本)、`--font-body`。
- 动效：`--press-scale`(`.avatar-wrap:active`/`.nickname-row:active` 均 scale 0.97)、`--press-transition`。
- 布局：`--tabbar-height` + `env(safe-area-inset-bottom)`。

## 5. 交互与动效
- 头像点击 `chooseImage` 上传更新（`uploadImage`）；昵称点击开 `NicknameSheet`；统计三宫格点击进评价/发布/待审核。
- 我要贡献 `ContributeSheet` 选 publishDish/submitStall/submitCanteen/apply；apply 落地 `ApplySheet`。
- 菜单项逐项 `navigateTo`；消息中心 badge 取 `notifyStore.unreadCount`。
- 各 Sheet 统一 spring 0.8/0.3 + 下拉关闭手势 + reduced-motion 降级（由组件实现，从底部进入、exit 短于 enter）。
- 未登录 `AuthForm`：表单内联校验（blur/提交），键盘类型对应（email/number 验证码），错误文案在字段下。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ user/edit/comment/bell/list/contact/settings 均 IconSvg；AuthForm 内 logo/mail/lock 经 Iconfont 补齐；无 emoji。
- ②金额 api 层：✅ 本页无金额。
- ③WaterfallList 禁 slot：➖ 本页无列表/瀑布流，不适用。
- ④三态齐备：⚠️ 用户卡统计区有 `stats-skeleton` 加载态，但整体登录态未设计「空态」——已登录即有数据，可接受；未登录态为 AuthForm 表单，无三态需求。
- ⑤Sheet 规范：✅ ContributeSheet/ApplySheet/NicknameSheet 均套通用弹层规范。
- ⑥按压 0.97：✅ `.avatar-wrap`/`.nickname-row` 用 `scale(var(--press-scale))`。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：➖ 本页用 `SettingGroup title=` 渲染分组标题（菜单分组语义，非内容分区），与 SectionTitle 同源组件体系；无内容区手写标题。
- ⑨底部避让：✅ scroll-wrap `--tabbar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测(角色徽标/状态色)(§8⑥)；统计三宫格数字建议 tabular-nums(§8⑮)；统计区 skeleton 加载态已具备(§8⑱)
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 菜单项/头像/我要贡献按钮 ≥44pt；分组间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.avatar-wrap`/`.nickname-row` `scale(var(--press-scale))`；菜单项由 SettingCell 统一按压 |
| 3 | 固定栏/导航预留安全区 | 合规 | TabBar + scroll-wrap 安全区；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | user/edit/comment/bell/list/contact/settings/logo/mail/lock 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测（建议交付前验证角色徽标/状态色对比） |
| 7 | 不靠颜色 alone 传意 | 合规 | StatusBadge 图标+文字；未读 badge 有数字+点 |
| 8 | prefers-reduced-motion 处理 | 合规 | 各 Sheet 降级交叉淡入；用户卡进场去 transform |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控端 `:active`/touch，无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；Sheet exit 短于 enter |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet 用 `--ease-drawer`/`--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 用户卡 enter-up 用 translateY+opacity；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | Sheet 手势可中断；transition 可重定向 |
| 15 | 数字 tabular-nums | 部分 | 统计三宫格为数字，建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | 字号 token；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「我要贡献」；无破坏性操作在主屏 |
| 18 | loading/empty/error 三态 | 部分 | 统计区 skeleton 加载态；已登录无空态（合理）；AuthForm 有内联校验错误 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 合规 | AuthForm 可见 label、必填标记、blur 校验、input type 对应键盘 |
| 20 | 导航一致：底部≤5 项 icon+label | 合规 | CustomTabBar ≤5，当前项高亮 |
| 21 | 一致性打磨 | 合规 | Sheet 统一 spring 0.8/0.3；按压 0.97；菜单 icon 同线宽 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：用户卡三宫格不挤；AuthForm 在窄屏不溢出。
- [ ] reduced-motion：三个 Sheet 交叉淡入、用户卡进场无位移。
- [ ] 动态字号：昵称/菜单项不截断。
- [ ] 暗色对比：StatusBadge/菜单 icon/版本行文本对比达标。
- [ ] 44pt：菜单项/头像/我要贡献 ≥44pt。
- [ ] 安全区：TabBar 与底部内容不被 home indicator 遮挡。
