# task-02 · 小程序页面架构落位（17 页 + pages-user 分包 + 发布方案 Y）

> 文档性质：技术负责人派工任务（已完成项归档）。
> 权威顺序：`docs/project_spec.md` §2.1（页面架构，2026-08-02 已拍板）> 本任务 > 代码现状。

## 目标
把已拍板的 **17 页小程序架构**落到 `frontend/src/pages.json` 与代码：主包 8 + 分包 `pages-detail` 5 + 分包 `pages-user` 4；落实发布入口方案 Y（「我要贡献」弹层分流）、`messages-services` 更名、清理被砍页。

## 状态
✅ **已完成**（commit **060a21f**「落实页面清单编码」+ **fa37ab5**「落实页面清单」+ **382de41**「pages-user 分包路径补 /index」）。

## 17 页架构（已拍板，与 pages.json 严格一致）
- **主包（8）**：`home` 首页 / `find` 发现 / `profile` 我的 / `community` 动态 / `webview` 外部链接 / `settings` 设置 / `feedback` 意见反馈 / `messages-services` 我的发布与贡献（路径 `pages/profile/messages-services/index`）。
- **分包 `pages-detail`（5）**：`canteen` 食堂详情 / `dish` 菜品详情 / `moment` 动态详情 / `stall` 档口详情 / `review` 发表评价。
- **分包 `pages-user`（4）**：`publish-moment` 发布动态 / `my-moments` 我的动态 / `publish-dish` 发布菜品 / `submit-stall` 提交档口·食堂。

## 已完成的子项
- 页面迁移：`my-moments`/`publish-moment`/`publish-dish`/`submit-stall` 下沉 `pages-user` 分包（fa37ab5 / 060a21f）。
- `messages-services` 更名：`my-publish` + `my-submissions` 合并进 `messages-services`（「我的发布与贡献」唯一聚合页，吸收两者内容与入口）。
- 「我要贡献」弹层：`ContributeSheet` 落地（发菜品 → `publish-dish` / 提交档口 → `submit-stall`，仅分流不内联表单字段）。
- 清理被砍页：`my-publish`/`my-submissions`/`notify`/`review-list`/`contact` 已从 pages.json 移除；遗留 doc 已删除（commit **295e4bc**）。
- TabBar 固定 4 Tab（home / find / community / profile）。

## 验收
- [x] pages.json 与 §2.1 17 页清单严格一致。
- [x] 发布类入口收敛（方案 Y）：`publish-moment` 主入口 + `ContributeSheet` 分流，无孤儿路由/死链。
- [x] `messages-services` 聚合页呈现「我的发布」+「我的贡献」。

## 依赖
- 依赖 task-01 基础组件（`ContributeSheet` 等）。
