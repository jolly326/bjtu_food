# tasks/ · 开发任务体系（2026-08-02 全部重写）

> 任务目录唯一索引。**编号已从 task-01 重新编排**，与旧 task-01~15（已删）不再混淆。
> 权威顺序：`docs/project_spec.md`（唯一权威）> 本目录各 task > `docs/mini-app-ui/`（逐页 UI 文档）。
> 未完成工作（原 task-12 契约缺口 / task-13 UI 定样 / task-14 UI 整改 / task-15 emoji→图标）的要点已**全部吸收**进新体系，非简单丢弃。

## 任务清单（按执行依赖排序）

| 编号 | 任务 | 状态 | 一句话用途 |
|---|---|---|---|
| [task-01](task-01-miniapp-foundation.md) | 小程序端基础设施 | ✅ 已完成 | 统一 request / UI Token / 动效工具 / 基础组件，其余任务只复用不新造 |
| [task-02](task-02-miniapp-page-architecture.md) | 小程序页面架构落位（17 页） | ✅ 已完成（060a21f / fa37ab5 / 382de41） | pages-user 分包、messages-services 更名、ContributeSheet、清理被砍页 |
| [task-03](task-03-miniapp-canteen-simplify.md) | 食堂详情简化 | ✅ 已完成（ea70d9f） | hero 信息卡 + 档口瀑布流，移除评价大区块 |
| [task-04](task-04-miniapp-emoji-to-icon.md) | 图标 emoji→SVG 迁移 | ✅ 已完成 | 46 SVG + IconSvg，零 emoji 图标残留（吸收原 task-15） |
| [task-05](task-05-miniapp-ui-rework.md) | 小程序 UI 整改 | 🔄 进行中 | 吸收原 task-14 W1~W16；多数已落地，剩余项转 task-07/08/09/10 |
| [task-06](task-06-contract-gaps.md) | 契约缺口补齐 | 🔄 待办/部分已拍板 | 吸收原 task-12.1~12.13 + CONTRACT_IMPACT 缺口（apply_action/评论点赞/注销/favorites 删除等） |
| [task-07](task-07-remove-webview.md) | 移除 webview 页 | ✅ 已完成（7dcba46） | 业务只跳公众号文章，砍 webview，URL 跳转改复制链接 |
| [task-08](task-08-moment-detail-investigation.md) | moment 详情后端排查 | ⏳ 待办 | GET /moments/{id} 非 200/404/401，后端确认数据/审核可见性 |
| [task-09](task-09-stall-three-tab-rework.md) | stall 档口三 tab 重构 | ✅ 已完成（f0b62ff） | 横幅 + hero + 底部 tabBar（菜品/评价/介绍），美团外卖式 |
| [task-10](task-10-dish-bottom-sheet.md) | dish 改底部弹层 | ✅ 已完成（9537969） | 独立页 → 底部 sheet，query→prop、onLoad→watch，复用 ApplySheet 范式 |
| [task-11](task-11-page-review-decisions.md) | 页面清单评议决议记录 | ✅ 已完成 | 4 个独立决议 + 被砍页决定的汇总索引 |

## 页面总数演算
- 基线 17 页（主包 8 + pages-detail 5 + pages-user 4）。
- task-07 移除 webview（主包→7）、task-10 dish 弹层化（pages-detail→4）→ **最终 15 页**。
- 各 task 须同步 `project_spec.md` §2.1 与 `docs/mini-app-ui/README.md` 页面数，防文档漂移。

## 已删除的旧文件（内容已吸收）
- `CONTRACT_IMPACT.md`（活缺口 → 并入 task-06，其中 §A 缺口/§C 决策/§D 影响面已整合）
- `PAGE_PLAN_PROPOSAL.md`（过期提案，直接删除）
- `task-12-miniapp-web-scope.md`（契约缺口 → task-06）
- `task-13-miniapp-ui-design.md`（UI 定样 → task-05 门禁基线）
- `task-14-miniapp-ui-implementation.md`（UI 整改 W1~W16 → task-05）
- `task-15-emoji-to-icon.md`（emoji→图标 → task-04）
