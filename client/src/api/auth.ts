/** 用户身份域（docs/api/client/auth.md）。 */

import { http } from '@/utils/request'

export const authApi = {
  /** 冷启动静默登录（唯一免鉴权端点） */
  wechatLogin: (code: string) => http.post<{ token: string }>('/auth/wechat-login', { code }),
}
