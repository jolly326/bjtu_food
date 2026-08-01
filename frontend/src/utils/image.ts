import { API_BASE_URL } from '@/api/config'

export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  if (path.startsWith('/static/')) return path
  if (path.startsWith('/images/') || path.startsWith('/uploads/')) return `${API_BASE_URL}${path}`
  return path
}


