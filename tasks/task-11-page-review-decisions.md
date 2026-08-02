# task-11 · 页面清单评议决议记录（汇总）

> 文档性质：技术负责人派工任务（决策记录归档）。
> 用途：把页面清单评议产生的 4 个独立决议 + 被砍页决定做一次性「决定记录」汇总，作为各新任务的索引与验收锚点。

## 状态
✅ **已完成**（决策已落库到各独立 task-07/08/09/10；本文件为汇总索引）。

## 页面清单评议决议（4 个独立新任务）
| 决议 | 落点 task | 一句话 |
|---|---|---|
| **移除 webview 页** | task-07 | 业务只跳外部公众号文章/H5，砍 webview 路由与页，Banner/广播 URL 跳转改复制链接 |
| **moment 详情后端排查** | task-08 | `GET /moments/{id}` 返回非 200/404/401，需后端确认数据存在性与审核可见性，产出排查清单+验收 |
| **stall 三 tab 重构** | task-09 | 横幅 + hero 卡 + 底部 tabBar（菜品/评价/档口介绍）；菜品 tab 左分类筛选 + 右瀑布流，美团外卖式 |
| **dish 改底部弹层** | task-10 | 独立页 → 底部 sheet；入参 query→prop、onLoad→watch、复用 ApplySheet 抽屉范式 |

## 被砍页最终决定（并入 task-02，已落地）
- `my-publish` / `my-submissions` → 合并进 `messages-services`（「我的发布与贡献」聚合页）。
- `notify`（消息中心）→ 消息并入「我的」(profile) 区块，不再独立路由。
- `review-list`（全部评价）→ 取消独立跳转，评价改详情页内联展示。
- `contact` → 并入 `feedback`（复用 FeedbackForm）。

## 页面总数演算
- 基线 17 页（主包 8 + pages-detail 5 + pages-user 4）。
- **task-07 移除 webview**（主包 8→7）→ 16 页。
- **task-10 dish 改底部弹层**（pages-detail 5→4）→ 15 页。
- 注：dish 弹层化与 webview 移除若并行，最终页面数为 **15**；各 task 须同步 `project_spec.md` §2.1 与 mini-app-ui README 页面数，避免文档漂移。

## 验收
- [ ] 4 个决议均有独立 task 承接并含验收标准。
- [ ] 被砍页决定已在 task-02 归档（commit 060a21f/fa37ab5/295e4bc）。
- [ ] 页面总数变化（17→15）已同步到 project_spec §2.1 与 mini-app-ui README。
