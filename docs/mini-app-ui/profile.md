# 我的 (profile)
- 路由: /pages/profile/index
- 分包: 主包
- 源文件: src/pages/profile/index.vue
- 最后依据 skills 校对: 2026-08-02
- 重构记录: 2026-08-03 信息架构重组（用户确认）：Hero 卡改「头像+昵称+ID+认证标识+>`、网格 4 项（动态/反馈中心/消息/评价）、设置内嵌页面底部、新增个人信息详情页与我的评价页；反馈中心重定位为「反馈问题 + 查看反馈进度」（去发布/贡献 tab）、消息中心改独立页（`pages/profile/messages/index`）、我的动态去分类 tab 直列。

## 1. 页面定位
个人中心。未登录态内嵌登录/注册/找回表单（`AuthForm`）；已登录态 = Hero 用户卡（头像·昵称·ID·认证）+ 4 项功能网格（我的动态/反馈中心/消息中心/我的评价）+ 内嵌设置分组（通知/通用/账号）。入口语义：动态→`my-moments/index`、反馈中心→`messages-services`（发布+贡献+意见反馈）、消息→`MessageSheet` 弹层、评价→`my-reviews/index`、Hero 卡→`profile-edit/index`（个人信息详情）。

## 2. 布局结构
- 顶部：`Header title="我的"`（含状态栏占位）。
- 滚动区 `scroll-view`。
- **未登录**：`<AuthForm />`（居中卡片式，登录/注册/找回同卡，含可见 label/邮箱/验证码输入）。
- **已登录**：
  1. Hero 用户卡 `.user-card`（enter-up，`--enter-i:0`）：**纯白卡**（`--bg-card`，无渐变）+ 头像（**无编辑角标**）+ 昵称(`--font-h3`) + 元信息行（**用户 ID** + **认证标识** `verify-tag`：admin=已认证/student=未认证，role 派生）+ 右侧 **`>` 箭头**；**整卡点击**跳个人信息详情页。
  2. 4 项网格 `.grid-menu`（enter-up，`--enter-i:1`，4 列，统一主色软底大图标）：我的动态(comment)/反馈中心(contact)/消息中心(bell，未读胶囊)/我的评价(star)。
  3. 设置内嵌：主列表（`--enter-i:2`）：动态与消息通知开关 + 关于食在交大/隐私政策/清除缓存；**账号注销单独卡片**（`--enter-i:3`，危险操作隔离，danger 标红）。**无退出登录、图标纯主题色无背景块、配色仅主题色+白色**。
  4. 版本行「食在交大 v1.0.0」。
- 底部：`CustomTabBar`。
- 无 Sheet（`ContributeSheet`/`ApplySheet`/`NicknameSheet`/`MessageSheet` 均已移除：反馈中心接管贡献、资料页接管编辑、消息中心为独立页）。
- 子页：`profile-edit/index`（个人信息：改头像/昵称、展示 ID 与认证、保存吸底）、`my-reviews/index`（我的评价：列表+删除）、`messages/index`（消息中心独立页：列表+单条已读+跳转）、`messages-services/index`（反馈中心：反馈入口 + 实体申请进度 + 反馈回复）。
- 子页跳转：我的动态→`my-moments/index`（一列直列，无分类 tab）；反馈中心→`messages-services/index`；消息→`messages/index`；评价→`my-reviews/index`。

## 3. 核心组件与用法
- `AuthForm`：未登录态登录/注册/找回（含 logo/mail/lock 等 SVG，均 IconSvg 已注册 key；可见 label）。
- 认证标识 `.verify-tag`：页内实现（非组件），`isVerified = role === 'admin'` 派生（无独立认证字段）；仅「已认证/未认证」两种文案（admin 已认证主色软底 / student 未认证中性）。
- 功能网格 `.grid-menu`：页内实现（非组件），4 列；`gridItems` 数据驱动（icon/label/action），图标统一 `--color-primary` + `--color-primary-soft` 软底（单色克制），消息项未读胶囊实时绑定 `notifyStore.unreadCount`。
- `SettingGroup` / `SettingCell`：内嵌设置列表；图标**纯主题色**（`--color-primary`）无背景块；`danger` 危险操作标红；`badge-count` 未读角标。
- `MessageSheet`：消息中心弹层（spec「消息并入我的区块」；复用 sheet 视觉，列表 ≤50 条倒序，未读红点 + 类型标签 + 时间；点击标记已读并跳转：`dish_audit` 仅已读（无独立菜品页），其余→动态详情；「全部已读」置灰角标）。
- `IconSvg`：`user`(头像兜底)、`edit`(昵称编辑+头像角标)、`comment`/`list`/`bell`/`plus`/`contact`/`settings`（菜单，均注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 强调/状态：`--color-primary`(头像编辑角标/网格图标)、`--color-primary-soft`(网格图标软底)、`--color-error`(未读胶囊/列表角标)。
- 圆角/阴影：`--radius-card`、`--shadow-card`、网格图标块圆角 24rpx。
- 间距：`--spacing-xs/md/sm/lg`（4/8pt 节奏）。
- 字号：`--font-h3`(昵称)、`--font-aux`(版本)、`--font-body`。
- 动效：`--press-scale`(`.avatar-wrap:active`/`.nickname-row:active` 均 scale 0.97)、`--press-transition`。
- 布局：`--tabbar-height` + `env(safe-area-inset-bottom)`。

## 5. 交互与动效
- 头像点击 `chooseImage` 上传更新（`uploadImage`，右下角编辑角标提示）；昵称点击开 `NicknameSheet`；统计三宫格点击进评价/发布/待审核。
- 我要贡献 `ContributeSheet` 选 publishDish/submitStall/submitCanteen/apply；apply 落地 `ApplySheet`。
- 消息中心卡点击进消息中心（摘要 = 最近 1 条 `content`，未读胶囊 = `notifyStore.unreadCount`）。
- 菜单项逐项 `navigateTo`。
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
- 待讨论项：暗色模式对比度待实测(角色徽标/状态色)(§8⑥)；**统计三宫格数字 tabular-nums 已落地**（StatsRow.vue `.stat-value`，见 §8⑮）；统计区 skeleton 加载态已具备(§8⑱)
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
| 15 | 数字 tabular-nums | 合规 | 统计三宫格 `.stat-value` 已 `font-variant-numeric: tabular-nums`（StatsRow.vue `.stat-value`） |
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
