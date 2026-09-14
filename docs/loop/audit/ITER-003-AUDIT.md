# ITER-003 · 团队缺陷与不一致排查报告

- **时间**：2026-09-14
- **主导**：产品经理（主 agent 代行）
- **参与**：后端审计员（质量把控工程师）、前端审计员（质量把控工程师）、PM 自动化扫描
- **排查目标**：识别并记录代码中的**缺陷、逻辑不一致、命名规范不统一、接口定义不匹配**
- **方法**：全仓 grep + 逐文件读证 + **线上 MySQL 实测**（非 grep 猜测）+ 前后端路由逐条 diff
- **明细报告**：`docs/loop/audit/backend.md`（18 条）、`docs/loop/audit/frontend.md`（16 条）

---

## 一、汇总

| 级别 | 后端 | 前端 | 合计 | 含义 |
|---|---|---|---|---|
| 🔴 | 3 | 2 | **5** | 阻断 / 正确性缺陷 |
| 🟠 | 9 | 9 | **18** | 不一致或规范偏离 |
| 🔵 | 6 | 5 | **11** | 优化 |
| **合计** | **18** | **16** | **34** | — |

**放行结论：不放行。** 存在 5 个 🔴，其中 3 个为机械性死代码/死链（本轮清零），2 个需运行时验证或产品拍板。

**已扫清的大面积风险（留档）**：错误码值域合规（仅 400/401/403/4031/500）｜无 `is_new` 残留｜Mapper 列引用线上实跑全通过｜无直连 ALTER｜金额统一「分」｜角色仅 STUDENT/ADMIN｜学生端菜品写接口确认下线｜前后端契约无「调用已下线接口」｜UI 红线（@click / 裸 scale / emoji / `../` 逃逸）全过｜pages.json 与 spec §2.1 一致（9 页）。

---

## 二、优先级排序与修复批次

| 批次 | 定义 | 项数 | 时限 |
|---|---|---|---|
| **A 批次** | 机械、零决策、可立即执行 | 4 | **本轮内完成** |
| **B 批次** | 需验证 / 小范围改动 / 契约回写 | 12 | 下一轮内完成 |
| **C 批次** | 需用户拍板或设计师裁决 | 7 | 决策后当轮 |
| **D 批次** | 优化项，排期 | 11 | 两轮内 |

---

## 三、🔴 阻断项（5 条）— 每项含责任人与时限

| ID | 问题 | 位置 | 责任人 | 时限 | 处置 |
|---|---|---|---|---|---|
| **RB1** | `ReviewService.likeReview` 无调用方死方法，且语义与现行 `toggleUseful` **相反**（单向不可取消 vs 幂等可取消） | `ReviewService.java:182-183`；`ReviewServiceImpl.java:394-413` | 后端开发工程师 | **A 批次（本轮）** | **已执行**：物理删除接口+实现 |
| **RB2** | `DishService.getHotDishes()` 无参重载零调用，两条热门路径易误用（无参会绕过 lat/lng 加权） | `DishService.java:41`；`DishServiceImpl.java:73-76` | 后端开发工程师 | **A 批次（本轮）** | **已执行**：删除无参重载（已确认测试零引用） |
| **RB3** | `/admin/**` 无角色闸门 → `handler_id`/`admin_id` 可能恒为 NULL，**审计链断裂** | `FeedbackAdminController.java:62`；`SecurityConfig.java:106` | 后端开发工程师 + 技术负责人 | **B 批次** | 需起服务实测后处置（见待确认 #RB3） |
| **RF1** | web `main.ts` 401 兜底 `router.push('/login')`，但 `/login` 路由**已随去登录体系删除**且无 catch-all → 任何 401 跳空白页 | `web/src/main.ts:20-22`；`router/index.ts:13-28` | Web管理后台开发工程师 | **A 批次（本轮）** | **已执行**：删除该死订阅 |
| **RF2** | web `userStore.adminId` 唯一赋值点 `loadProfile()` **零调用** → `UserView`「禁止操作自己/批量自保护」恒不生效 | `stores/userStore.ts:10,27-34`；`views/user/UserView.vue:84,106` | Web管理后台开发工程师 + 技术负责人 | **B 批次** | 依 Q-023 决策后处置 |

---

## 四、🟠 不一致项（18 条，摘要）

### 契约与文档断层（责任人：技术负责人）
| ID | 问题 | 位置 |
|---|---|---|
| RB4 | `PageResult` 字段为 `{list,total}`，与 spec 权威契约 `{records,total,page,pageSize}` 不符（靠前后端兜底才不炸） | `PageResult.java:35-39` vs spec:215,376 |
| RB5 | `POST /auth/admin/login` 被 3 处注释 + spec §5 白名单宣称存在，实际**不存在** | `SwaggerConfig.java:37`、`SecurityConfig.java:35`、`AuthService.java:14`、spec:214 |
| RB6 | `/admin/**` 无 `ADMIN`/`SUPER_ADMIN` 角色闸门，spec §0.3/§0.4 承诺的分层权限失效 | `SecurityConfig.java:106`；`RoleConst.java:6-8` |
| RB13 | 管理员新增/编辑强制 `approved`，与 spec「提交→pending→审核」表述断层 | `DishServiceImpl.java:354,373` vs spec:60,289 |
| RB15 | 审核状态常量在 `AuditConst`/`DishConst` 重复定义；安全态在 `ReviewServiceImpl`/`SecSuggest` 语义重叠 | 多处 |
| RB17 | `user` 为 MySQL 保留字作表名，未配置全局转义（当前实测正常） | `User.java:16` |

### 数据与实体漂移（责任人：后端开发工程师）
| ID | 问题 | 位置 |
|---|---|---|
| RB7 | `review.tags` 列存在于线上库与 `schema.sql`，实体 `Review` **无该字段**（反向漂移） | `schema.sql:152`；`Review.java` |
| RB8 | `ReviewAdminVO.hasSensitive` **硬编码恒 false**，字段语义空洞 | `ReviewAdminVO.java:53`；`ReviewServiceImpl.java:443` |
| RB9 | `AdminCreateReq` DTO 无调用方（管理员账号体系已下线） | `AdminCreateReq.java:15` |
| RB10 | `StallAvgRatingDTO` 后缀混入 `VO` 家族（同目录其余全 VO） | `StallAvgRatingDTO.java:8` |
| RB11 | 悬空注释「喜欢计数存储方案待架构师评估」无归属无结论 | `DishServiceImpl.java:425` |
| RB12 | `promotion` 标签死查询链，`schema.sql` 自认技术债 | `DishController.java:47-51`；`DishMapper.xml:170-180` |
| RB14 | `ReviewAdminVO` javadoc 称公开接口严禁返回 `secState`，实际已公开返回 | `ReviewAdminVO.java:12-13` vs `ReviewVO.java:52` |

### 前端死代码与规范（责任人：Web / 小程序工程师）
| ID | 端 | 问题 | 位置 |
|---|---|---|---|
| RF3 | web | 已下线管理员体系三件套以占位文件驻留，`myRole` 为假状态（`super_admin` 永假） | `api/admin.ts`、`stores/adminUserStore.ts`、`views/admin/AdminManageView.vue`、`AdminLayout.vue:81` |
| RF4 | client | `ActionSheet.vue` / `ReportModal.vue` / `ListPickerSheet.vue` 仅被单一页面包引用（组件组织红线） | 见 frontend.md #4 |
| RF5 | client | `composables/useReport.ts` 仅被单一页面编排引用（应下沉页面包） | `useReport.ts`；`useDishPage.ts:29` |
| RF6 | client | `stores/dish.ts` 导出 16 个零消费成员 → 连带 `/dishes/new|promotions|rising|hot|recommend` 成死 api 链 | `stores/dish.ts:460-473` |
| RF7 | web | `/dashboard/account` 未被任何路由注册，用户菜单「账号设置」点击**空白** | `AdminLayout.vue:50`；`router/index.ts:13-28` |
| RF8 | client | `uni.showModal.confirmColor` 内联裸 hex `#C45549`，且与同类 3 处（危险色 token）口径不一 | `pages/mine/index.vue:181` vs `theme/tokens.ts:68` |
| RF9 | client | `api/notify.ts` 注释声明不存在的 `PUT /my/notifications/read-all`（幽灵接口） | `notify.ts:7` |

---

## 五、🔵 优化项（11 条）

后端：热度公式散落 4 处（3 处等价 + 1 处独立口径，`DishMapper.xml:128,291,307` + `DishServiceImpl.java:213,272`）｜`FeedbackAdminController.handle` 同时支持 body/query 两种 `reply` 传法｜反馈无公开面需在 spec §5.a 补一句防误判。
前端：`TabBar.vue:25` 未使用 import `onMounted`｜`ApplyFeedbackView.vue` 命名与职责不符（实为反馈容器）｜分页兜底散在两处工具｜`FeedbackView.vue:28-29` 状态映射本地硬编码｜`userStore` 遗留登录态字段｜`api/upload.ts:13` 过期注释｜通知缺「一键已读」。

---

## 六、需拍板事项（登记为 Q-023 ~ Q-028）

| ID | 问题 | 决策人 | 关联 |
|---|---|---|---|
| Q-023 | 管理端口令体系下是否还需「操作人身份」？（决定 `handler_id`/`admin_id` 是补固定标识还是彻底删字段与自保护分支） | 用户 + 技术负责人 | RB3 / RF2 |
| Q-024 | 用户菜单「账号设置」入口去留（现状死链，实际功能在 `/dashboard/system?tab=account`） | 用户 | RF7 |
| Q-025 | 美团式写评是否彻底不做（决定 `review.tags` 列删除） | 用户 | RB7 |
| Q-026 | 上新 / 促销 / 新晋黑马 / 猜你喜欢 / 档口菜品 板块是否保留（决定死 store 与 `promotion` 链路是否可删） | 用户 | RF6 / RB12 |
| Q-027 | 分页契约：后端补 `records/page/pageSize`，还是修订 spec？ | 技术负责人 | RB4 |
| Q-028 | 注销确认色应与其它确认统一为危险色，还是保留主色？ | UI-UX 设计师 | RF8 |

---

## 七、后续跟进机制

1. **登记**：本轮全部 34 条已登记于 `docs/loop/audit/{backend,frontend}.md`；需决策项登记于 `docs/loop/QUESTIONS.md`（Q-023~Q-028），走 §8 唯一渠道。
2. **批次推进**：A 批次本轮清零 → B 批次下一轮 → C 批次待决策后当轮执行 → D 批次排期。
3. **回归门禁**：批次完成后必须三端构建全绿（`mvn compile` / `npm run build:mp-weixin` / `npm run build`），🔴🟠 不回退。
4. **闭环标准**：每项满足「有责任人 → 有修复 → 有验证证据（file:line / commit / 构建结果）→ 登记表置 closed」五条。
5. **复审节奏**：每轮迭代结束重扫 🔴/🟠 是否回退；连续两轮无实质产出则按停止条件 S4 停机上报。
6. **责任矩阵**：后端审计→后端开发工程师；前端双端→对应端工程师；契约/spec→技术负责人；UI 语义→UI-UX 设计师；跨端决策→产品经理。
