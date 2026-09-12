import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import { useBookStore } from './stores/book'
import { startNotificationListener } from './utils/notifier'
import { initTheme } from './utils/theme'
import './assets/style.css'

// v2.2.61: 在 app 创建前先初始化主题,避免闪白/闪黑
initTheme()

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(router)
  app.mount('#app')

  // 初始化数据库
  const store = useBookStore()
  try {
    await store.init()
  } catch (e) {
    console.error('DB init failed', e)
  }

  // 启动通知监听(原生层) — 自动记账
  try {
    await startNotificationListener()
  } catch (e) {
    console.warn('Notification listener start failed', e)
  }
}

bootstrap()
