import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupPress } from './directives/press'
import { UNAUTHORIZED_EVENT } from '@/api/http'
import './styles/shared.css'
// 显式引入 Element Plus 全量样式，保证 ElMessage / ElMessageBox 等函数式 API 有样式
// （模板内 el-* 组件仍由 vite 的 ElementPlusResolver 按需解析）。
import 'element-plus/dist/index.css'

// 令牌失效（401）统一跳转登录页
window.addEventListener(UNAUTHORIZED_EVENT, () => {
  router.replace('/login')
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
setupPress(app)
app.mount('#app')
