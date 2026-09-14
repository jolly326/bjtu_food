# Loop 状态看板（STATUS）

- **当前轮次**：ITER-003（团队缺陷与不一致排查，已产出报告）
- **上一轮结论**：ITER-002 二轮对齐（4 项决议落地）
- **本轮报告**：`docs/loop/audit/ITER-003-AUDIT.md`（汇总）＋ `audit/backend.md`＋`audit/frontend.md`（明细）
- **问题渠道**：`docs/loop/QUESTIONS.md`（开放：Q-004~Q-008、Q-013~Q-028）

## ITER-003 排查汇总
| 级别 | 后端 | 前端 | 合计 |
|---|---|---|---|
| 🔴 | 3 | 2 | 5 |
| 🟠 | 9 | 9 | 18 |
| 🔵 | 6 | 5 | 11 |

**放行结论：不放行**（5 个 🔴）。

## 批次进度
- ✅ **A 批次（本轮已清零）**：RB1 删 `likeReview` 死方法｜RB2 删 `getHotDishes()` 无参重载｜RF1 删 web 401 死跳 `/login`｜RB11 清理悬空技术债注释。验证：`mvn compile` EXIT=0、`npm run build` EXIT=0。
- ⏳ **B 批次（下一轮）**：RB3 `handler_id` 运行时验证与处置｜RF2 `adminId` 自保护死链｜RF7 `/dashboard/account` 死链｜RF4+RF5 client 组件/composable 下沉｜RF3 占位文件删除（需用户批准删除权限）｜RB7/RB8/RB9/RB10/RB12/RB14/RB15/RB17 后端清理与契约回写
- ⏳ **C 批次（待拍板）**：Q-023~Q-028
- ⏳ **D 批次（排期）**：11 条 🔵 优化（热度公式抽常量、反馈状态映射集中、未使用 import、幽灵接口注释等）

## 待用户决策（阻塞）
| 事项 | 阻塞什么 |
|---|---|
| Q-004 重新部署云托管 | 后台全部功能（上一版早于今日修复） |
| Q-005 后台上传 31 道菜首图 | 小程序菜品图（上传鉴权已修，待部署生效） |
| Q-023 管理端操作人身份 | 审计链（handler_id / 操作日志）与自保护分支的去留 |
| Q-024 / Q-025 / Q-026 | 账号设置入口、review.tags 列、上新/促销等板块存废 |
| Q-006 小程序 downloadFile 域名 | 正式版图片显示 |

## 循环触发
- 手动：用户说「继续下一轮」→ 读本文件接续
- 自动（可选）：`FREQ=DAILY;BYHOUR=2;BYMINUTE=0`
