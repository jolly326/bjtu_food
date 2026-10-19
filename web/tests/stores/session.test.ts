import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api', () => ({
  sessionApi: { login: vi.fn(() => Promise.resolve({ token: 'T', username: 'admin' })) },
}))
vi.mock('@/api/request', () => ({
  readToken: vi.fn(() => ''),
  writeToken: vi.fn(),
  clearToken: vi.fn(),
  readUsername: vi.fn(() => ''),
  writeUsername: vi.fn(),
  clearUsername: vi.fn(),
}))

import { clearToken, clearUsername, writeToken, writeUsername } from '@/api/request'
import { useSessionStore } from '@/stores/session'

describe('运营端会话 store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('login 写入令牌并记录账号名', async () => {
    const session = useSessionStore()
    await session.login('admin', 'pw')
    expect(writeToken).toHaveBeenCalledWith('T')
    expect(writeUsername).toHaveBeenCalledWith('admin')
    expect(session.token).toBe('T')
    expect(session.username).toBe('admin')
    expect(session.isAuthenticated).toBe(true)
  })

  it('logout 清空会话', () => {
    const session = useSessionStore()
    session.logout()
    expect(clearToken).toHaveBeenCalled()
    expect(clearUsername).toHaveBeenCalled()
    expect(session.token).toBe('')
    expect(session.username).toBe('')
    expect(session.isAuthenticated).toBe(false)
  })
})
