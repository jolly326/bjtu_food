import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
// Element Plus 基础样式 + 主题变量（缺失时 --el-bg-color / --el-text-color-* 等未定义，
// 使用原生 HTML + var(--el-*) 的页面会因声明被整条丢弃而出现透明弹窗 / 无边框）
import 'element-plus/dist/index.css'
import './styles/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
