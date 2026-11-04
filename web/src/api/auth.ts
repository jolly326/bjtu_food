/** 管理员登录域（docs/api/web/auth.md）。 */

import { http } from './request'

export const sessionApi = {
  login: (username: string, password: string) =>
    http.post<{ token: string; username: string }>('/admin/sessions', { username, password }),
}
