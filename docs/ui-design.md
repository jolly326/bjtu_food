# UI 设计规范（食在交大 bjtu_food）

> 本文档描述小程序端（client）的整体视觉规范、设计 Token、组件体系与页面地图，**以当前代码为准**（`client/src/App.vue`、`client/src/theme/tokens.ts`、`client/src/uni.scss`）。
> 面向后续前端开发/改版，保证视觉一致性与可维护性。

## 1. 设计 Token（单一事实源）

### 1.1 品牌主色：朱砂红（Vermilion）
产品决策：全站主色由鲜橙调整为**朱砂红**并收深，呼应食堂暖色场景，避免过亮刺眼。

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--color-primary` | `#9B2A1D` | `#C45A3C` | 主色（按钮、强调、选中） |
| `--color-primary-dark` | `#7A1F14` | `#A8482E` | 主色暗阶（hover/深按） |
| `--color-primary-soft` | `#E8D0C4` | `#3A211C` | 主色浅底（图标软底/选中标签底） |
| `--color-primary-surface` | `#9B2A1D` | `#A8482E` | 主色表面（header/首页顶部大面积色块） |
| `--color-on-primary-surface` | `#FFFFFF` | `#F5EFEC` | 表面上的文字 |

> 色值定义在 `App.vue` 的全局 `<style>`（`page, :root`），**禁止组件内裸色值**，一律 `var(--xxx)`。深色模式通过页面根节点挂 `.theme-dark` 覆盖 token。

### 1.2 语义色
| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--color-accent` | `#C45A3C` | `#C45A3C` | 强调（热卖/热搜/新品统一） |
| `--color-price` | `#C45A3C` | `#E8A07E` | 价格红 |
| `--color-error` | `#FF3B30` | `#FF6B61` | 错误 |
| `--color-success` | `#10B981` | `#34D399` | 成功 |
| `--color-like` | `#B53B2C` | `#D9695A` | 喜欢 |
| `--color-star` | `#F5A623` | `#FFC24B` | 评分星 |
| `--color-star-empty` | `#E5E5EA` | `#3A3632` | 空星 |

### 1.3 中性色（文字四档，对比度达标）
| Token | 浅色 | 深色 | 对比度 |
|---|---|---|---|
| `--text-primary` | `#1D1A18` | `#F4F0EC` | ≥7:1 |
| `--text-secondary` | `#6E6964` | `#B8B0A8` | ≥4.5:1 |
| `--text-tertiary` | `#8F8A84` | `#8E887F` | ≥3:1 |
| `--bg-page` | `#F7F5F2` | `#161310` | 页面背景 |
| `--bg-card` | `#FFFFFF` | `#201D1A` | 卡片背景 |

> 中性灰统一带极轻暖调，与朱砂红主色协调（非冷灰）。

### 1.4 圆角（4pt 体系）
| Token | 值 | 用途 |
|---|---|---|
| `--radius-tag` | 16px | 标签 |
| `--radius-card` | 16px | 卡片 |
| `--radius-modal` | 24px | 弹层 |
| `--radius-btn` | 16px | 按钮 |
| `--radius-icon` | 12px | 图标底 |
| `--radius-sheet` | 24px | 底部弹层 |

### 1.5 间距（4pt 基准栅格）
`--spacing-2xs(4rpx)` / `--spacing-xs` / `--spacing-sm` / `--spacing-md` / `--spacing-lg` / `--spacing-xl`，以 4 的倍数递增，禁止裸 `4rpx` 之外的值。

## 2. 主题模式

### 2.1 深色模式
- 状态：`theme` store（`stores/theme.ts`），手动开关 + 本地持久化 + 跟随系统
- 实现：页面根节点挂 `.theme-dark` → 命中 `App.vue` 的深色 token 覆盖 → 全站即时切换
- 跟随系统：未手动设置时取 `uni.getAppBaseInfo().theme`，并监听 `wx.onThemeChange` 自动同步

### 2.2 图标色
`IconSvg`（SVG data-uri）无法解析 `var()`，色值从 `theme/tokens.ts` 的 `ICON_COLOR_VARS` 读取（浅/深两套真实色值）。改图标色只需改该文件。

## 3. 页面地图（共 15 页，以 pages.json 为准）

### 主包（9 页）
| 路径 | 页面 | 说明 |
|---|---|---|
| `/pages/home/index` | 首页 | 瀑布流菜品、品类滚轮、筛选、搜索入口、广播 ticker |
| `/pages/find/index` | 发现 | 分类宫格、搜索、菜品列表 |
| `/pages/profile/index` | 我的 | 个人信息、功能宫格、认证入口、主题切换、注销 |
| `/pages/community/index` | 动态广场 | 社区动态流（举报用 useReport） |
| `/pages/feedback/index` | 意见反馈 | 反馈表单（suggestion/add/error/bug） |
| `/pages/profile/notifications/index` | 消息通知 | 通知列表、未读红点 |
| `/pages/activity/index` | 最新活动 | 公众号文章卡片（首页万能区 + 「我的」入口展示，点击提示「功能暂未实现」，2026-08-19） |
| `/pages/about/index` | 关于我们 | 版本/联系 |
| `/pages/webview/index` | WebView | 内嵌公众号文章 |

### 分包 pages-detail（3 页）
| 路径 | 页面 | 说明 |
|---|---|---|
| `/pages-detail/moment` | 动态详情 | 动态+评论（评论举报保留内联） |
| `/pages-detail/dish` | 菜品详情 | 菜品+评价+评分分布 |
| `/pages-detail/review-list` | 全部评价 | 评价列表（举报用 useReport） |

### 分包 pages-user（3 页）
| 路径 | 页面 | 说明 |
|---|---|---|
| `/pages-user/publish-content` | 发布内容 | 发动态/评价（编辑态回填评分） |
| `/pages-user/my-moments` | 我发布的 | 我的动态管理 |
| `/pages-user/profile-edit` | 个人信息编辑 | 昵称/头像 |

## 4. 组件体系

### 基础组件
| 组件 | 用途 |
|---|---|
| `Header` | 页面顶部栏（品牌色表面、返回、标题） |
| `IconSvg` | SVG 图标（data-uri，色值走 tokens.ts） |
| `EmptyState` | 空态占位 |
| `WaterfallList` | 双列瀑布流（key 仅用稳定 id） |
| `FilterBar` | 首页品类滚轮筛选 |
| `HomeFeed` | 首页内容流 |
| `BroadcastBar` | 广播 ticker |
| `ReportModal` | 举报弹窗（逻辑收敛到 `useReport` hook） |

### 交互规范
- **按压态**：卡片/按钮用 `pressed` class + `@touchstart/@touchend`（activity 等页），禁用动效偏好时 `reduceMotion` 关闭动画
- **toast/弹窗**：统一 `uni.showToast`（icon none 用统一文案）
- **表单校验**：`fieldErrors` 记录，`input-error` 高亮，失焦清除
- **reduced-motion**：动效组件尊重系统减少动效设置

## 5. 一致性红线（视觉相关）
1. **禁止裸色值**：组件一律 `var(--xxx)`；例外（已注释）：scroll-view 背景 `#F5F5F7` 兜底、`swiper` 原生 indicator 色、`uni.showModal` confirmColor
2. **金额显示**：后端返回「分」，前端 api 层 `/100` 转「元」展示，禁止前端再次换算
3. **图标缺失**：`IconSvg` 回退 `ICONS.empty` 中性占位，禁止破图
4. **新增颜色**：改 `App.vue` token + `tokens.ts`（两处），不新增裸值

## 6. 已知待优化
- `webview/index.vue` progressbar `#9B2A1D`、`find/index.vue` confirmColor `#FF3B30` 为原生属性裸 hex（var 不支持），已注释登记；若后续平台支持 var 再收敛
- 约 200 行 touch 按压态样板重复（activity 等页），建议后续抽 `<PressCard>` 公共组件
