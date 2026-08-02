# task-09 · stall 档口详情三 tab 重构（页面清单评议 → 决议：美团外卖式）

> 文档性质：技术负责人派工任务（新，待办）。
> 权威顺序：`docs/project_spec.md` §4 / §4.9（含底部避让、Sheet、Token、WaterfallList 禁 slot）> `docs/mini-app-ui/stall.md`（当前实现）> 本任务 > 代码现状。
> 关联：原 task-14 W11 的演化（stall 结构化 + 圆角方图已落地，本任务在三 tab 重构基础上推进）。

## 背景 / 决议
页面清单评议决议：stall 档口详情改为**美团外卖式三 tab 结构**——顶部横幅 + 档口 hero 卡 + 底部固定 tabBar（菜品 / 评价 / 档口介绍）。菜品 tab 左侧分类筛选（`DISH_CATEGORIES` 前端常量 + tags 兜底全部）、右侧菜品瀑布流（`WaterfallList` 单列 `stall`）。

## 目标
把当前 `pages-detail/stall.vue`（图集 + 档口信息卡 + 全部菜品折叠 + 评价预览 单页纵向）重构为「hero + 底部 tabBar 三段」信息架构。

## 布局结构（目标）
1. **横幅 + hero 卡**（顶部，滚动区）：`ImageSwiper` 图集 + 档口名/位置/星级/简介/营业时间 hero 卡（`CardSection`/`SectionTitle`），含弱化反馈入口（`ApplySheet` STALL）。
2. **底部固定 tabBar**（3 tab：菜品 / 评价 / 档口介绍）：
   - 用统一 `--action-bar-height` + `env(safe-area-inset-bottom)` 避让（§4.9 底部固定栏红线，scroll-wrap 加 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`）。
   - tab 切换用 `SegmentTabs`（数据驱动，禁 v-if 链）或等价 tab 状态；选中高亮 scale 用登记 token（`--tab-active-scale`）。
3. **菜品 tab**：左侧纵向分类筛选（`DISH_CATEGORIES` 前端常量，`frontend/src/constants/categories.ts`；选择「全部」+ 各类；后端若返回 tags 则 tags 兜底全部）→ 右侧 `WaterfallList single type="stall"` 菜品瀑布流（内部 `StallDishRow`/`DishCard`，禁具名 slot，`@dish-click` 上抛 → dish 详情）。
4. **评价 tab**：内联 `ReviewItem` 列表（前 N 条 + 查看全部，或分页），互动=爱心喜欢。
5. **档口介绍 tab**：营业时间/位置/简介/联系电话（若有）。

## 涉及文件
- `frontend/src/pages/pages-detail/stall.vue`（重构）
- `frontend/src/constants/categories.ts`（复用 `DISH_CATEGORIES`，不新增）
- 复用组件：`ImageSwiper`/`CardSection`/`SectionTitle`/`WaterfallList`/`StallDishRow`/`ReviewItem`/`SegmentTabs`/`EmptyState`/`ApplySheet`/`IconSvg`（location/star-filled/arrow 等）
- `docs/mini-app-ui/stall.md`（同步三 tab 结构）
- 后端：菜品列表需支持按分类筛选（`GET /dishes?stallId=&tag=`，task-06 §12 分类/口味筛选联动）；若 `DISH_CATEGORIES` 与后端 tag 需对齐则登记缺口。

## 验收标准
- [ ] 顶部 hero 卡 + 底部固定 3 tabBar，切换顺滑、无 `v-if` 链、无横向溢出。
- [ ] 菜品 tab：左侧分类筛选（`DISH_CATEGORIES` 全部+各类，tags 兜底），右侧 `WaterfallList` 单列菜品瀑布流，点击 → dish 详情。
- [ ] 评价 tab：`ReviewItem` 内联，爱心喜欢互动，三态齐备。
- [ ] 档口介绍 tab：信息分组清晰。
- [ ] 底部 tabBar 用 `--action-bar-height` + 安全区避让，内容不被遮挡；`@tap` 统一、按压 `scale(var(--press-scale))`、图标走 `IconSvg`、无裸 hex。
- [ ] 与 task-06 §12（菜品按分类筛选）联动，后端按需补 `tag`/`spiceLevel` 过滤。
- [ ] 小程序 dev 编译通过。

## 依赖
- task-01（WaterfallList/StallDishRow/ReviewItem/SegmentTabs/ApplySheet/Token）。
- task-06 §12（后端分类/口味筛选，若需）。
