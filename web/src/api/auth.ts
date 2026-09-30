import { get, post, put } from './http'
import type { LoginVO, AdminVO, PasswordReq } from '@/types/common'

export function login(username: string, password: string): Promise<LoginVO> {
  return post<LoginVO>('/admin/auth/login', { username, password })
}

export function logout(): Promise<null> {
  return post<null>('/admin/auth/logout')
}

export function me(): Promise<AdminVO> {
  return get<AdminVO>('/admin/auth/me')
}

export function changePassword(req: PasswordReq): Promise<null> {
  return put<null>('/admin/auth/password', req)
}
