# 学生端 UI 总览

> 本目录是**学生端（微信小程序）页面 UI 的唯一真源**（视觉 / 交互 / 间距 / 动效）。
> 业务规则见 [`func/client/`](../../func/client/README.md)；接口与字段见 [`api/`](../../api/README.md)。
>
> 🔴 **风格宪法：[Q版风格指南.md](./Q版风格指南.md)** —— 新增 / 改动 UI 前先读它（风格内核、色比、字体、留白、图标、反例）。

## 页面清单

| 页面 | 设计稿 | 归属功能 |
|---|---|---|
| 美食地图 | [美食地图.md](./美食地图.md) | [A1 美食地图](../../func/client/A-浏览与发现/A1-美食地图.md) · [B1 点赞反馈](../../func/client/B-轻量反馈/B1-点赞反馈.md) · [B2 菜品纠错](../../func/client/B-轻量反馈/B2-菜品纠错.md) |

## 导航结构

🔴 **无 TabBar、无全局 AppHeader、无页标题** —— 全站仅一个全屏沉浸式「美食地图」页（见 [美食地图.md](./美食地图.md)）；所有导航 chrome 去除，菜品大图铺满视口。

- **无二级页面** —— 没有详情页 / 路由页；单品信息经**底部弹窗（Sheet）**承载（见 [美食地图.md](./美食地图.md) §3）。
- 顶部状态栏避让、底部安全区由画布自身内距承担（见 [美食地图.md](./美食地图.md) §1）。

## 页面骨架（单页）

| 区块 | 口径 |
|---|---|
| 顶部安全区 | `max(statusBarHeight, env(safe-area-inset-top))`（画布内容起始避让） |
| 主内容区 | 全屏 `FoodMap` 画布（`flex: 1` + `min-height: 0`） |
| 底部弹窗层 | `DishSheet`（点磁贴唤起，见 [美食地图.md](./美食地图.md) §3） |
| 页面底色 | `--bg-page` = **`#FFFAF3`**（奶米纸色，🔴 全站统一；单页沉浸） |

> 🔴 **视觉语言（Q 版动森（圆润厚实 · 轻拟物））见 [公共组件与形态基线.md](./公共组件与形态基线.md) §0** —— 所有页面 / 组件严格遵循；页面级视觉取值见 [设计变量.md](./设计变量.md)，跨页形态口径与组件规格见 [公共组件与形态基线.md](./公共组件与形态基线.md)。**页面稿只写「本页用法」，不复述基线规格。**

---

## 客户端架构说明（优化版）

- **单页面**：仅 `pages/map/index.vue`
- **组件拆分**：
  - `components/`：AppIcon、BaseSheet、ImagePlaceholder、PageLoading、RetryBlock 等跨页公共组件
  - `pages/map/components/DishInfo.vue`：菜品信息、点赞、纠错入口
  - `pages/map/components/FeedbackPanel.vue`：纠错选项分页
  - `pages/map/components/ConfirmBubble.vue`：轻确认气泡
  - `pages/map/components/SheetToast.vue`：成功提示
  - `pages/map/components/FoodMap.vue`：画布、规整网格、缩放、点击判定
  - `pages/map/components/SheetPill.vue`：复用胶囊按钮
  - `pages/map/components/DishSheet.vue`：组合器（只组装子组件，不持有业务状态）
- **目录结构**：`components/`（跨页公共件）· `pages/map/components/`（页面私有件）· `theme/`（设计变量与图标）· `hooks/` · `utils/` · `constants.ts` · `types/generated/api.d.ts`
- **架构原则**：单一责任、魔法数字集中于 `constants.ts`、动画 / 定时器统一清理、契约真源（docs 优先）
