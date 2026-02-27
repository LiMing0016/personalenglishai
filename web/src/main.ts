import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/main.css'

const app = createApp(App)
app.use(router)
// 等初始导航（含守卫重定向）完成后再挂载，避免 /app 等路由下 router-view 先空再跳转导致白屏
router.isReady().then(() => {
  app.mount('#app')
})


