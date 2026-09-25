export * as canteenApi from './canteen'
export * as stallApi from './stall'
export * as dishApi from './dish'
export * as reviewApi from './review'
export * as userApi from './user'
// auditApi 不提供（Q-107 / P1-01）：/admin/audit/** 三条接口 + AuditType 前端零消费
// （管理员录入即 approved，pending 无新来源）；仍在用的 listAllReviews（打 /admin/reviews）已迁入 reviewApi。
// adminApi 不提供（project_spec §7.10）：管理员账号体系随管理端「去登录与角色体系」一并移除，
// 后端 /admin/admins* 与 /auth/admin/login 均不存在。
export * as feedbackApi from './feedback'
export * as correctionApi from './corrections'
// operationLogApi 不提供（后台精简）：操作日志页 / 常量表 / 路由整链下线，
// 前端不再消费 GET /admin/operation-logs（后端 AOP 埋点仍照常写，不受影响）。
