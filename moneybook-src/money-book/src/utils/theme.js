// v2.2.61: 暗色模式工具 — 跟随系统 / 浅色 / 深色

const THEME_KEY = 'moneybook_theme'
// 'auto' | 'light' | 'dark'

export function getTheme() {
  try {
    return localStorage.getItem(THEME_KEY) || 'auto'
  } catch {
    return 'auto'
  }
}

export function setTheme(t) {
  if (!['auto', 'light', 'dark'].includes(t)) t = 'auto'
  try {
    localStorage.setItem(THEME_KEY, t)
  } catch {}
  applyTheme(t)
}

export function applyTheme(t) {
  const effective = resolveEffective(t)
  document.documentElement.setAttribute('data-theme', effective)
  document.documentElement.setAttribute('data-theme-pref', t)
  // meta theme-color for status bar
  let meta = document.querySelector('meta[name="theme-color"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'theme-color'
    document.head.appendChild(meta)
  }
  meta.content = effective === 'dark' ? '#0F172A' : '#FFFFFF'
}

/**
 * v2.2.80: auto 模式改为"根据手机时间"自动切换亮/暗
 * - 晚上 19:00 ~ 早上 06:59 用深色,其余用浅色
 * - light / dark 为手动固定
 */
export function resolveEffective(t) {
  if (t === 'light') return 'light'
  if (t === 'dark') return 'dark'
  // auto: 按手机时钟
  const h = new Date().getHours()
  const isNight = h >= 19 || h < 7
  return isNight ? 'dark' : 'light'
}

/**
 * v2.2.80: 每分钟根据手机时间刷新亮暗(auto 模式下随天黑/天亮自动切换)
 * 用 setTimeout 链,每次触发后对齐到下一个整分钟,避免定时器堆积
 */
let _themeTimer = null
export function startThemeTimer() {
  if (typeof window === 'undefined') return
  const tick = () => {
    if (getTheme() === 'auto') applyTheme('auto')
    // 对齐到下一个整分钟边界再触发下一次
    const now = new Date()
    const delay = (60 - now.getSeconds()) * 1000 - now.getMilliseconds()
    _themeTimer = setTimeout(tick, delay)
  }
  clearTimeout(_themeTimer)
  tick()
}

export function initTheme() {
  applyTheme(getTheme())
  // 每分钟按手机时间刷新(auto 模式天黑/天亮自动切换)
  startThemeTimer()
}
