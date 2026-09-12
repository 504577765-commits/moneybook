<template>
  <div class="app">
    <router-view v-slot="{ Component, route }">
      <transition :name="route.meta.transition || 'page'" mode="out-in">
        <component :is="Component" :key="route.fullPath" />
      </transition>
    </router-view>
    <DialogHost />

    <nav class="tab-bar">
      <router-link to="/" class="tab tappable">
        <span class="tab-icon">🏠</span>
        <span class="tab-label">首页</span>
        <span class="tab-indicator"></span>
      </router-link>
      <router-link to="/records" class="tab tappable">
        <span class="tab-icon">📋</span>
        <span class="tab-label">账单</span>
        <span class="tab-indicator"></span>
      </router-link>
      <router-link to="/stats" class="tab tappable">
        <span class="tab-icon">📊</span>
        <span class="tab-label">统计</span>
        <span class="tab-indicator"></span>
      </router-link>
      <router-link to="/me" class="tab tappable">
        <span class="tab-icon">👤</span>
        <span class="tab-label">我的</span>
        <span class="tab-indicator"></span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { useBookStore } from './stores/book'
import { onMounted, computed } from 'vue'
import DialogHost from './components/DialogHost.vue'

const store = useBookStore()
const ready = computed(() => store.ready)
</script>

<style scoped>
.app {
  min-height: 100vh;
  padding-bottom: 70px;
  background: var(--bg-1);
  color: var(--text-1);
}

/* === 页面切换动画 === */
.page-enter-active { transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1); }
.page-leave-active { transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1); }
.page-enter-from { opacity: 0; transform: translateY(10px); }
.page-leave-to { opacity: 0; transform: translateY(-4px); }

/* === Tab 栏 === */
.tab-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  display: flex; justify-content: space-around; align-items: center;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  padding: 6px 0 calc(6px + env(safe-area-inset-bottom));
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.03);
}
.tab {
  position: relative;
  text-align: center; flex: 1;
  color: var(--text-muted);
  text-decoration: none;
  font-size: 12px;
  padding: 4px 0;
  transition: color var(--transition);
}
.tab-icon {
  display: block; font-size: 22px; line-height: 1;
  transition: transform var(--transition);
}
.tab-label { display: block; margin-top: 2px; transition: font-weight var(--transition); }

/* 选中态:图标弹一下 + 颜色变化 + 底部小圆点 */
.tab.router-link-active { color: var(--primary); }
.tab.router-link-active .tab-icon {
  animation: bounceIn 0.4s cubic-bezier(0.68, -0.55, 0.265, 1.55);
  transform: scale(1.1);
}
.tab.router-link-active .tab-label { font-weight: 700; }

/* 底部指示小圆点(选中态) */
.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 0;
  height: 3px;
  background: var(--primary);
  border-radius: 999px;
  transform: translateX(-50%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.tab.router-link-active .tab-indicator {
  width: 24px;
}

/* === 中央 FAB 按钮 === */
.tab.fab .tab-icon-big {
  display: inline-block; width: 52px; height: 52px; line-height: 50px;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: #fff;
  border-radius: 50%;
  font-size: 32px; font-weight: 300;
  box-shadow: 0 8px 20px rgba(91,108,255,0.4);
  margin-top: -22px;
  transition: transform var(--transition), box-shadow var(--transition);
  position: relative;
}
.tab.fab:active .tab-icon-big {
  transform: scale(0.9) rotate(90deg);
  box-shadow: 0 4px 10px rgba(91,108,255,0.4);
}

/* FAB 周围光晕(呼吸) */
.tab.fab .tab-icon-big::before {
  content: '';
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: var(--primary);
  opacity: 0.2;
  z-index: -1;
  animation: pulse 2.4s ease-in-out infinite;
}

@keyframes bounceIn {
  0% { transform: scale(0.6); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1.1); }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.2; }
  50% { transform: scale(1.15); opacity: 0.35; }
}
</style>
