export * as canteenApi from './canteen'
export * as stallApi from './stall'
export * as dishApi from './dish'
export * as reviewApi from './review'
export * as userApi from './user'
export * as uploadApi from './upload'
// auditApi 已物理删除（2026-09-14，Q-107 / P1-01）：/admin/audit/** 三条接口 + AuditType 前端零消费
// （管理员录入即 approved，pending 无新来源），原 web/src/api/audit.ts 已删除；
// 其中仍在用的 listAllReviews（打 /admin/reviews）已迁入 reviewApi。
// adminApi 已下线（2026-09-14，project_spec §7.10）：管理员账号体系随管理端「去登录与角色体系」一并移除，
// 后端 /admin/admins* 与 /auth/admin/login 均不存在。原 web/src/api/admin.ts 已物理删除。
export * as feedbackApi from './feedback'
export * as operationLogApi from './operationLog'
export * as dashboardApi from './dashboard'
export * as categoryApi from './category'
