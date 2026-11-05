import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElOption, ElSelect } from 'element-plus'
import App from './App.vue'
import router from './router'
import { vZoomImg } from './directives/zoomImg'
// Element Plus 仅消费 el-select（筛选下拉）与 ElMessage（轻提示）：只引「基础变量 + 这几个组件样式」，
// 不整包引入 index.css（首屏体积）；base.css 提供 --el-* 变量，theme.css 用 html:root 覆盖品牌色。
// el-select 内部还依赖 popper（浮层定位）/ scrollbar（下拉滚动）/ input（选择器输入框）/
// tag（多选标签）/ icon（箭头与清除图标）的样式，故一并引入对应分片。
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/el-input.css'
import './styles/theme.css'
import './styles/motion.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 筛选下拉：显式注册 el-select / el-option（模板里用 kebab-case 写法，Vue 解析时对应 ElSelect / ElOption）
app.component('ElSelect', ElSelect)
app.component('ElOption', ElOption)
// 全局指令：任意 <img v-zoomimg> 即获得「点击放大预览」
app.directive('zoomimg', vZoomImg)

app.mount('#app')
