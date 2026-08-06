import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { setupPress } from './directives/press'
import { onUnauthorized } from './api/http'
import './styles/shared.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
setupPress(app)

// P1-W1：401 统一引导登录（清 token + 跳 /login），对齐小程序 auth:unauthorized
onUnauthorized(() => {
  if (router.currentRoute.value.path !== '/login') router.push('/login')
})

app.mount('#app')
