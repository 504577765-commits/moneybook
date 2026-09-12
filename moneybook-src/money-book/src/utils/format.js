// 通用工具
import dayjs from 'dayjs'

export function fmtAmount(n, withSign = false) {
  const v = Number(n) || 0
  const s = v.toFixed(2)
  return withSign ? (v > 0 ? '+' + s : s) : s
}

export function fmtTime(ts, pattern = 'MM-DD HH:mm') {
  return dayjs(ts).format(pattern)
}

export function groupByDate(list) {
  const out = {}
  for (const t of list) {
    const key = dayjs(t.occurred_at).format('YYYY-MM-DD')
    if (!out[key]) out[key] = []
    out[key].push(t)
  }
  return Object.entries(out)
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([date, items]) => ({ date, items }))
}

export function todayStart() {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

export function monthStart(ts) {
  const d = new Date(ts)
  return new Date(d.getFullYear(), d.getMonth(), 1).getTime()
}

export function monthEnd(ts) {
  const d = new Date(ts)
  return new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime()
}

export function uid() {
  return 'tx_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
}
