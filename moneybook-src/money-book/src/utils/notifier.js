
import { showToast } from './dialog'

// 通知监听器 — v2.2.19 关键修复:JS 端只显示 Toast,不再写库!
//
// 之前的问题: Java 端 saveTransaction + JS 端 insertTransactionJs 双重写库
// 解决: JS 端完全不再调用 addTransaction,所有记账逻辑都在 Java 端
// Java 端已有 5 层去重,这是唯一正确的"事实数据源"
import { Capacitor } from '@capacitor/core'

let started = false
let pollTimer = null

export async function startNotificationListener() {
  if (started) return
  started = true

  if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') {
    console.log('[Notifier] Web 环境,跳过原生通知监听')
    return
  }

  const Notifier = window.Capacitor?.Plugins?.MoneyNotifier
  if (!Notifier) {
    console.warn('[Notifier] MoneyNotifier 插件未注册')
    return
  }

  // 1) 监听 window 上的实时事件 — 只显示 toast,不写库!
  window.addEventListener('paymentNotification', async (e) => {
    const info = e.detail || {}
    console.log('[Notifier] 实时通知', info)
    showToastFromInfo(info)
  })

  // 2) 启动时拉取历史缓存 — 只显示 toast,不写库!
  await pullPendingToast()

  // 3) 每 30 秒定时拉取 — 只显示 toast,不写库!
  pollTimer = setInterval(pullPendingToast, 30000)

  console.log('[Notifier] 启动成功 (v2.2.19: JS 端不再写库)')
}

async function pullPendingToast() {
  const Notifier = window.Capacitor?.Plugins?.MoneyNotifier
  if (!Notifier?.getPendingNotifications) return
  try {
    const { notifications } = await Notifier.getPendingNotifications()
    console.log(`[Notifier] 拉取到 ${notifications?.length || 0} 条待处理通知(只显示 toast)`)
    for (const n of notifications || []) {
      showToastFromInfo(n)
    }
  } catch (e) {
    console.warn('[Notifier] 拉取失败', e)
  }
}

function showToastFromInfo(info) {
  // v2.2.19:JS 端只显示 toast,所有数据写入由 Java 端完成
  // Java 端 PaymentNotificationListener.saveTransaction + 5 层去重
  if (info.preAmount && info.preMerchant) {
    showLocalToast(`已自动记账: ${info.preMerchant} ¥${Number(info.preAmount).toFixed(2)}`)
    // v2.2.76: 通知所有视图刷新(月度预算 / 列表 / 统计)
    window.dispatchEvent(new CustomEvent('moneybook:tx-added', { detail: info }))
  } else if (info.content || info.text) {
    showLocalToast(`收到通知: ${(info.content || info.text).substring(0, 30)}`)
  }
}

export async function openNotificationAccess() {
  const Notifier = window.Capacitor?.Plugins?.MoneyNotifier
  if (Notifier?.openAccessSettings) {
    return await Notifier.openAccessSettings()
  }
}

export async function checkNotificationAccess() {
  const Notifier = window.Capacitor?.Plugins?.MoneyNotifier
  if (Notifier?.checkNotificationAccess) {
    try {
      const r = await Notifier.checkNotificationAccess()
      return !!r.granted
    } catch (e) {
      return false
    }
  }
  return false
}

export async function requestSmsPermission() {
  const Notifier = window.Capacitor?.Plugins?.MoneyNotifier
  if (Notifier?.requestSmsPermission) {
    return await Notifier.requestSmsPermission()
  }
}


function showLocalToast(text) {
  showToast(text, 'success', 2500)
}
