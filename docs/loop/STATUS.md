# Loop 状态看板（STATUS）

- **当前轮次**：ITER-003（团队缺陷与不一致排查）→ A/B 批次已闭环
- **报告**：`docs/loop/audit/ITER-003-AUDIT.md`（汇总）＋ `backend.md` / `frontend.md`（明细）
- **验收标准**：`docs/loop/launch-checklist.md`（三项并行）
- **问题渠道**：`docs/loop/QUESTIONS.md`

## ITER-003 批次进度

| 批次 | 内容 | 状态 |
|---|---|---|
| **A** | 死方法 `likeReview` / `getHotDishes()`、web 401 死跳 `/login`、悬空注释 | ✅ 已闭环 |
| **B** | 命名统一（`StallAvgRatingDTO`→`VO`）、死字段 `hasSensitive`、常量单一真源（`AuditStatusConst`/`SecStateConst`）、组件与 composable 下沉（4 个）、`PUT /admin/feedbacks/{id}` 收敛为仅 body、删中转文件与孤立 store、物理删除 5 个死文件、修复 `HistoryService` 缺失 import | ✅ 已闭环 |
| **C** | 需用户拍板项 | ✅ 全部拍板（§7.9~§7.12） |
| **D** | 蓝色优化项（热度公式抽常量、`user` 保留字转义评估等） | ⏳ 剩余少量 |

> **教训（已记入流程）**：`mvn compile` 增量编译会被旧 class 掩盖缺失 import，**门禁须用 `mvn clean compile`**。

## 待用户执行（5 项，见 launch-checklist D 段）

| # | 事项 | 影响 |
|---|---|---|
| D1 | 云托管按 `main`（`d75b981`）重新部署 | 后台可用（`ADMIN_TOKEN`/COS/上传鉴权修复才生效） |
| D2 | 上传 31 张菜品首图（当前 31/31 缺失）+ 按 `dish-proofread-checklist.md` 逐条校对 | 小程序菜品图与数据质量 |
| D3 | 小程序 downloadFile 域名加 COS 域名 | 正式版图片显示 |
| D4 | 执行 DB 迁移（`serve_period`/`limited`/`review.tags` 三列删除，脚本幂等） | 清除孤儿列 |
| D5 | （已完成）死文件删除授权 | ✅ 5 个文件已物理删除 |

## 已闭环决策（spec §7.1 ~ §7.12）

首发校本部 / 定位找吃的 / 投稿合并进 feedback / 先发后审+举报人工巡查 / 评价准入双约束（反馈免认证）/ 收藏不存在 / 管理端菜品直接 approved / 风味菜系定型 / isNew·餐段·限量下线 / 5 个零消费入口下线 / 操作人身份降级 / 评价 tags 删列 / 账号设置入口删除 / 分页契约收尾 / 举报去重 / 验收三项并行 / 不做只读口令 / 下架菜评价保留 / 不做「喜欢」计数 / 注销色保留主色

## 下一步候选

1. **D 批次**：热度公式抽常量、`user` 表名转义评估、`FeedbackView` 等剩余蓝色项
2. **二期**：反馈「一键转菜品」（§7.3/§7.7）
3. **UI 文案**：反馈入口「审核后上架」与「48 小时内处理」两处文案落位（属 UI 评审范围，待设计师确认落点）

## 循环触发
- 手动：用户说「继续下一轮」→ 读本文件接续
- 自动（可选）：`FREQ=DAILY;BYHOUR=2;BYMINUTE=0`
