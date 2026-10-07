import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupPress } from '@/directives/press'
import { setUnauthorizedHandler } from '@/api/http'
import './styles/shared.css'
// 显式引入 Element Plus 全量样式：**函数式 API（ElMessage / ElMessageBox）的样式不在
// ElementPlusResolver 的按需注入范围内**（它只处理模板内 el-* 组件），故此处必须全量引入。
import 'element-plus/dist/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
setupPress(app)

// TD-23：401 = 会话失效（未带 token / 签名无效 / 已过期 / 账号已停用）→ 请求层已清 token，此处跳登录页。
// 🔴 已在登录页时不再跳 —— 登录失败本身也回 401，重复导航没有意义（见 docs/ui/web/登录页.md）。
setUnauthorizedHandler(() => {
  if (router.currentRoute.value.path !== '/login') {
    void router.replace('/login')
  }
})

app.mount('#app')
