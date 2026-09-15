# UI Surface Consistency

UI 表面一致性契约（小程序 `client/`）。本 capability 收敛「卡片表面 / 筛选条 / 头部高度 / 动态入场动效 / 个人中心用户卡」的视觉一致性规则，消除因白色描边卡、筛选图标错位、头部高度不一、合成层圆角失效导致的视觉缺陷。所有规则须与 `docs/project_spec.md` §4 视觉规范及 §5.x 数据契约对齐，冲突以 project_spec 为准。

## Purpose

- 规定列表卡「白底（`--bg-card`）+ 品牌淡色柔和投影」的表面语言：以「浅米灰页面底 — 白卡 — 内容 — 强调」四层结构拉开页面层级。（2026-09-05 由 tab-pages-visual-unify 取代原「与页面同色 + 发丝边、禁止投影」规则；原「杜绝白卡在浅灰页上造成白色块割裂感」的决策已废弃。）
- 约束首页筛选条「食堂 / 价格 / 筛选」三白底胶囊布局与红底态，保证最右「筛选」胶囊稳定贴右、不被长食堂名挤出（tab-pages-visual-refine-2 将右端图标升级为同款胶囊）。
- 统一全站头部高度（以 find/search 页 `.search-nav` 为基准）。（原「含 web-view 返回条」已随 2026-09-13 活动全链路下线、`web-view` 退出小程序而移除）
- 规定动态列表 `enter-up` 入场动画的挂载位置，规避微信合成层 `border-radius` 裁剪失效引发的上左角竖直色块。
- 明确价格「分→元」展示单一真源（`utils/money`）。

## Requirements

### Requirement: 列表卡以白底与柔和投影分层

列表卡（`DishCard`、评价卡等内卡片）SHALL 使用卡片表面色 `--bg-card`（白底），并渲染 `--shadow-card` 柔和品牌淡色投影，以与页面底色 `--bg-page` 形成层级；配合 `border-radius: var(--radius-card)` 与 `overflow:hidden` 裁切圆角外内容。卡 SHALL NOT 以发丝边作为唯一的轮廓表达手段，亦 SHALL NOT 再沿用「卡与页面同色」的旧通则。页面层级 SHALL 由「浅米灰页面底 — 白卡 — 内容 — 强调」四层结构承担：白卡在浅米灰底上以柔和投影抬起即为预期层级表达，不再视为「白色块割裂」。卡片之间 SHALL 保留固定间距以形成呼吸节奏。

本条取代原「列表卡与页面同色、以发丝边勾勒」条款（原条款禁止 `--bg-card` 白底与 `--shadow-card` 投影）。

#### Scenario: 卡片为白底且带投影

- **WHEN** 审查 `DishCard.vue` 与评价卡的卡片根样式
- **THEN** 背景为 `var(--bg-card)`，且引用 `var(--shadow-card)`

#### Scenario: 与页面底色形成层级

- **WHEN** 在浅米灰 `--bg-page` 上查看列表卡
- **THEN** 卡片以白底 + 柔和投影从页面抬起，且卡片之间存在固定间距

#### Scenario: 圆角外内容被裁

- **WHEN** 卡片内含图片/标签等贴边内容
- **THEN** 根元素 `overflow:hidden` 存在，圆角外区域被裁切

### Requirement: 首页筛选条两胶囊 + 最右筛选图标

首页（含 find 结果态）筛选条 SHALL 呈现三个视觉一致的白底圆角控件：可收缩的「食堂」胶囊 + 可收缩的「价格」胶囊（组成 `.filter-chips` 组，`flex:1; min-width:0`），以及最右与二者同款的「筛选」胶囊按钮（`flex-shrink:0; margin-left:auto`，内容为「筛选」文字 + 线性筛选图标）。筛选控件 SHALL 常驻最右、不被内容挤压、不随列表滚动；沿用白底圆角（`--bg-card` + `--radius-pill`）与胶囊文字层级，消除透明图标与两胶囊的风格割裂。筛选胶囊 SHALL NOT 挂跳转（无 `@tap`、不 emit 跳转事件），亦不参与食堂/价格面板的展开；如需跳转，须由产品另行拍板（约定落点仍为 find）。

#### Scenario: 筛选图标常驻最右且不跳转

- **WHEN** 审查 `FilterBar.vue` 的筛选控件
- **THEN** 其为白底圆角胶囊（含「筛选」文字 + 线性图标），具备 `flex-shrink:0` 与 `margin-left:auto`，且无 `@tap` / 跳转 emit

#### Scenario: 食堂名过长时图标不挤出

- **WHEN** 所选食堂名较长、`.filter-chips` 组占满剩余宽度
- **THEN** 食堂胶囊 `flex-shrink:1; min-width:0; max-width:45%` 收缩并以省略号截断，最右「筛选」胶囊仍完整贴右

#### Scenario: 筛选胶囊与两胶囊风格一致

- **WHEN** 查看筛选条整行
- **THEN** 食堂、价格、筛选三个控件同高、同白底圆角、同字号层级，视觉一致

### Requirement: 筛选胶囊红底仅展开态

「全部食堂」/「全部价格」两胶囊默认（收起）SHALL 呈现页面凹陷面（`--bg-page` 底 + `--color-primary` 文字 + 发丝边），下拉展开时（`filterOpen` / `priceOpen`=true）切换为红底（`--color-primary` 底 + 反白文字 `on-primary`）。收起后胶囊文案回显所选食堂名或价格区间；未选时回显「全部食堂」/「全部价格」。

#### Scenario: 默认非红底

- **WHEN** 筛选 / 价格下拉均未展开
- **THEN** 两胶囊为 `--bg-page` 底 + `--color-primary` 文字 + 发丝边，无红底

#### Scenario: 展开为红底

- **WHEN** `filterOpen` 或 `priceOpen` 为 true
- **THEN** 对应胶囊切换为 `--color-primary` 底 + 反白文字

#### Scenario: 收起回显所选

- **WHEN** 用户已选某食堂 / 某价格区间并收起下拉
- **THEN** 食堂胶囊显示所选食堂名、价格胶囊显示对应区间文案，而非「全部」

### Requirement: 价格以元展示、分→元单一真源

价格展示与价格筛选链路 SHALL 以「元」为用户可见口径，单位换算 SHALL 收敛于 `utils/money` 单一真源，仅存在两种合法转换：**后端裸金额（分）→ 展示（元）** 用 `fenToYuan`；**api 提交（元）→ 后端（分）** 在 api 层用一次 `yuanToFen`。前端筛选状态与提交参数（`FilterBar` 的 `priceRange` prop / `price-select` emit、首页与发现页持有的价格区间、透传给 `searchDishesPage` 的 `minPrice`/`maxPrice`）SHALL 统一以「元」表达，**不得**以「分」为中间存储/回传口径二次换算，禁止任何页面/组件裸算 `/100`。（历史 `HomePriceSheet`、`home/index.vue` 的 `priceLabel`、`find/index.vue` 的 `findPriceLabel` 已随 `FilterBar` 合并移除，本条款不再引用。）

#### Scenario: 选中 10–20 元正确显示

- **WHEN** 价格区间 `{ min: 10, max: 20 }`（元）处于选中态且下拉收起
- **THEN** 价格胶囊显示「10-20 元」，而非「1000-2000」

#### Scenario: 提交参数与元口径一致
- **WHEN** 用户选择 10–20 元区间并触发筛选请求
- **THEN** 请求参数 `minPrice`/`maxPrice` 以后端分单位提交（1000/2000 分），且前端仅经 api 层一次性转换，无双重换算

### Requirement: 全站头部高度统一

所有页面头部（`AppHeader` `.header-wrap`）SHALL 与搜索页（find）`.search-nav` 等高；统一以 `padding-bottom: var(--spacing-sm)`（16rpx）在红色块底部留白复刻。头部不得各自追加额外 padding 覆盖（已删除 `.header-wrap.home` 冗余覆盖）。

#### Scenario: 搜索页与其余页 header 等高

- **WHEN** 并排比对 find 页 `.search-nav` 与 home / dynamic / profile 的 `AppHeader`
- **THEN** 二者红色块底部留白一致（均为 `--spacing-sm`）

### Requirement: 个人中心用户卡用动态类区分认证态

`pages/mine/index.vue` 用户卡 SHALL 以动态类 `.user-card--verified`（认证态）/ `.user-card--guest`（游客态）区分，而非内联 `v-bind` 三元；两种状态 SHALL 同为白底 `--bg-card` + 柔和投影 `--shadow-card`，SHALL NOT 再沿用「认证态白底、游客态透明面」的表面差异。认证态与游客态的区分 SHALL 仅由顶部主色软条纹与卡片内容（昵称 / 绑定邮箱、以及游客态的「去认证」引导）表达。

#### Scenario: 两态类名切换

- **WHEN** `isVerified` 变化
- **THEN** 用户卡根类在 `user-card--verified` / `user-card--guest` 之间切换，非内联样式拼接

#### Scenario: 游客态同为白底投影

- **WHEN** 未认证（游客态）用户查看「我的」页
- **THEN** 用户卡背景为 `var(--bg-card)` 且带 `var(--shadow-card)` 投影，非透明面
