export * as canteenApi from './canteen'
export * as stallApi from './stall'
export * as dishApi from './dish'
export * as reviewApi from './review'
export * as userApi from './user'
export * as uploadApi from './upload'
export * as auditApi from './audit'
// adminApi 已下线（2026-09-14，project_spec §7.10）：管理员账号体系随管理端「去登录与角色体系」一并移除，
// 后端 /admin/admins* 与 /auth/admin/login 均不存在。原 web/src/api/admin.ts 已物理删除。
export * as feedbackApi from './feedback'
export * as operationLogApi from './operationLog'
export * as dashboardApi from './dashboard'
