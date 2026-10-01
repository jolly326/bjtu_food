/** 学号：纯数字串（校园邮箱由 `{学号}@bjtu.edu.cn` 推导，故不接受字母/符号） */
export function isValidStudentNo(value: string): boolean {
  return /^\d+$/.test(value.trim())
}
