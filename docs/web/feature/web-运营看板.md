# 运营看板（B-02）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)
>
> ⚠️ 本管理后台（Web 后台）已软冻结，待后期整体重构移除（详见 [管理后台功能总览](./README.md) 顶部声明）。

## 介绍

管理后台**登录后首屏**，回答一个问题：**「今天有哪些事等着我处理」**。它是**聚合只读视图**，不承载任何写操作；所有写动作跳转到对应治理功能（B-09 / B-10 / B-11）。

看板由三组指标构成：

1. **待办（最优先）**：待处理意见反馈数、待处理举报数、待处理纠错数 —— 每项都是可点击入口，直达对应列表并预置「待处理」筛选。
2. **菜品主数据健康度**：在售菜品数、**有图率**、**有评价率** —— 用于发现「信息不完整」的菜品（配图与评价是学生决策的主要依据，缺失即低质量）。
3. **用户与内容概况**：用户总数、今日新增用户、**近 7 日活跃用户（WAU）**、评价总数、全站平均分。

**口径边界（遵守产品定型）**：
- **不做**「售罄 / 今日供应」等即时经营状态（一期内不做，属演进预留）。
- **不展示**任何距离 / 定位指标（公开侧无坐标，见 B-03）。
- 北极星为**周活 / 留存**，故活跃口径固定为 **近 7 日**，不提供任意区间切换。

**性能口径**：全部指标走**单条聚合查询**（一次 `COUNT` / `AVG` 组合），禁止逐项循环查库；看板数据允许 60 秒缓存，避免每次进首页都打全表。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/dashboard` | 🔑 管理员 | 运营概览聚合指标（只读，无入参） |

## 字段

### 响应（`data` = `DashboardVO`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `pendingFeedbackCount` | number | 待处理意见反馈数（`user_feedback.status='pending'` 且 `type` 为纯反馈三类） |
| `pendingReportCount` | number | 待处理举报数（`user_feedback.status='pending'` 且 `type='report'`） |
| `pendingCorrectionCount` | number | 待处理菜品纠错数（`dish_correction.status='pending'`） |
| `dishOnSaleCount` | number | 在售菜品数（`dish.status='on'`） |
| `dishWithImageCount` | number | 有配图的在售菜品数（`images` 非空） |
| `dishWithReviewCount` | number | 有可见评价的在售菜品数（`rating_count > 0`） |
| `userTotal` | number | 用户总数（不含已注销） |
| `newUserToday` | number | 今日新增用户数 |
| `activeUser7d` | number | 近 7 日活跃用户数（WAU，口径见下） |
| `reviewTotal` | number | 可见评价总数（`is_hidden=0`） |
| `avgRating` | number \| null | 全站平均评分（无评价为 `null`） |

> **活跃口径**：近 7 日活跃 = 近 7 日有**登录行为**的去重用户数。当前无行为日志表，故一期以 `user.last_login_at` 判定；若后续引入行为埋点再改口径（口径变更须同步修订本文档）。
> **有图率 / 有评价率**由前端用 `dishWithImageCount / dishOnSaleCount`、`dishWithReviewCount / dishOnSaleCount` 现场计算，**后端不下发比率字段**（避免同一事实两份真源）。

## 数据（读取）

| 表 | 读什么 | 中文解释 |
|---|---|---|
| `user_feedback` | 按 `status` + `type` 分别计数 | 待办：反馈 / 举报 |
| `dish_correction` | `status='pending'` 计数 | 待办：纠错 |
| `dish` | `status='on'` 计数、有图计数、`rating_count>0` 计数 | 主数据健康度 |
| `user` | 总数、今日新增、近 7 日活跃（按 `last_login_at`） | 用户概况 |
| `review` | `is_hidden=0` 计数与 `AVG(rating)` | 内容概况 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

**本功能为新增**：现有管理端无看板聚合端点，本接口需**新建**；`DashboardVO` 与聚合查询一并新增。
