<template>
  <div class="page stats">
    <div class="page-header fade-in-up">统计</div>

    <div class="range-tabs card fade-in-up">
      <span v-for="r in ranges" :key="r.v"
        :class="['chip', store.currentRange === r.v ? 'active':'']"
        @click="store.setRange(r.v)">{{ r.label }}</span>
    </div>

    <div class="card summary-card fade-in-up" style="animation-delay: 0.05s">
      <div class="row-between">
        <div>
          <div class="muted">结余</div>
          <div class="big-amount">¥{{ fmtAmount(summary.balance) }}</div>
        </div>
        <div class="col" style="text-align:right;">
          <div><span class="muted">收入</span> <span class="amount-pos">¥{{ fmtAmount(summary.income) }}</span></div>
          <div><span class="muted">支出</span> <span class="amount-neg">¥{{ fmtAmount(summary.expense) }}</span></div>
        </div>
      </div>
    </div>

    <!-- v2.3.x: 净资产 -->
    <div class="card summary-card fade-in-up" style="animation-delay: 0.08s">
      <div class="card-title" style="display:flex;justify-content:space-between;align-items:center;">
        <span>净资产</span>
        <span class="link-all" @click="goAccounts">管理账户 ›</span>
      </div>
      <div class="nw-total">¥{{ fmtAmount(netWorth) }}</div>
      <div class="nw-list">
        <div v-for="a in activeAccounts" :key="a.id" class="nw-row">
          <span class="nw-icon">{{ a.icon || '💰' }}</span>
          <span class="nw-name">{{ a.name }}</span>
          <span class="nw-balance">¥{{ fmtAmount(a.balance) }}</span>
        </div>
        <div v-if="!activeAccounts.length" class="muted">暂无账户</div>
      </div>
    </div>

    <!-- v2.3.x: 现金流(近 12 月) -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.1s">
      <div class="card-title">💰 现金流</div>
      <v-chart :option="cashflowOption" autoresize style="height: 220px;" v-if="cashflow.some(o => o.expense > 0 || o.income > 0)" />
      <div v-else class="empty"><div class="ico">💸</div><div>暂无数据</div></div>
    </div>

    <!-- v2.3.x: 年累计 vs 去年同期 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.12s" v-if="yearCompare">
      <div class="card-title">📅 {{ year }}年累计 vs 去年</div>
      <div v-for="(row, i) in yearRows" :key="i" class="yc-row">
        <span class="yc-label">{{ row.label }}</span>
        <span class="yc-cur">{{ row.cur }}（去年 {{ row.last }}）</span>
        <span :class="['yc-pct', row.delta===0 ? 'flat' : (row.isGood ? 'pos' : 'neg')]">{{ row.arrow }}{{ fmtAmount(row.delta) }} ({{ (row.pct >= 0 ? '+' : '') + row.pct.toFixed(1) }}%)</span>
      </div>
    </div>

    <!-- v2.2.67: 智能洞察 (顶部 1-2 条) -->
    <div v-if="topInsights.length" class="fade-in-up" style="animation-delay: 0.1s">
      <div v-for="(ins, i) in topInsights" :key="i"
        class="insight-card"
        :style="`background: linear-gradient(135deg, ${ins.bg1}, ${ins.bg2});`">
        <div class="insight-icon">{{ ins.icon }}</div>
        <div class="insight-text">
          <div class="insight-title">{{ ins.title }}</div>
          <div class="insight-sub">{{ ins.sub }}</div>
        </div>
      </div>
    </div>

    <!-- v2.2.67: 月对月对比 -->
    <div v-if="monthCompare" class="card fade-in-up" style="animation-delay: 0.12s">
      <div class="card-title">📆 本月 vs 上月</div>
      <div class="mc-row">
        <div class="mc-label muted">本月支出</div>
        <div class="mc-amount amount-neg">¥{{ fmtAmount(monthCompare.thisMonth) }}</div>
      </div>
      <div class="mc-row">
        <div class="mc-label muted">上月支出</div>
        <div class="mc-amount">¥{{ fmtAmount(monthCompare.lastMonth) }}</div>
      </div>
      <div class="mc-row">
        <div class="mc-label muted">变化</div>
        <div :class="['mc-amount', monthCompare.diff > 0 ? 'amount-neg' : 'amount-pos', 'mc-diff']">
          {{ monthCompare.diff > 0 ? '多花' : '少花' }} ¥{{ fmtAmount(Math.abs(monthCompare.diff)) }}
          <span style="font-size:13px;font-weight:500;">({{ monthCompare.diffPct > 0 ? '+' : '' }}{{ monthCompare.diffPct.toFixed(1) }}%)</span>
        </div>
      </div>
      <div class="mc-progress">
        <div :class="['mc-bar', monthCompare.diff > 0 ? 'mc-bar-up' : 'mc-bar-down']"
             :style="`width: ${Math.min(100, monthCompare.barPct)}%`"></div>
      </div>
      <div class="muted" style="font-size:11px;margin-top:4px;">
        平均每天 {{ fmtAmount(monthCompare.thisAvg) }}, 上月 {{ fmtAmount(monthCompare.lastAvg) }}
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.15s">
      <div class="card-title">每日趋势</div>
      <v-chart :option="trendOption" autoresize style="height: 200px;" v-if="trendData.length" />
      <div v-else class="empty"><div class="ico">📈</div><div>暂无数据</div></div>
    </div>

    <!-- 饼图 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.18s">
      <div class="card-title">支出分类</div>
      <v-chart :option="pieOption" autoresize style="height: 220px;" v-if="pieData.length" />
      <div v-else class="empty"><div class="ico">🍩</div><div>暂无支出记录</div></div>
    </div>

    <div class="card chart-card fade-in-up" style="animation-delay: 0.2s" v-if="ranking.length">
      <div class="card-title">分类 TOP 5</div>
      <div v-for="(r, i) in ranking" :key="r.category_id" class="rank-item">
        <span class="rank-no">{{ i+1 }}</span>
        <span class="rank-icon">{{ r.icon }}</span>
        <span class="rank-name">{{ r.name }}</span>
        <span class="rank-amount">¥{{ fmtAmount(r.total) }}</span>
        <span class="muted">{{ ((r.total / summary.expense) * 100).toFixed(0) }}%</span>
      </div>
    </div>

    <!-- 商户排行 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.22s" v-if="merchantRanking.length">
      <div class="card-title">🏷️ 常去商户 TOP 5</div>
      <div v-for="(r, i) in merchantRanking" :key="r.merchant" class="rank-item">
        <span class="rank-no">{{ i+1 }}</span>
        <span class="rank-icon">🏪</span>
        <span class="rank-name">{{ r.merchant }}</span>
        <span class="muted" style="font-size:11px;">{{ r.count }}次</span>
        <span class="rank-amount">¥{{ fmtAmount(r.total) }}</span>
      </div>
    </div>

    <!-- 热力图 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.25s" v-if="calendarData.weeks.length">
      <div class="card-title" style="display:flex;justify-content:space-between;align-items:center;">
        <span>📅 近 12 周</span>
        <span class="muted" style="font-size:11px;font-weight:400;">颜色越深 = 越多</span>
      </div>
      <div class="heatmap">
        <div class="hm-day-labels">
          <span v-for="d in ['一','三','五']" :key="d">{{ d }}</span>
        </div>
        <div class="hm-grid">
          <div v-for="(week, wi) in calendarData.weeks" :key="wi" class="hm-col">
            <div v-for="(cell, ci) in week" :key="ci"
              :class="['hm-cell', `lvl-${cell.level}`]"
              :title="cell.date ? `${cell.date}: ¥${cell.amount.toFixed(0)}` : ''"></div>
          </div>
        </div>
      </div>
      <div class="hm-legend">
        <span class="muted">少</span>
        <div :class="['hm-cell', 'lvl-0']"></div>
        <div :class="['hm-cell', 'lvl-1']"></div>
        <div :class="['hm-cell', 'lvl-2']"></div>
        <div :class="['hm-cell', 'lvl-3']"></div>
        <div :class="['hm-cell', 'lvl-4']"></div>
        <span class="muted">多</span>
      </div>
    </div>

    <!-- v2.3.x: 自定义报表入口 -->
    <button class="report-btn fade-in-up" @click="goReport">📊 自定义报表</button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useBookStore } from '../stores/book'
import { useRouter } from 'vue-router'
import { fmtAmount } from '../utils/format'
import { listTransactions } from '../db'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

use([CanvasRenderer, LineChart, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const store = useBookStore()
const router = useRouter()
const ranges = [
  { v: 'day', label: '日' },
  { v: 'week', label: '周' },
  { v: 'month', label: '月' }
]

const txs = ref([])
const heatmapTxs = ref([]) // v2.2.74: 独立全量(近 12 周),不受 range 限制
const mcTxs = ref([])     // v2.2.80: 月对月对比专用 — 独立拉取近 2 月,不受 range 限制

// v2.2.80: 月对月对比独立刷新(近 2 月全量)
async function refreshMonthCompare() {
  const now = new Date()
  const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1).getTime()
  const thisMonthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime()
  mcTxs.value = await listTransactions({ startTs: lastMonthStart, endTs: thisMonthEnd, limit: 10000 })
}

async function refresh() {
  const { start, end } = store.getRangeBounds()
  txs.value = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
  // v2.2.74: 热力图永远显示近 12 周 — 与当前 range 无关
  const now = new Date()
  const heatEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).getTime()
  const heatStart = heatEnd - 12 * 7 * 86400000
  heatmapTxs.value = await listTransactions({ startTs: heatStart, endTs: heatEnd, type: 'expense', limit: 10000 })
  await refreshMonthCompare() // v2.2.80: 同时刷新月对月对比
  await loadCashflow()        // v2.3.x: 同时刷新现金流 + 年累计
}
watch(() => [store.currentRange, store.currentAnchor], refresh, { immediate: false })
onMounted(refresh)

// v2.2.76: 新交易入账后立即刷新
function onTxAdded() { refresh() }
window.addEventListener('moneybook:tx-added', onTxAdded)
onUnmounted(() => window.removeEventListener('moneybook:tx-added', onTxAdded))

const summary = computed(() => {
  let expense = 0, income = 0
  for (const t of txs.value) {
    if (t.type === 'transfer') continue   // v2.3.x: 转账不计入收支
    if (t.type === 'expense') expense += t.amount
    else income += t.amount
  }
  return { expense, income, balance: income - expense }
})

// v2.3.x: 净资产
const accounts = computed(() => store.accounts)
const activeAccounts = computed(() => accounts.value.filter(a => !a.archived))
const netWorth = computed(() => accounts.value.reduce((s, a) => s + (Number(a.balance) || 0), 0))

// v2.3.x: 现金流(近 12 月) + 年累计 — 拉取去年初至今
const cashflowTxs = ref([])
const CASHFLOW_MONTHS = 12
async function loadCashflow() {
  const now = new Date()
  const y1 = new Date(now.getFullYear() - 1, 0, 1).getTime()
  const start = Math.min(
    new Date(now.getFullYear(), now.getMonth() - (CASHFLOW_MONTHS - 1), 1).getTime(),
    y1
  )
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime()
  cashflowTxs.value = await listTransactions({ startTs: start, endTs: end, limit: 10000 })
}

function sumParts(t, dirs) {
  const isIncome = t.type === 'income'
  const parts = (t.splits && t.splits.length) ? t.splits : [{ amount: t.amount }]
  for (const p of parts) {
    const amt = Number(p.amount) || 0
    if (isIncome) dirs.income += amt; else dirs.expense += amt
  }
}

const cashflow = computed(() => {
  const now = new Date()
  const arr = []
  for (let i = CASHFLOW_MONTHS - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    arr.push({
      ym: `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`,
      label: `${d.getMonth()+1}月`, expense: 0, income: 0
    })
  }
  const map = new Map(arr.map(x => [x.ym, x]))
  for (const t of cashflowTxs.value) {
    if (t.type === 'transfer') continue
    const d = new Date(t.occurred_at)
    const o = map.get(`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`)
    if (o) sumParts(t, o)
  }
  return arr
})

const cashflowOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['支出', '收入'], bottom: 0, textStyle: { fontSize: 11 } },
  grid: { left: 36, right: 16, top: 20, bottom: 32 },
  xAxis: { type: 'category', data: cashflow.value.map(d => d.label), axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
  series: [
    { name: '支出', data: cashflow.value.map(d => d.expense.toFixed(2)), type: 'line', smooth: true, lineStyle:{color:'#FF6B6B'}, itemStyle:{color:'#FF6B6B'}, areaStyle:{color:'rgba(255,107,107,0.12)'} },
    { name: '收入', data: cashflow.value.map(d => d.income.toFixed(2)),  type: 'line', smooth: true, lineStyle:{color:'#26DE81'}, itemStyle:{color:'#26DE81'}, areaStyle:{color:'rgba(38,222,129,0.12)'} }
  ]
}))

// v2.3.x: 年累计 vs 去年同期
const year = computed(() => new Date().getFullYear())
const yearCompare = computed(() => {
  const now = new Date()
  const y = now.getFullYear()
  const pullStart = new Date(y - 1, 0, 1).getTime()
  const thisYearStart = new Date(y, 0, 1).getTime()
  const nowMid = now.getTime()
  const elapsed = nowMid - thisYearStart
  const lastPeriodEnd = pullStart + elapsed   // 去年同期同一时间点
  const cy = { income: 0, expense: 0 }
  const ly = { income: 0, expense: 0 }
  for (const t of cashflowTxs.value) {
    if (t.type === 'transfer') continue
    const ts = t.occurred_at
    if (ts >= thisYearStart && ts <= nowMid) sumParts(t, cy)
    else if (ts >= pullStart && ts < lastPeriodEnd) sumParts(t, ly)
  }
  if (cy.expense === 0 && cy.income === 0 && ly.expense === 0 && ly.income === 0) return null
  cy.balance = cy.income - cy.expense
  ly.balance = ly.income - ly.expense
  return { cy, ly, hasData: true }
})
const yearRows = computed(() => {
  const v = yearCompare.value
  if (!v) return []
  const pct = (cur, base) => base > 0 ? ((cur - base) / base) * 100 : (base === 0 ? 0 : (cur > 0 ? 100 : 0))
  const rows = [
    { label: '支出', cur: '¥' + fmtAmount(v.cy.expense), last: '¥' + fmtAmount(v.ly.expense),
      delta: v.cy.expense - v.ly.expense, pct: pct(v.cy.expense, v.ly.expense), isGood: false },
    { label: '收入', cur: '¥' + fmtAmount(v.cy.income), last: '¥' + fmtAmount(v.ly.income),
      delta: v.cy.income - v.ly.income, pct: pct(v.cy.income, v.ly.income), isGood: true },
    { label: '结余', cur: '¥' + fmtAmount(v.cy.balance), last: '¥' + fmtAmount(v.ly.balance),
      delta: v.cy.balance - v.ly.balance, pct: pct(v.cy.balance, v.ly.balance), isGood: true }
  ]
  return rows.map(r => ({ ...r, arrow: r.delta > 0 ? '▲' : (r.delta < 0 ? '▼' : '') }))
})

// v2.3.x: 导航
function goAccounts() { router.push('/accounts') }
function goReport() { router.push('/report') }

const trendData = computed(() => {
  const map = new Map()
  for (const t of txs.value) {
    if (t.type === 'transfer') continue   // v2.3.x: 转账不计入
    const d = new Date(t.occurred_at)
    const k = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    if (!map.has(k)) map.set(k, { day: k, expense: 0, income: 0 })
    const o = map.get(k)
    if (t.type === 'expense') o.expense += t.amount
    else o.income += t.amount
  }
  return [...map.values()].sort((a,b) => a.day.localeCompare(b.day))
})

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['支出', '收入'], bottom: 0, textStyle: { fontSize: 11 } },
  grid: { left: 36, right: 16, top: 16, bottom: 32 },
  xAxis: { type: 'category', data: trendData.value.map(d => d.day.slice(5)), axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
  series: [
    { name: '支出', data: trendData.value.map(d => d.expense.toFixed(2)), type: 'line', smooth: true, lineStyle:{color:'#FF6B6B'}, itemStyle:{color:'#FF6B6B'}, areaStyle:{color:'rgba(255,107,107,0.15)'} },
    { name: '收入', data: trendData.value.map(d => d.income.toFixed(2)),  type: 'line', smooth: true, lineStyle:{color:'#26DE81'}, itemStyle:{color:'#26DE81'}, areaStyle:{color:'rgba(38,222,129,0.15)'} }
  ]
}))

const pieData = computed(() => {
  const map = new Map()
  for (const t of txs.value.filter(x => x.type === 'expense')) {
    map.set(t.category_id, (map.get(t.category_id) || 0) + t.amount)
  }
  return [...map.entries()].map(([id, total]) => {
    const c = store.categories.expense.find(x => x.id === id) || { name: '其他', color: '#BDC3C7' }
    return { name: c.name, value: +total.toFixed(2), itemStyle: { color: c.color } }
  })
})

const pieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
  series: [{
    type: 'pie', radius: ['40%', '70%'], data: pieData.value,
    label: { formatter: '{b}\n{d}%', fontSize: 10 }
  }]
}))

const ranking = computed(() => {
  const map = new Map()
  for (const t of txs.value.filter(x => x.type === 'expense')) {
    map.set(t.category_id, (map.get(t.category_id) || 0) + t.amount)
  }
  return [...map.entries()]
    .map(([cid, total]) => {
      const c = store.categories.expense.find(x => x.id === cid) || { name: '其他', icon: '📦' }
      return { category_id: cid, name: c.name, icon: c.icon, total }
    })
    .sort((a, b) => b.total - a.total)
    .slice(0, 5)
})

const merchantRanking = computed(() => {
  const map = new Map()
  for (const t of txs.value) {
    if (t.type !== 'expense' || !t.merchant) continue
    const m = t.merchant.trim()
    if (!m || m === '未知商户') continue
    if (!map.has(m)) map.set(m, { merchant: m, count: 0, total: 0 })
    const o = map.get(m)
    o.count++
    o.total += t.amount
  }
  return [...map.values()].sort((a, b) => b.total - a.total).slice(0, 5)
})

// v2.2.67: 智能洞察 (顶部 1-2 条)
const topInsights = computed(() => {
  const now = new Date()
  const today0 = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dayOfWeek = (today0.getDay() + 6) % 7
  const weekStart = new Date(today0)
  weekStart.setDate(weekStart.getDate() - dayOfWeek)
  const lastWeekStart = new Date(weekStart)
  lastWeekStart.setDate(lastWeekStart.getDate() - 7)
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)

  const thisWeek = []
  const lastWeek = []
  let todayExpense = 0, todayCount = 0
  for (const t of txs.value) {
    if (t.type !== 'expense') continue
    const dt = new Date(t.occurred_at)
    if (dt >= weekStart && dt < new Date(weekStart.getTime() + 7 * 86400000)) thisWeek.push(t)
    if (dt >= lastWeekStart && dt < weekStart) lastWeek.push(t)
    if (dt >= today0) { todayExpense += t.amount; todayCount++ }
  }
  const sum = (arr) => arr.reduce((s, t) => s + t.amount, 0)
  const thisWeekTotal = sum(thisWeek)
  const lastWeekTotal = sum(lastWeek)
  const out = []
  // 优先级 1: 今日消费 (简洁)
  if (todayCount > 0) {
    out.push({
      icon: '💡', title: '今日已花 ¥' + todayExpense.toFixed(2), sub: todayCount + ' 笔',
      bg1: '#EEF2FF', bg2: '#E0E7FF'
    })
  }
  // 优先级 2: 本周 vs 上周 (变化大才显示)
  if (thisWeekTotal > 0 && lastWeekTotal > 0) {
    const diff = thisWeekTotal - lastWeekTotal
    const pct = (diff / lastWeekTotal * 100)
    if (Math.abs(pct) > 8) {
      out.push({
        icon: diff > 0 ? '📈' : '📉',
        title: '本周' + (diff > 0 ? '多花' : '少花') + ' ¥' + Math.abs(diff).toFixed(0),
        sub: (diff > 0 ? '+' : '') + pct.toFixed(1) + '%(上周 ¥' + lastWeekTotal.toFixed(0) + ')',
        bg1: diff > 0 ? '#FEE2E2' : '#D1FAE5',
        bg2: diff > 0 ? '#FECACA' : '#A7F3D0'
      })
    }
  }
  return out.slice(0, 2)
})

// v2.2.67: 月对月对比
// v2.2.80: 改用独立的 mcTxs(近 2 月全量) ,不再被日/周 range 截断导致上月归零
const monthCompare = computed(() => {
  const pool = mcTxs.value?.length ? mcTxs.value : txs.value
  if (!pool.length) return null
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  const lastMonthEnd = monthStart

  let thisSum = 0, thisCount = 0
  let lastSum = 0, lastCount = 0
  for (const t of pool) {
    if (t.type !== 'expense') continue
    const dt = new Date(t.occurred_at)
    if (dt >= monthStart) { thisSum += t.amount; thisCount++ }
    if (dt >= lastMonthStart && dt < lastMonthEnd) { lastSum += t.amount; lastCount++ }
  }
  if (thisCount === 0 && lastCount === 0) return null

  const days = now.getDate()
  const lastMonthDays = new Date(now.getFullYear(), now.getMonth(), 0).getDate()

  const diff = thisSum - lastSum
  const diffPct = lastSum > 0 ? (diff / lastSum * 100) : 0
  const barPct = lastSum > 0 ? Math.min(200, (thisSum / lastSum * 100)) : 100

  return {
    thisMonth: thisSum,
    lastMonth: lastSum,
    diff,
    diffPct,
    barPct,
    thisAvg: thisSum / days,
    lastAvg: lastSum / lastMonthDays
  }
})

// v2.2.67: 日历热力图(独立数据 — 不受 range 影响)
const calendarData = computed(() => {
  const end = new Date()
  end.setHours(0, 0, 0, 0)
  const start = new Date(end)
  start.setDate(start.getDate() - 12 * 7 - 7)

  const dayMap = new Map()
  for (const t of heatmapTxs.value) {  // v2.2.74: 用独立全量数据
    const d = new Date(t.occurred_at)
    const k = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    dayMap.set(k, (dayMap.get(k) || 0) + t.amount)
  }
  const amounts = [...dayMap.values()]
  const max = Math.max(1, ...amounts)

  const firstDay = new Date(start)
  const dayOfWeek = (firstDay.getDay() + 6) % 7
  firstDay.setDate(firstDay.getDate() - dayOfWeek)

  const weeks = []
  const today = new Date(end)
  const cursor = new Date(firstDay)
  while (cursor <= today || weeks.length < 12) {
    const week = []
    for (let i = 0; i < 7; i++) {
      const d = new Date(cursor)
      const k = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
      const amount = dayMap.get(k) || 0
      let level = 0
      if (amount > 0) {
        const ratio = amount / max
        if (ratio < 0.25) level = 1
        else if (ratio < 0.5) level = 2
        else if (ratio < 0.75) level = 3
        else level = 4
      }
      week.push({ date: k, amount, level })
      cursor.setDate(cursor.getDate() + 1)
    }
    weeks.push(week)
    if (weeks.length >= 13) break
  }
  return { weeks: weeks.slice(-12), max }
})
</script>

<style scoped>
.stats .page-header { margin-bottom: 12px; }
.range-tabs { padding: 8px 10px; display: flex; gap: 6px; }
.summary-card { padding: 16px 14px; }
.big-amount { font-size: 30px; font-weight: 700; margin-top: 4px; }
.chart-card .card-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; color: #2C3E50; }
.chart-row { display: flex; gap: 8px; }
.rank-item { display:flex; align-items:center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.rank-item:last-child { border-bottom: none; }
.rank-no { width: 18px; text-align:center; color: var(--primary); font-weight: 600; font-size: 12px; }
.rank-icon { font-size: 18px; }
.rank-name { flex: 1; font-size: 13px; }
.rank-amount { font-weight: 600; font-size: 13px; }

/* === 智能洞察 === */
.insight-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: var(--radius);
  margin-bottom: 10px;
  border: 1px solid rgba(99,102,241,0.1);
  box-shadow: var(--shadow-sm);
  animation: fadeInUp 0.4s ease-out;
}
.insight-icon { font-size: 26px; flex-shrink: 0; }
.insight-text { flex: 1; min-width: 0; }
.insight-title { font-size: 13px; font-weight: 700; color: #1F2937; }
.insight-sub { font-size: 11px; color: #4B5563; margin-top: 1px; }

/* === 月对月对比 === */
.mc-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; font-size: 13px; }
.mc-label { font-size: 11px; }
.mc-amount { font-weight: 700; font-size: 14px; }
.mc-diff { font-size: 14px; padding: 3px 8px; border-radius: 6px; }
.mc-diff.amount-neg { background: rgba(239, 68, 68, 0.1); }
.mc-diff.amount-pos { background: rgba(16, 185, 129, 0.1); }
.mc-progress { height: 5px; background: var(--border-light); border-radius: 999px; overflow: hidden; margin-top: 6px; }
.mc-bar { height: 100%; border-radius: 999px; transition: width 0.4s ease; }
.mc-bar-up { background: linear-gradient(90deg, #F59E0B, #EF4444); }
.mc-bar-down { background: linear-gradient(90deg, #10B981, #059669); }

/* === 热力图 === */
.heatmap { display: flex; gap: 3px; margin-top: 6px; overflow-x: auto; padding-bottom: 4px; }
.hm-day-labels {
  display: flex; flex-direction: column; gap: 2px;
  font-size: 9px; color: var(--text-3);
  justify-content: space-around;
  padding: 1px 0;
}
.hm-day-labels span { height: 12px; line-height: 12px; }
.hm-grid { display: flex; gap: 2px; }
.hm-col { display: flex; flex-direction: column; gap: 2px; }
.hm-cell {
  width: 12px; height: 12px;
  border-radius: 2px;
  background: var(--border-light);
  transition: transform 0.15s;
}
.hm-cell:hover { transform: scale(1.3); }
.lvl-0 { background: var(--border-light); }
.lvl-1 { background: #C7D2FE; }
.lvl-2 { background: #818CF8; }
.lvl-3 { background: #6366F1; }
.lvl-4 { background: #4338CA; }
.hm-legend {
  display: flex; align-items: center; gap: 3px;
  margin-top: 8px;
  font-size: 10px;
  color: var(--text-3);
  justify-content: flex-end;
}
.hm-legend .hm-cell { width: 8px; height: 8px; }

/* === v2.3.x: 净资产 === */
.link-all { font-size: 12px; color: var(--primary); font-weight: 500; }
.nw-total { font-size: 26px; font-weight: 800; color: var(--text-1); margin-bottom: 8px; font-variant-numeric: tabular-nums; }
.nw-list { display: flex; flex-direction: column; }
.nw-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.nw-row:last-child { border-bottom: none; }
.nw-icon {
  width: 30px; height: 30px; border-radius: 8px; background: var(--primary-bg);
  display: flex; align-items: center; justify-content: center; font-size: 15px; flex-shrink: 0;
}
.nw-name { flex: 1; font-size: 13px; color: var(--text-2); }
.nw-balance { font-size: 13px; font-weight: 700; color: var(--text-1); font-variant-numeric: tabular-nums; }

/* === v2.3.x: 年累计 === */
.yc-row { display: flex; align-items: baseline; gap: 6px; padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--border-light); }
.yc-row:last-child { border-bottom: none; }
.yc-label { width: 34px; color: var(--text-3); flex-shrink: 0; }
.yc-cur { flex: 1; color: var(--text-2); font-size: 12px; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.yc-cur b { color: var(--text-1); font-weight: 700; }
.yc-pct { font-weight: 700; font-variant-numeric: tabular-nums; flex-shrink: 0; }
.yc-pct.pos { color: #10B981; }
.yc-pct.neg { color: #EF4444; }
.yc-pct.flat { color: var(--text-3); }

/* === v2.3.x: 自定义报表按钮 === */
.report-btn {
  display: block; width: 100%;
  background: var(--gradient); color: #fff;
  border: none; border-radius: var(--radius-lg);
  padding: 13px; font-size: 15px; font-weight: 700;
  box-shadow: var(--shadow-lg); cursor: pointer;
  margin-top: 14px;
}
.report-btn:active { transform: scale(0.98); }
</style>
