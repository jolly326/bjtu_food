/**
 * UGC 展示层 XSS 转义工具
 * 后端落库前已做敏感词过滤，此处再做 HTML 转义兜底，防止存储型 XSS。
 */

/** 转义 HTML 特殊字符为实体 */
export function escapeHtml(input: string | null | undefined): string {
  if (!input) return '';
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * 将纯文本按换行符转换为 HTML <br>，并对每行做 HTML 转义（用于 v-html 渲染用户文本）。
 */
export function renderUserText(input: string | null | undefined): string {
  return escapeHtml(input).replace(/\n/g, '<br>');
}
