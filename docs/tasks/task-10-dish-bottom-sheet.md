# task-10 · dish 菜品详情改底部弹层（页面清单评议 → 决议：独立页 → 底部 sheet）

> 文档性质：技术负责人派工任务（**✅ 已完成，commit 9537969**）。
> 权威顺序：`docs/project_spec.md` §4 / §4.9（Sheet 规范、底部避让、动效、Token）> `docs/mini-app-ui/dish.md`（已标注弹层化）> 本任务 > 代码现状。
> 关联：原 task-14 W9 的演化（评价入口/爱心喜欢已落地，本任务改交互载体为底部弹层）。

## 背景 / 决议
页面清单评议决议：菜品详情从**独立页** `pages-detail/dish` 改为**底部弹出 sheet**（App 内轻量查看菜品，不整页跳转）。入参 `query` → 组件 `prop`、`onLoad` → `watch`，复用 `ApplySheet` 抽屉动画范式。

## 目标
把菜品详情承载方式改为底部弹层，保持既有信息（图集/名称/价格/标签/评分/菜品信息卡/评价/申请下架）与交互（爱心喜欢/分享/去档口/写评价）不变，仅换交互载体。

## 关键改造点
1. **入参方式**：独立页 `onLoad(query)` 读 `dishId` → 改为组件 `props.dishId`（或 `props.dish`），数据加载由 `onLoad` 改 `watch(() => props.dishId, ...)`。
2. **弹层范式**：复用 `ApplySheet` 的抽屉动画范式（spring `0.8/0.3` 抽屉缓动 `cubic-bezier(0.32,0.72,0,1)`、`ic-close` 关闭、下拉关闭手势（仅向下、阈值~120px）、`prefers-reduced-motion` 交叉淡入降级）。
3. **容器**：内容放进底部 sheet 滚动区（`scroll-view`）；底部操作栏（喜欢/写评价/去档口）留在 sheet 底部，用 `--action-bar-height` + 安全区避让。
4. **调用方改造**：凡从 `DishCard`/`WaterfallList`/`find`/`home`/`stall` 等点击菜品详情的位置，从 `navigateTo('/pages/pages-detail/dish?dishId=')` 改为打开该 sheet 组件（传 `dishId`）。
5. **保留**：图集 `ImageSwiper`、价格/标签/评分、菜品信息卡（位置与营业/属性/介绍）、评价 `ReviewItem`、申请下架 `ApplySheet`、分享面板、爱心喜欢乐观更新。

## 涉及文件
- `frontend/src/pages/pages-detail/dish.vue`（改造为弹层内容，或抽 `DishDetailSheet.vue` 组件）
- `frontend/src/pages.json`：`pages-detail` 分包减少 dish 页（5 → 4）→ 主包 + 分页总数变化（17 → 16，与 task-07 webview 移除合计 15）
- 调用方：`DishCard`/`WaterfallList`/`find`/`home`/`stall`/`community`/`moment` 关联等所有进入 dish 详情的入口
- `docs/mini-app-ui/dish.md`（同步 sheet 化）、`project_spec.md` §2.1（页面数）
- 复用 `ApplySheet` 抽屉动画 / `useSheet`（若抽统一）相关工具

## 验收标准
- [ ] 菜品详情以底部 sheet 弹出，抽屉缓动/下拉关闭手势/reduced-motion 降级符合 §4.4/§4.9 Sheet 规范。
- [ ] `dishId` 经 prop 传入，`watch` 驱动加载（无 `onLoad` 残留）；打开不同菜品复用同一 sheet 正确刷新。
- [ ] 既有信息与交互（图集/价格折扣/评价爱心喜欢/分享/申请下架/去档口/写评价）全部保留。
- [ ] 所有进入菜品详情的入口统一改为 sheet 打开，无 `navigateTo('/pages/pages-detail/dish')` 残留。
- [ ] `pages.json` 移除 dish 独立页；`project_spec.md` §2.1 / mini-app-ui README 页面数同步。
- [ ] 小程序 dev 编译通过，无控制台报错。

## 依赖
- task-01（ApplySheet/ReviewItem/ImageSwiper/SegmentTabs/Token/Sheet 规范）。
- 与 task-07（webview 移除）合计主包+分包页面数由 17 → 15，注意同步 `project_spec.md` §2.1。
