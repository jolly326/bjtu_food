# task-03 · 食堂详情简化（hero 信息卡 + 档口瀑布流）

> 文档性质：技术负责人派工任务（已完成项归档）。
> 权威顺序：`docs/project_spec.md` §4 / §4.9 > `docs/mini-app-ui/canteen.md` > 本任务 > 代码现状。

## 目标
按用户拍板「食堂详情简化」重构：食堂详情收敛为 **hero 信息卡 + 档口单列瀑布流**，移除独立评价大区块（评价改为详情内联展示，不跳 review-list）。

## 状态
✅ **已完成**（commit **ea70d9f**「refactor(canteen): 食堂详情简化为 hero 信息卡 + 档口瀑布流，移除评价区块」）。

## 完成内容
- 移除 `canteen.vue` 的评价大区块（原「用户评价 + 查看全部评价」整块），评价改为详情内联（dish/stall/canteen 三详情页均内联展示）。
- 档口列表用 `WaterfallList single type="stall"`（内部 `StallCardSingle`）+ `@stall-click`，禁具名 slot（修 P0 V2，§4.9 红线）。
- 保留 `ApplySheet`（entity-type CANTEEN，反馈/申请调整）弱化入口。

## 验收
- [x] 食堂详情 = 图集 + hero 信息卡（图/名/简介/位置）+ 档口单列瀑布流；真机无 slot 空白。
- [x] 移除评价大区块，无 `review-list` 跳转残留（`review-list` 页已整体下线）。
- [x] 档口卡 tap → `stall` 详情，`navParams` 传档口名/食堂。

## 依赖
- 依赖 task-01（WaterfallList/StallCardSingle/CardSection/SectionTitle/EmptyState/ApplySheet）。
