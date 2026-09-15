# Client Visual Language Specification

## Purpose

建立小程序端统一的视觉语言：以暖砖红色彩体系、三档圆角、品牌淡色柔和投影、2px 线性图标与五档文字层级为基线，约束首页 / 我的两个主 Tab 页的信息优先级与间距节奏，消除图标线面混用与信息层级扁平，让高频页面呈现一致的质感与呼吸感。

## Requirements

### Requirement: 色彩体系采用暖砖红 token 取值

主色 `--color-primary` SHALL 为 `#C45549`（提亮后的暖砖红）；辅助浅红 `--color-primary-soft` SHALL 为 `#F8E8E5`；页面底色 `--bg-page` SHALL 为 `#F7F3EF`（浅米灰）；卡片底色 `--bg-card` SHALL 保持 `#FFFFFF`。文字层级 SHALL 收敛为三阶：一级 `--text-primary` `#262626`、二级 `--text-secondary` `#595959`、三级 `--text-tertiary` `#999999`；`--text-quaternary` SHALL 移除，其既有引用一律改归 `--text-tertiary`，不得再设第四档弱化文字色。

所有取值 SHALL 仅声明于 `theme/tokens.ts` 与 `App.vue` 的 `page` / `:root` 双份声明中（两处一一对应，遵守 `ui-token-system` 的「令牌双份声明同步」），组件与页面 SHALL NOT 出现裸 hex。随主色派生的 token（含 `--color-price` 与含品牌色通道的阴影）SHALL 同步为新主色 `#C45549`（RGB `196, 85, 73`）。

#### Scenario: 主色与底色取值
- **WHEN** 审查 `theme/tokens.ts` 中的 `--color-primary` 与 `--bg-page`
- **THEN** 分别为 `#C45549` 与 `#F7F3EF`

#### Scenario: 文字层级取值
- **WHEN** 审查 `--text-primary` / `--text-secondary` / `--text-tertiary`
- **THEN** 分别为 `#262626` / `#595959` / `#999999`，且 `--text-quaternary` 已移除、无引用残留（文字层级为三阶）

#### Scenario: 派生 token 随主色同步
- **WHEN** 审查含品牌色通道的阴影 token 与 `--color-price`
- **THEN** 其通道 / 取值为新主色 RGB `196, 85, 73` / `#C45549`，而非旧的 `184, 74, 62`

### Requirement: 圆角按「大卡 / 小元素 / 圆形」三档收敛

圆角 SHALL 收敛为三档：内容大卡（`DishCard` / 我的页用户卡 / 功能卡）`32rpx`（≈16px）；标签与按钮等小元素 `16rpx`（≈8px）；头像与圆形图标底 `50%`。既有 `--radius-card: 32rpx` 保持不变；`--radius-tag` 与 `--radius-btn` SHALL 由 `32rpx` 收敛为 `16rpx`，以拉开大卡与小元素的差异。

#### Scenario: 大卡圆角
- **WHEN** 审查 `DishCard` / 我的页用户卡的 `border-radius`
- **THEN** 引用 `--radius-card`（`32rpx`）

#### Scenario: 小元素圆角收敛
- **WHEN** 审查标签、小按钮的圆角令牌取值
- **THEN** `--radius-tag` 与 `--radius-btn` 为 `16rpx`，与大卡的 `32rpx` 可区分

#### Scenario: 圆形元素
- **WHEN** 审查默认头像、圆形图标底的圆角
- **THEN** 为 `50%`（`--radius-circle`）

### Requirement: 文字层级按五档映射字号与字重

文字 SHALL 按五档映射既有 `--font-*` 与 `--weight-*` 令牌，不得为本次新增字号别名（遵守 `ui-token-system` 的「字号别名收敛」）：导航标题 `36rpx` / `600`；一级标题（菜名、用户昵称、菜单主标题）`32rpx` / `600`；正文（动态正文、菜品说明）`28rpx` / `400`；辅助信息（时间、距离、版本号）`24rpx` / `400`；强调信息基准（互动数）`32rpx` / `600`。

两个 SHALL 作为显式例外、不套用五档映射：
- **价格**：沿用 Requirement「首页菜品卡信息优先级」的「大号加粗」要求，为 `--font-h3`(`36rpx`) / `--weight-bold`，并以 `--color-price` 保证第一眼可见；不纳入 `32rpx` / `600` 强调档。
- **评分**：评分徽标叠于图片层、尺寸受限，为 `--font-tiny` 的深灰小胶囊 + 白字（见「首页菜品卡信息优先级」标签与评分收拢场景）；不纳入五档映射。

次要信息 SHALL 通过降低字重与改用 `--text-tertiary` 弱化，SHALL NOT 仅靠缩小字号制造层级。

#### Scenario: 五档映射落地
- **WHEN** 审查首页 / 动态 / 我的三页的文案样式
- **THEN** 落入五档的文案（导航标题 / 一级标题 / 正文 / 辅助 / 互动数）各自命中对应档位的字号与字重令牌；价格与评分按上文显式例外处理，不要求命中五档

#### Scenario: 未新增字号别名
- **WHEN** 审查 `--font-*` 令牌集合
- **THEN** 未出现取相同数值的重复别名

#### Scenario: 次要信息弱化
- **WHEN** 查看时间、距离、版本号等辅助信息
- **THEN** 其颜色为 `--text-tertiary`，视觉权重明显低于一级标题

### Requirement: 图标统一为 2px 线性风格

全站图标 SHALL 统一为 2px 线宽、圆角端点与圆角连接的线性风格（`stroke-width="2"`、`stroke-linecap="round"`、`stroke-linejoin="round"`），SHALL NOT 线面混用。图标 SHALL 统一经 `IconSvg` 渲染；缺失键 SHALL 渲染中性 `empty` 占位而非语义图标（沿用既有 `IconSvg` 回退规则）。

#### Scenario: 线宽与端点一致
- **WHEN** 审查 `assets/icons` 下的图标资源
- **THEN** 均为 2px 线宽、圆角端点与圆角连接，无面性图标混入

#### Scenario: 缺失键回退占位
- **WHEN** 以不存在的 `name` 渲染 `IconSvg`
- **THEN** 渲染中性 `empty` 占位，回退为语义图标以外的图标

### Requirement: 首页顶部区域轻量化分层

首页搜索入口 SHALL 从红色顶栏中抽出，呈现为悬浮白色圆角卡片（`--bg-card` 白底 + 柔和投影），与红色顶栏形成层级差，弱化红栏厚重感。筛选栏 SHALL 置于页面底色 `--bg-page`（浅米灰）区域，与白色搜索卡片分层；筛选图标 SHALL 为线性风格。本条仅调整表面与层级，SHALL NOT 改变筛选条的布局约束与点击行为（`ui-surface-consistency` 的「两胶囊 + 最右筛选图标」「筛选胶囊红底仅展开态」条款继续有效）。

#### Scenario: 搜索入口为悬浮白卡
- **WHEN** 查看首页顶部
- **THEN** 搜索入口呈现为白底圆角卡片且带柔和投影，非红底内嵌样式

#### Scenario: 筛选栏与搜索卡分层
- **WHEN** 查看首页筛选栏
- **THEN** 筛选栏落在 `--bg-page` 底色区，与白色搜索卡片在明度上可区分

#### Scenario: 筛选图标线性且行为不变
- **WHEN** 查看筛选栏最右侧图标
- **THEN** 其为线性风格、常驻最右，且不新增跳转行为

### Requirement: 首页菜品卡信息优先级

菜品卡 SHALL 按「菜名 > 价格 > 标签 > 食堂 > 距离」排序视觉权重。菜名 SHALL 加粗加深；价格 SHALL 以 `--color-price`（主色系）大号加粗置于卡片右上角；食堂与距离 SHALL 缩小字号并降为 `--text-tertiary`、置于卡片底部；评分 SHALL 收为图片层上的深灰小胶囊 + 白字；菜品标签（如「必吃推荐」「招牌菜」）SHALL 缩小为浅红底（`--color-primary-soft`）+ 深红文字，置于菜名下方。图片 / 占位图区域占比 SHALL 缩小，把视觉重心让给文字信息。

#### Scenario: 价格突出
- **WHEN** 查看任一菜品卡
- **THEN** 价格位于卡片右上角，为 `--color-price`、字号与字重均高于食堂与距离

#### Scenario: 食堂与距离弱化
- **WHEN** 查看菜品卡底部信息
- **THEN** 食堂与距离为 `--text-tertiary` 且字号小于菜名

#### Scenario: 标签与评分收拢
- **WHEN** 查看带标签与评分的菜品卡
- **THEN** 标签为浅红底深红字且位于菜名下方，评分为叠在图片层上的深灰小胶囊白字

### Requirement: 我的页用户卡与统一认证引导

我的页用户卡 SHALL 呈现为与首页 / 动态一致的白色圆角卡片 + 柔和投影。未认证态 SHALL 以「主色文字 + 细边框」的轻量胶囊按钮「去认证」替代锁形「未认证」徽章，作为页面唯一常驻认证入口；该按钮 SHALL NOT 为实心浅红底块。菜单列表内 SHALL NOT 出现「认证」弱标识。需认证入口在点击时 SHALL 仍弹出 `AuthSheet` 引导（`client-auth-sheet-placement` 的挂载与触发规则不变）。

#### Scenario: 用户卡白底带投影
- **WHEN** 查看我的页用户信息区
- **THEN** 其为 `--bg-card` 白底、`--radius-card` 圆角、带柔和投影

#### Scenario: 去认证行动入口
- **WHEN** 未认证用户查看我的页
- **THEN** 用户卡内出现「主色文字 + 细边框」胶囊按钮「去认证」，无锁形「未认证」徽章，亦非实心浅红底块

#### Scenario: 菜单内无认证标识
- **WHEN** 查看我的页菜单列表各行
- **THEN** 行内不出现「认证」文字标识

#### Scenario: 点击需认证入口仍引导
- **WHEN** 未认证用户点击需认证入口
- **THEN** 仍弹出 `AuthSheet` 认证引导

### Requirement: 我的页菜单节奏与版本号弱化

菜单列表 SHALL 收紧行高并以细分割线分隔行。功能宫格 SHALL 保持三格（意见反馈 / 系统通知 / 我的评价），SHALL NOT 新增「我的收藏」（违反「收藏功能全量移除，无入口 / 字段 / 图标」红线）或「浏览历史」（需新建页面与后端接口，超出本次 UI 范围），亦 SHALL NOT 回加「最新活动」格（已随 2026-09-13 活动全链路下线移除）。底部版本号 SHALL 置于页面右下角，字号缩小至不大于 `22rpx`（≈11px）并降为 `--text-tertiary`，以释放页面垂直重心。

#### Scenario: 菜单行高与分割线
- **WHEN** 查看我的页菜单列表
- **THEN** 行高较改动前收紧，行间存在细分割线

#### Scenario: 功能宫格不新增入口
- **WHEN** 查看我的页功能宫格区
- **THEN** 仍为单行三格且仅含意见反馈 / 系统通知 / 我的评价，无「我的收藏」「浏览历史」，无「最新活动」

#### Scenario: 版本号弱化
- **WHEN** 滚动至我的页底部
- **THEN** 版本号位于右下角、字号不大于 `22rpx`、颜色为 `--text-tertiary`

### Requirement: 我的页视觉比例细节

「我的」页 SHALL 在不新增任何入口/按钮/功能的前提下落实以下视觉比例：用户卡头像显示尺寸不小于 `120rpx` 且为正圆，昵称使用一级字重（600）；游客编号 / 绑定邮箱以三级灰（`--text-tertiary`）小号弱化。未认证「去认证」为细边框胶囊（边框细于或等于 `1rpx`，主色文字字重 500）。用户卡右侧箭头为三级灰小号。功能宫格入口 SHALL 保留且不新增：图标底圆缩小至不大于 `88rpx`、卡片高度较现状收窄（原「新」角标规则随「最新活动」格于 2026-09-13 删除而作废；系统通知格未读红点规则见 `profile-restructure`）。底部版本号 SHALL 位于右下角、字号不大于 `20rpx`、颜色为 `--text-tertiary`。

#### Scenario: 头像与文字层级
- **WHEN** 查看「我的」页用户卡
- **THEN** 头像不小于 `120rpx` 正圆，昵称字重 600，游客编号/邮箱为三级灰小号，右箭头三级灰小号

#### Scenario: 去认证细边框
- **WHEN** 未认证用户查看「我的」页
- **THEN** 「去认证」为细边框（≤`1rpx`）胶囊、主色文字字重 500，且仍弹 `AuthSheet`

#### Scenario: 功能宫格收窄且不新增入口
- **WHEN** 查看功能宫格区
- **THEN** 仍为三格（意见反馈 / 系统通知 / 我的评价），图标底圆 ≤`88rpx`、卡片高度较现状收窄

#### Scenario: 版本号再弱化
- **WHEN** 查看「我的」页右下角
- **THEN** 版本号字号不大于 `20rpx`、三级灰、位于右下角
