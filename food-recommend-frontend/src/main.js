import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus, { size: 'default' })
app.use(router)

// 设置路由守卫
setupRouterGuards(router)

app.mount('#app')
