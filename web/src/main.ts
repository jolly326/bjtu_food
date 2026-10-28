import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vZoomImg } from './directives/zoomImg'
// Element Plus 仅消费 ElMessage / ElMessageBox：只引「基础变量 + 这两个组件样式」，
// 不整包引入 index.css（首屏体积）；base.css 提供 --el-* 变量，theme.css 用 html:root 覆盖品牌色。
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import './styles/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 全局指令：任意 <img v-zoomimg> 即获得「点击放大预览」
app.directive('zoomimg', vZoomImg)

app.mount('#app')
