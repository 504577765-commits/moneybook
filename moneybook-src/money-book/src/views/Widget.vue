<template>
  <div class="page widget-page">
    <div class="page-header">桌面小组件</div>

    <!-- 添加小组件引导 -->
    <div class="card" style="background: linear-gradient(135deg, #FFE5F1 0%, #FFC2DD 100%); border: 2px solid #FF6B9D;">
      <div style="text-align:center; padding: 8px 0;">
        <div style="font-size: 56px; margin-bottom: 8px;">📱</div>
        <div style="font-weight:700; font-size: 18px; color: #C2185B;">添加桌面小组件</div>
      </div>

      <div style="background: white; border-radius: 16px; padding: 16px; margin-top: 12px;">
        <div style="font-size: 13px; color: #5D2E4E; line-height: 1.8;">
          <b style="color: #C2185B;">📌 怎么添加?</b><br>
          1. 长按桌面空白处 → 选择"小组件"<br>
          2. 找到"记账本" → 拖到桌面<br>
          3. (如果找不到)看下面↓<br>
        </div>
      </div>

      <div style="background: rgba(255,255,255,0.7); border-radius: 12px; padding: 12px; margin-top: 8px; font-size: 12px; color: #5D2E4E; line-height: 1.7;">
        <b>⚠️ 如果在小组件列表里找不到"记账本":</b><br>
        · <b>小米/红米</b>: 安全中心 → 自启动管理 → 允许"记账本"<br>
        · <b>华为</b>: 手机管家 → 启动管理 → 改为"手动管理" → 允许<br>
        · <b>OPPO/vivo</b>: 设置 → 应用管理 → 记账本 → 允许"显示桌面快捷方式"<br>
        · 添加完小组件后,返回本页面 → 下方有"主题/透明度"等设置
      </div>
    </div>

    <!-- 主题色 -->
    <div class="card">
      <div class="card-title">🎨 主题色</div>
      <div class="theme-grid">
        <div v-for="t in themes" :key="t.id"
          :class="['theme-item', settings.theme === t.id ? 'active' : '']"
          :style="{background: t.bg}"
          @click="setTheme(t.id)">
          <div class="theme-circle"></div>
          <div class="theme-label">{{ t.name }}</div>
        </div>
      </div>
    </div>

    <!-- 透明度(v2.2.5:RemoteViews 不支持动态改透明度,这个功能暂时隐藏) -->
    <!--
    <div class="card">
      <div class="card-title">💧 透明度 ({{ settings.opacity }}%)</div>
      <div class="opacity-grid">
        <div v-for="o in [20, 50, 80, 100]" :key="o"
          :class="['opacity-item', settings.opacity === o ? 'active' : '']"
          @click="setOpacity(o)">
          {{ o }}%
        </div>
      </div>
    </div>
    -->

    <!-- v2.2.11 完全实时模式 -->
    <div class="card" style="background: linear-gradient(135deg, #E0FFE5 0%, #C8F8D8 100%); border: 2px solid #1B9C5C;">
      <div class="card-title" style="color:#1B5E20;">⚡ 完全实时模式(v{{ APP_VERSION }})</div>
      <div style="font-size: 13px; color: #1B5E20; line-height: 1.7;">
        <b>Widget 不再有任何定时器</b>,只在以下情况更新:
      </div>
      <ul style="margin: 8px 0 0; padding-left: 22px; font-size: 12px; color: #1B5E20; line-height: 1.9;">
        <li>📲 收到银行短信 → <b>立即更新</b></li>
        <li>📝 APP 内添加交易 → <b>立即更新</b></li>
        <li>🚀 打开 APP → <b>立即更新</b></li>
        <li>🚀 打开 APP → <b>立即更新</b></li>
      </ul>
      <div style="margin-top: 10px; padding: 8px; background: rgba(27,156,92,0.2); border-radius: 8px; font-size: 12px; color: #1B5E20; line-height: 1.6;">
        💡 <b>以前</b>:30 分钟兜底定时器也刷新一次<br>
        <b>现在</b>:完全靠记账触发,0 延迟
      </div>
    </div>

    <!-- 状态 / 操作 -->
    <div class="card">
      <div class="card-title">📊 当前状态</div>
      <div class="status-row">
        <span>已添加小组件</span>
        <span :class="['badge', hasWidget ? 'badge-ok' : 'badge-no']">
          {{ hasWidget ? `✓ ${widgetCount}个` : '✗ 未添加' }}
        </span>
      </div>
      <button class="btn btn-cute" style="width:100%; margin-top:12px;" @click="refreshNow">
        🔄 立即刷新小组件
      </button>
    </div>

    <!-- 预览图 -->
    <div class="card">
      <div class="card-title">👀 预览效果</div>
      <div class="widget-preview" :style="previewStyle">
        <div class="preview-header">
          <span style="font-weight:700; font-size:13px;">💰 今日记账</span>
          <span style="font-size:10px; opacity:0.8;">刚刚</span>
        </div>
        <div class="preview-row">
          <span style="font-weight:bold;">支出 ¥{{ previewData.expense }}</span>
          <span>收入 ¥{{ previewData.income }}</span>
        </div>
        <div style="font-size:11px; opacity:0.85; margin-top:2px;">今日 {{ previewData.count }} 笔</div>
        <div style="height:1px; background:white; opacity:0.3; margin:6px 0;"></div>
        <div style="font-size:12px;">🛒 {{ previewData.last }}</div>
      </div>
    </div>

    <div style="text-align:center; color:#999; font-size:12px; margin: 20px 0;">
      v{{ APP_VERSION }} · 桌面小组件
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useBookStore } from '../stores/book'
import { showAlert, showToast } from '../utils/dialog'
import { APP_VERSION } from '../config/version.js'

const store = useBookStore()
const hasWidget = ref(false)
const widgetCount = ref(0)
const settings = ref({
  theme: 'pink',
  opacity: 100,
  refreshMinutes: 15
})

const themes = [
  { id: 'pink', name: '粉色', bg: 'linear-gradient(135deg, #FF6B9D, #FF9CC3)' },
  { id: 'blue', name: '蓝色', bg: 'linear-gradient(135deg, #5B6CFF, #8C9EFF)' },
  { id: 'green', name: '绿色', bg: 'linear-gradient(135deg, #26DE81, #6BE8A4)' },
  { id: 'purple', name: '紫色', bg: 'linear-gradient(135deg, #A55EEA, #C490F0)' },
]

const refreshOptions = [
  // v2.2.11: 不再使用 — widget 完全实时
]

// 预览数据
const previewData = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  const todayTxs = store.transactions.filter(t => {
    if (!t.occurredAt) return false
    const d = new Date(t.occurredAt)
    return d.toISOString().slice(0, 10) === today
  })
  const expense = todayTxs.filter(t => t.type === 'expense').reduce((s, t) => s + (t.amount || 0), 0)
  const income = todayTxs.filter(t => t.type === 'income').reduce((s, t) => s + (t.amount || 0), 0)
  const last = todayTxs[0] || store.transactions[0]
  return {
    expense: expense.toFixed(2),
    income: income.toFixed(2),
    count: todayTxs.length,
    last: last ? `${last.merchant || '未知'} ¥${(last.amount || 0).toFixed(2)}` : '还没有记账哦~'
  }
})

const previewStyle = computed(() => {
  const t = themes.find(x => x.id === settings.value.theme) || themes[0]
  const opacity = settings.value.opacity / 100
  return {
    background: t.bg,
    opacity: opacity,
    borderRadius: '16px',
    padding: '12px',
    color: 'white',
    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
  }
})

onMounted(async () => {
  if (window.Capacitor?.Plugins?.MoneyNotifier?.getWidgetSettings) {
    try {
      const r = await window.Capacitor.Plugins.MoneyNotifier.getWidgetSettings()
      settings.value.theme = r.theme
      settings.value.opacity = r.opacity
      settings.value.refreshMinutes = r.refreshMinutes
      hasWidget.value = r.hasWidget
      widgetCount.value = r.widgetCount
    } catch (e) {
      console.error('getWidgetSettings err', e)
    }
  }
})

async function setTheme(theme) {
  settings.value.theme = theme
  await save()
}

async function setOpacity(opacity) {
  settings.value.opacity = opacity
  await save()
}

async function setRefresh() {
  // v2.2.11: 不再使用 — widget 完全实时,没有刷新频率选项
}

async function save() {
  if (!window.Capacitor?.Plugins?.MoneyNotifier?.saveWidgetSettings) return
  try {
    await window.Capacitor.Plugins.MoneyNotifier.saveWidgetSettings({
      theme: settings.value.theme,
      opacity: settings.value.opacity,
      refreshMinutes: 0  // v2.2.11: 0 = 完全实时
    })
  } catch (e) {
    console.error('saveWidgetSettings err', e)
  }
}

async function refreshNow() {
  if (!window.Capacitor?.Plugins?.MoneyNotifier?.refreshWidget) return
  try {
    const r = await window.Capacitor.Plugins.MoneyNotifier.refreshWidget()
    if (r.message) {
      showAlert(r.message, '提示')
    } else {
      showToast('已刷新', 'success')
    }
  } catch (e) {
    showToast('刷新失败: ' + e.message, 'error')
  }
}
</script>

<style scoped>
.widget-page { padding-bottom: 40px; }
.theme-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top: 8px; }
.theme-item {
  border-radius: 14px; padding: 12px 8px; text-align: center; cursor: pointer;
  border: 2px solid transparent; transition: all 0.2s;
}
.theme-item.active { border-color: #2C3E50; transform: scale(1.05); }
.theme-circle {
  width: 28px; height: 28px; border-radius: 50%; background: rgba(255,255,255,0.5);
  margin: 0 auto 6px;
}
.theme-label { font-size: 12px; color: white; font-weight: 600; }
.opacity-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 8px; }
.opacity-item {
  background: #F5F5F5; border-radius: 10px; padding: 10px 6px; text-align: center;
  cursor: pointer; font-size: 13px; color: #2C3E50; transition: all 0.2s;
}
.opacity-item.active { background: #FF6B9D; color: white; font-weight: 700; }
.status-row { display: flex; justify-content: space-between; padding: 6px 0; }
.badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.badge-ok { background: #D8F8E8; color: #1B9C5C; }
.badge-no { background: #FFE4E4; color: #C62828; }
.widget-preview {
  margin-top: 10px;
  min-height: 120px;
}
.preview-header { display: flex; justify-content: space-between; align-items: center; color: white; }
.preview-row { display: flex; justify-content: space-between; margin-top: 6px; font-size: 13px; color: white; }
</style>
