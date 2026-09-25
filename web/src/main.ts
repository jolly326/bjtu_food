import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupPress } from './directives/press'
import './styles/shared.css'

// WEB-116：Element Plus 改为按需引入（vite.config.ts 中 ElementPlusResolver），
// 全量 import 与全量 CSS 已移除；模板中的 el-* 组件与 ElMessage 等函数式 API
// 由 unplugin 自动导入并携带对应样式，无需 app.use(ElementPlus)。

const app = createApp(App)

app.use(createPinia())
app.use(router)
setupPress(app)

// 管理端无登录体系：接口由 X-Admin-Token 口令校验，401/403 语义为「口令无效/未授权」，
// 由 api/http.ts 的提示文案承担，不再做路由跳转。
// （原实现 401 时 push('/login')，而 /login 路由不存在且无 catch-all → 会跳空白页）
app.mount('#app')
