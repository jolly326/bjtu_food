# task-04 · 小程序图标 emoji→SVG 矢量迁移

> 文档性质：技术负责人派工任务（已完成项归档）。
> 权威顺序：`docs/mini-app-ui/icons.md`（图标映射权威源）> `docs/project_spec.md` §4.2 / §4.9（SVG 图标红线）> 本任务 > 代码现状。
> 背景：原 task-15（emoji→图标）已基本收口，此处归档其要点并复核剩余遗漏。

## 目标
将所有以 emoji 字符充当图标/语义的实现替换为 `frontend/src/assets/icons` 下 SVG 矢量图标引用，零 emoji 图标残留、风格一致、语义唯一（ic-heart=喜欢、ic-thumb=有用/点赞，互不混用）。

## 状态
✅ **已完成**（图标资源与统一组件已落地；全端经 `<IconSvg name="…" />` 引用）。

## 完成内容
- 资源层：`frontend/src/assets/icons` 现有 **46 个 SVG**（ic-search/location/heart/heart-filled/thumb/fire/clock/lightbulb/share/comment/plus/report/arrow/back/bell/check/chili/close/contact/delete/dish/drink/edit/empty/fastfood/filter/home/image/list/lock/logo/mail/malatang/midnight/noodle/portion/price/profile/rice/search/settings/snack/stall/star/star-filled/user 等）。
- 统一图标组件：`IconSvg.vue`，全端经此组件引用；`ImageFallback.vue` 图破兜底；`empty` 中性占位键（缺失/未注册键渲染 `empty`，非 `dish` 碗）。
- 语义唯一：`heart`=喜欢、`thumb`=有用/点赞、`star`=评分，三者互不混用（10 轮迭代已稳定）。
- `ic-lightbulb.svg`（猜你喜欢）经 Iconfont MCP 新增，替换 💡。
- 全端 `grep` emoji（🔍📍❤️👍🔥⏰💡📤💬➕⚠️）= **0 处**（pages/components 均无残留）。

## 验收（原 task-15 六条）
- [x] 全仓 `frontend/` 检索不到 11 个 emoji 作为图标/语义残留。
- [x] 被替换位置均经统一图标组件引用 `assets/icons` 对应 SVG，随主题变色。
- [x] `ic-lightbulb` 在首页「猜你喜欢」/推荐位正确显示。
- [x] 交互态正确：喜欢/有用乐观更新后图标高亮态符合约定。
- [x] 未引入新未登记图标或 emoji 占位。
- [x] 小程序 dev 编译通过，无控制台报错。

## 关联
- §4.2 图标映射表、§4.9 emoji/SVG 红线、`docs/mini-app-ui/icons.md`。
- 依赖 task-01（IconSvg 组件、Token）。
