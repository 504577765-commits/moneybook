<template>
  <div class="page me">
    <div class="page-header fade-in-up">我的</div>

    <!-- 状态卡(单行,大显示) — 自动记账 -->
    <div class="status-card fade-in-up" :class="hasPerm && hasNotif ? 'ok' : 'warn'">
      <div class="status-icon">{{ hasPerm && hasNotif ? '✅' : '⚠️' }}</div>
      <div class="status-text">
        <div class="status-title">{{ hasPerm && hasNotif ? '自动记账运行中' : '需要开启权限' }}</div>
        <div class="status-sub">短信 {{ hasPerm ? '✓' : '✗' }} · 通知 {{ hasNotif ? '✓' : '✗' }}</div>
      </div>
      <button v-if="!hasPerm" class="btn-mini primary" @click="openSms">开启</button>
      <button v-else-if="!hasNotif" class="btn-mini primary" @click="openNotifList">开通知</button>
    </div>

    <!-- 系统设置组 -->
    <div class="group fade-in-up" style="animation-delay: 0.05s">
      <div class="group-label">系统</div>
      <div class="group-card">
        <div class="setting-row" @click="openKeepAliveSettings">
          <span class="sr-icon">🔋</span>
          <span class="sr-label">后台保活</span>
          <span class="sr-chev">›</span>
        </div>
        <div class="setting-row" @click="onListenClick">
          <span class="sr-icon">🎯</span>
          <span class="sr-label">监听源</span>
          <span :class="['sr-chev', showListen ? 'open' : '']">›</span>
        </div>
        <!-- v2.2.72: 监听源展开 -->
        <transition name="expand">
        <div v-if="showListen" class="listen-expand">
          <div class="listen-row" v-for="opt in listenOptions" :key="opt.key">
            <div class="lr-info">
              <span class="lr-icon">{{ opt.icon }}</span>
              <div>
                <div class="lr-name">{{ opt.label }}</div>
                <div class="lr-desc">{{ opt.desc }}</div>
              </div>
            </div>
            <label class="switch-sm">
              <input type="checkbox" v-model="listenSettings[opt.key]" @change="saveListenSettingsDebounced">
              <span class="slider-sm"></span>
            </label>
          </div>
          <button class="lr-reset" @click="resetListenSettings">🛡️ 恢复安全模式(全关)</button>
        </div>
        </transition>
        <div class="setting-row" @click="onWidgetClick">
          <span class="sr-icon">📱</span>
          <span class="sr-label">桌面组件</span>
          <span class="sr-chev">›</span>
        </div>
      </div>
    </div>

    <!-- 偏好组 -->
    <div class="group fade-in-up" style="animation-delay: 0.1s">
      <div class="group-label">偏好</div>
      <div class="group-card">
        <div class="setting-row">
          <span class="sr-icon">🌙</span>
          <span class="sr-label">主题</span>
          <div class="theme-pills-sm">
            <span :class="['tp', theme === 'auto' ? 'active' : '']" @click.stop="changeTheme('auto')">跟</span>
            <span :class="['tp', theme === 'light' ? 'active' : '']" @click.stop="changeTheme('light')">☀</span>
            <span :class="['tp', theme === 'dark' ? 'active' : '']" @click.stop="changeTheme('dark')">🌙</span>
          </div>
        </div>
        <div class="setting-row" @click="editBudget">
          <span class="sr-icon">📊</span>
          <span class="sr-label">月度预算</span>
          <span class="sr-value">
            {{ monthlyBudget > 0 ? '¥' + monthlyBudget : '未设置' }}
            <span class="sr-chev">›</span>
          </span>
        </div>
      </div>
    </div>

    <div class="about fade-in-up" style="animation-delay: 0.15s">
      <div>记账本 v{{ APP_VERSION }}</div>
      <div class="muted" style="margin-top:4px;">数据本地存储,不上传云端</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '../stores/book'
import { openNotificationAccess, requestSmsPermission, checkNotificationAccess } from '../utils/notifier'
import { showAlert, showConfirm, showPrompt, showToast } from '../utils/dialog'
import { listTransactions, cleanDuplicates, getRecentRawTexts, clearNotificationLog } from '../db'
import { APP_VERSION } from '../config/version.js'
import { getTheme, setTheme, applyTheme } from '../utils/theme'

const router = useRouter()
function onListenClick() {
  showListen.value = !showListen.value
}
function onWidgetClick() {
  router.push('/widget')
}

// v2.2.61: 主题 + 预算
const theme = ref(getTheme())
function changeTheme(t) {
  theme.value = t
  setTheme(t)
}

const BUDGET_KEY = 'moneybook_monthly_budget'
const monthlyBudget = ref(parseInt(localStorage.getItem(BUDGET_KEY) || '0') || 0)
function setBudget(v) {
  monthlyBudget.value = v
  try { localStorage.setItem(BUDGET_KEY, String(v)) } catch {}
  // 通知其他视图预算已变更
  window.dispatchEvent(new CustomEvent('moneybook:budget-changed', { detail: { value: v } }))
}
async function editBudget() {
  const current = monthlyBudget.value > 0 ? String(monthlyBudget.value) : ''
  const v = await showPrompt('设置每月总预算(元),填 0 表示不设', current, '月度预算', { placeholder: '如 3000', type: 'number' })
  if (v === null) return
  const n = Number(v) || 0
  if (n < 0 || n > 999999) { showToast('金额需在 0~999999 之间', 'error'); return }
  setBudget(n)
  showToast(n > 0 ? `预算已设为 ¥${n}` : '已取消预算', 'success')
}

const monthTxs = ref([])
const monthSpent = computed(() => {
  let s = 0
  for (const t of monthTxs.value) {
    if (t.type === 'expense') s += t.amount
  }
  return s
})
const budgetPct = computed(() => monthlyBudget.value > 0 ? (monthSpent.value / monthlyBudget.value) * 100 : 0)
const fmt = (n) => (n || 0).toFixed(2)

async function loadMonthTxs() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  const end = now.getTime()
  monthTxs.value = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
}

// 实时同步 system theme 变化(auto 模式)
watch(() => store.ready, (ready) => {
  if (ready) {
    applyTheme(theme.value)
    loadMonthTxs()
  }
})

const store = useBookStore()
const hasPerm = ref(false)
const hasNotif = ref(false)
const runStatus = ref(null)

const warningStyle = {
  background: 'linear-gradient(135deg, #FFF5F5 0%, #FFE8E8 100%)',
  border: '2px solid #FFB4B4',
}
const successStyle = {
  background: 'linear-gradient(135deg, #F0FFF4 0%, #D8F8E8 100%)',
  border: '2px solid #6BE8A4',
}

async function openSms() {
  try {
    const r = await requestSmsPermission()
    if (r?.granted) {
      hasPerm.value = true
      showToast('短信权限已开启', 'success')
    } else {
      showToast('用户拒绝授权,无法识别', 'error')
    }
  } catch (e) {
    showToast('申请失败: ' + e.message, 'error')
  }
}

async function openExactAlarmSettings() {
  // Android 12+ 引导用户开启精准闹钟权限
  if (window.Capacitor?.Plugins?.MoneyNotifier?.openExactAlarmSettings) {
    try {
      await window.Capacitor.Plugins.MoneyNotifier.openExactAlarmSettings()
    } catch (e) {
      showAlert('请到 设置 → 应用 → 记账本 → 特殊权限 → 闹钟和提醒 开启', '需要开启精准闹钟')
    }
  }
}

async function openKeepAliveSettings() {
  // v2.1.1 一键打开 ROM 保活设置(神隐模式 / 自启动管理) — 服务自动记账
  if (window.Capacitor?.Plugins?.MoneyNotifier?.openKeepAliveSettings) {
    try {
      await window.Capacitor.Plugins.MoneyNotifier.openKeepAliveSettings()
    } catch (e) {
      showAlert('请手动操作:\n\n小米/红米:\n设置 → 应用 → 记账本 → 省电策略 → 无限制\n设置 → 应用 → 记账本 → 自启动 → 允许\n\n华为:\n设置 → 应用 → 记账本 → 应用启动管理 → 允许自启动\n\nOPPO/vivo:\n设置 → 电池 → 记账本 → 允许后台运行', '后台保活指引')
    }
  }
}

// v2.2.13 监听源设置 — 默认全关(银行短信永远开),只控制支付 APP 通知
const listenSettings = ref({
  alipay: false,
  wechat: false,
  unionpay: false,
  bank_app: false,
  other: false
})

const listenOptions = [
  { key: 'alipay',    label: '支付宝通知', icon: '💙', desc: '支付成功/收款通知' },
  { key: 'wechat',    label: '微信通知', icon: '💚', desc: '微信支付/收款通知' },
  { key: 'unionpay',  label: '银联通知', icon: '💳', desc: '云闪付/银联钱包通知' },
  { key: 'bank_app',  label: '银行 APP 通知', icon: '🏦', desc: '工银/招行/平安等银行 APP' },
  { key: 'other',     label: '其他通知', icon: '📦', desc: '其他可能有支付信息的通知' }
]

// v2.2.13: 一键重置为"安全模式"(全关,只听银行短信)
function resetListenSettings() {
  listenSettings.value.alipay = false
  listenSettings.value.wechat = false
  listenSettings.value.unionpay = false
  listenSettings.value.bank_app = false
  listenSettings.value.other = false
  saveListenSettingsDebounced()
  showAlert('银行短信: 永远监听\n所有支付 APP 通知: 已关闭\n\n这样不会重复记账(2min 内同金额同商户自动去重)', '✅ 已重置为安全模式')
}

let listenSaveTimer = null
async function saveListenSettingsDebounced() {
  if (listenSaveTimer) clearTimeout(listenSaveTimer)
  listenSaveTimer = setTimeout(async () => {
    if (!window.Capacitor?.Plugins?.MoneyNotifier?.saveListenSettings) return
    try {
      await window.Capacitor.Plugins.MoneyNotifier.saveListenSettings(listenSettings.value)
      console.log('监听源设置已保存')
    } catch (e) {
      console.error('saveListenSettings err', e)
    }
  }, 500)
}

async function loadListenSettings() {
  if (!window.Capacitor?.Plugins?.MoneyNotifier?.getListenSettings) return
  try {
    const r = await window.Capacitor.Plugins.MoneyNotifier.getListenSettings()
    Object.keys(listenSettings.value).forEach(k => {
      if (r[k] !== undefined) listenSettings.value[k] = r[k]
    })
  } catch (e) {
    console.error('getListenSettings err', e)
  }
}

async function openNotifList() {
  try {
    await openNotificationAccess()
  } catch (e) {
    showAlert('请手动打开:系统设置 → 通知使用权 → 记账本', '无法跳转')
  }
}

async function checkAll() {
  // 短信权限
  if (window.Capacitor?.Plugins?.MoneyNotifier?.checkSmsPermission) {
    try {
      const r = await window.Capacitor.Plugins.MoneyNotifier.checkSmsPermission()
      hasPerm.value = !!r.granted
    } catch (e) { hasPerm.value = false }
  }
  // 通知权限
  try {
    hasNotif.value = await checkNotificationAccess()
  } catch (e) { hasNotif.value = false }

  // v2.2.9:实时运行状态
  if (hasPerm.value && window.Capacitor?.Plugins?.MoneyNotifier?.getRunStatus) {
    try {
      runStatus.value = await window.Capacitor.Plugins.MoneyNotifier.getRunStatus()
    } catch (e) {
      console.error('getRunStatus err', e)
    }
  }
}

async function showDiagnostics() {
  if (!window.Capacitor?.Plugins?.MoneyNotifier?.getRecentLogs) {
    showAlert('诊断功能不可用', '提示')
    return
  }
  try {
    const r = await window.Capacitor.Plugins.MoneyNotifier.getRecentLogs()
    const logs = r.logs || []
    const text = logs.join('\n') || '(无日志)'
    showAlert(text, '🔍 APP 诊断信息')
  } catch (e) {
    showAlert('获取诊断失败: ' + e.message, '错误')
  }
}

// v2.2.8:诊断面板(显示在页面里,可滚动,长内容也能看)
const showDiagPanel = ref(false)
const showListen = ref(false)
const showRecentNotifs = ref(false)
const recentNotifs = ref([])

function pkgLabel(pkg) {
  if (!pkg) return '?'
  if (pkg === 'com.eg.android.AlipayGphone') return '支付宝'
  if (pkg === 'com.tencent.mm') return '微信'
  if (pkg === 'com.unionpay') return '云闪付'
  if (pkg.includes('mms') || pkg.includes('messaging')) return '短信'
  if (pkg.includes('icbc')) return '工行'
  if (pkg.includes('cmbchina')) return '招行'
  if (pkg.includes('ccb')) return '建行'
  if (pkg.includes('boc')) return '中行'
  if (pkg.includes('abc')) return '农行'
  if (pkg.includes('bankcomm')) return '交行'
  if (pkg.includes('pingan')) return '平安'
  if (pkg.includes('cib')) return '兴业'
  if (pkg.includes('spdb')) return '浦发'
  if (pkg.includes('cgbchina')) return '广发'
  if (pkg.includes('everbright')) return '光大'
  return pkg
}

function formatTime(ts) {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const pad = n => n < 10 ? '0' + n : n
    return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch (e) { return '' }
}

async function loadRecentNotifs() {
  try {
    const r = await getRecentRawTexts(50)
    recentNotifs.value = r.items || []
  } catch (e) {
    console.error('loadRecentNotifs err', e)
  }
}

async function clearNotifLog() {
  if (!await showConfirm('清空所有通知原文记录?', '确认')) return
  try {
    await clearNotificationLog()
    recentNotifs.value = []
    showToast('已清空', 'success')
  } catch (e) {
    showToast('清空失败: ' + e.message, 'error')
  }
}
const diagLogs = ref([])

// v2.2.20:清理重复交易
async function cleanDup() {
  if (!await showConfirm('将删除数据库中的重复交易(同金额同商户 1min 内),保留最早一条。\n\n继续?', '清理重复交易')) return
  try {
    const removed = await cleanDuplicates()
    showToast('已清理 ' + removed + ' 条重复交易', 'success')
    // 重新拉取数据 + 刷新 widget
    await store.refreshTransactions()
    try { await store.refreshWidget() } catch (e) {}
  } catch (e) {
    showToast('清理失败: ' + e.message, 'error')
  }
}

// v2.2.66: 删除云端备份和账户相关代码(用户要求)

async function openDiagnostics() {
  if (!window.Capacitor?.Plugins?.MoneyNotifier?.getRecentLogs) {
    showAlert('诊断功能不可用', '提示')
    return
  }
  showDiagPanel.value = true
  diagLogs.value = ['加载中...']
  try {
    const r = await window.Capacitor.Plugins.MoneyNotifier.getRecentLogs()
    diagLogs.value = r.logs || []
  } catch (e) {
    diagLogs.value = ['获取诊断失败: ' + e.message]
  }
}

onMounted(() => {
  checkAll()
  loadListenSettings()
  // 每次页面显示时重新检查
  const interval = setInterval(checkAll, 2000)
  // 30 秒后停止轮询
  setTimeout(() => clearInterval(interval), 30000)
})

// v2.2.40: 打开"最近通知"面板时自动加载
watch(showRecentNotifs, (v) => {
  if (v) loadRecentNotifs()
})
</script>

<style scoped>
.me .page-header {
  margin-bottom: 12px;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #5B6CFF, #FF6B9D);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.permission-card { padding: 22px; border-radius: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.perm-actions { display: flex; flex-direction: column; gap: 10px; }
.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  font-size: 14px;
  color: #5D6D7E;
  border-bottom: 1px solid #F0F2F5;
}
.status-row:last-of-type { border-bottom: none; }
.badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
}
.badge-ok { background: rgba(38,222,129,0.15); color: #1B9C5C; }
.badge-no { background: rgba(255,107,107,0.15); color: #C0392B; }
.card-title { font-size: 16px; font-weight: 700; margin-bottom: 12px; color: #2C3E50; }
.cat-mini { width: 32px; height: 32px; border-radius: 10px; display:flex; align-items:center; justify-content:center; font-size: 18px; color: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.1); }
.card {
  border-radius: 20px !important;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04) !important;
}
.btn-cute {
  background: linear-gradient(135deg, #FF6B6B, #FF8E8E) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
  padding: 14px 20px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(255,107,107,0.35);
  transition: transform .15s;
  cursor: pointer;
}
.btn-big {
  padding: 18px 20px;
  font-size: 15px;
}
.btn-cute:active { transform: scale(0.97); }

/* v2.2.4 监听源开关 */
.listen-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 0; border-bottom: 1px solid #F0F0F0;
}
.listen-row:last-child { border-bottom: none; }
.listen-info { flex: 1; }
.switch { position: relative; display: inline-block; width: 44px; height: 24px; flex-shrink: 0; }
.switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
  background: #CCC; border-radius: 24px; transition: .3s;
}
.slider:before {
  position: absolute; content: ""; height: 18px; width: 18px;
  left: 3px; top: 3px; background: white; border-radius: 50%; transition: .3s;
}
.switch input:checked + .slider { background: #FFB300; }
.switch input:checked + .slider:before { transform: translateX(20px); }
</style>

