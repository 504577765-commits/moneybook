<template>
  <div class="page report">
    <!-- 头部: 返回 + 标题 -->
    <div class="page-header fade-in-up">
      <button class="back-btn" @click="goBack" aria-label="返回">❮</button>
      <span>自定义报表</span>
    </div>

    <!-- 时间范围 -->
    <div class="card fade-in-up" style="animation-delay: 0.05s">
      <div class="rb-label">时间范围</div>
      <div class="range-chips">
        <span v-for="r in rangeOptions" :key="r.v"
          :class="['chip', rangeType === r.v ? 'active' : '']"
          @click="onRangeChange(r.v)">{{ r.label }}</span>
      </div>
      <div v-if="rangeType === 'custom'" class="muted rb-custom-hint">
        自定义 {{ customStart || '—' }} ~ {{ customEnd || '—' }}
      </div>
    </div>

    <!-- 筛选条件 -->
    <div class="card fade-in-up" style="animation-delay: 0.08s">
      <div class="rb-label">筛选</div>
      <div class="filter-row">
        <span class="f-label">类型</span>
        <select v-model="typeFilter" class="f-select">
          <option value="all">全部</option>
          <option value="expense">支出</option>
          <option value="income">收入</option>
        </select>
      </div>
      <div class="filter-row">
        <span class="f-label">分类</span>
        <select v-model="categoryFilter" class="f-select">
          <option value="all">全部</option>
          <option v-for="c in catOptions" :key="c.id" :value="c.id">{{ c.icon }} {{ c.name }}</option>
        </select>
      </div>
      <div class="filter-row">
        <span class="f-label">账户</span>
        <select v-model="accountFilter" class="f-select">
          <option value="all">全部</option>
          <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.icon || '💰' }} {{ a.name }}</option>
        </select>
      </div>
    </div>

    <!-- 汇总 -->
    <div class="card summary-card fade-in-up" style="animation-delay: 0.1s">
      <div class="rb-label">汇总</div>
      <div class="rb-3">
        <div class="rb-stat"><div class="muted">总收入</div><div class="rb-val pos">¥{{ fmtAmount(result.totalIncome) }}</div></div>
        <div class="rb-stat"><div class="muted">总支出</div><div class="rb-val neg">¥{{ fmtAmount(result.totalExpense) }}</div></div>
        <div class="rb-stat"><div class="muted">结余</div><div class="rb-val">¥{{ fmtAmount(result.balance) }}</div></div>
      </div>
      <div class="rb-count muted">{{ result.txCount }} 笔交易</div>
    </div>

    <!-- 分类汇总 + 柱状图 -->
    <div class="card chart-card fade-in-up" style="animation-delay: 0.12s">
      <div class="rb-label">分类汇总</div>
      <v-chart :option="barOption" autoresize style="height: 200px;" v-if="result.cats.length" />
      <div v-else class="empty"><div class="ico">📊</div><div>无匹配数据</div></div>
      <div class="rb-cat-list" v-if="result.cats.length">
        <div v-for="c in result.cats" :key="c.id" class="rb-cat-row">
          <span class="rb-cat-icon" :style="{ background: catInfo(c.id).color + '22' }">{{ catInfo(c.id).icon }}</span>
          <span class="rb-cat-name">{{ catInfo(c.id).name }}</span>
          <span class="rb-cat-count muted">{{ c.count }} 笔</span>
          <span class="rb-cat-amt">¥{{ fmtAmount(c.amount) }}</span>
        </div>
      </div>
    </div>

    <!-- 保存 / 历史视图 -->
    <div class="card fade-in-up" style="animation-delay: 0.14s">
      <div class="rb-label">历史视图</div>
      <select v-model="currentViewId" class="f-select full" @change="applyView">
        <option :value="-1">选择已保存的视图…</option>
        <option v-for="(v, i) in savedViews" :key="i" :value="i">{{ v.name }}（{{ rangeLabelText(v) }}）</option>
      </select>
      <div class="rb-actions">
        <button class="btn-save" @click="saveReport">💾 保存此报表</button>
        <button class="btn-del" v-if="currentViewId >= 0" @click="deleteView">🗑 删除</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '../stores/book'
import { listTransactions } from '../db'
import { fmtAmount } from '../utils/format'
import { showPrompt, showToast, showConfirm } from '../utils/dialog'
import dayjs from 'dayjs'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const store = useBookStore()

const VIEW_KEY = 'moneybook_report_views'

const rangeOptions = [
  { v: 'month', label: '本月' },
  { v: 'lastMonth', label: '上月' },
  { v: '3m', label: '近3月' },
  { v: '6m', label: '近6月' },
  { v: 'year', label: '今年' },
  { v: 'custom', label: '自定义' }
]
const rangeType = ref('month')
const customStart = ref('')
const customEnd = ref('')
let prevRange = 'month'

const typeFilter = ref('all')
const categoryFilter = ref('all')
const accountFilter = ref('all')

const accounts = computed(() => store.accounts)
const catOptions = computed(() => {
  if (typeFilter.value === 'expense') return store.categories.expense || []
  if (typeFilter.value === 'income') return store.categories.income || []
  return [...(store.categories.expense || []), ...(store.categories.income || [])]
})

function parseDate(str) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(str || ''))) return null
  const d = new Date(str + 'T00:00:00')
  return isNaN(d.getTime()) ? null : d
}

function onRangeChange(v) {
  if (v !== 'custom') { rangeType.value = v; prevRange = v; return }
  ;(async () => {
    const s = await showPrompt('开始日期 (yyyy-mm-dd)', customStart.value || dayjs().startOf('month').format('YYYY-MM-DD'), '自定义范围', { placeholder: '如 2026-01-01' })
    if (s === null) { rangeType.value = prevRange; return }
    const e = await showPrompt('结束日期 (yyyy-mm-dd)', customEnd.value || dayjs().format('YYYY-MM-DD'), '自定义范围', { placeholder: '如 2026-12-31' })
    if (e === null) { rangeType.value = prevRange; return }
    const sd = parseDate(s), ed = parseDate(e)
    if (!sd || !ed || sd > ed) { showToast('日期格式错误或开始晚于结束', 'error'); rangeType.value = prevRange; return }
    customStart.value = s
    customEnd.value = e
    prevRange = v
    rangeType.value = v
  })()
}

function currentPreset() {
  const now = new Date()
  const y = now.getFullYear(), m = now.getMonth()
  switch (rangeType.value) {
    case 'month': return [new Date(y, m, 1).getTime(), new Date(y, m + 1, 1).getTime()]
    case 'lastMonth': return [new Date(y, m - 1, 1).getTime(), new Date(y, m, 1).getTime()]
    case '3m': return [new Date(y, m - 2, 1).getTime(), new Date(y, m + 1, 1).getTime()]
    case '6m': return [new Date(y, m - 5, 1).getTime(), new Date(y, m + 1, 1).getTime()]
    case 'year': return [new Date(y, 0, 1).getTime(), new Date(y + 1, 0, 1).getTime()]
    case 'custom': {
      const sd = parseDate(customStart.value), ed = parseDate(customEnd.value)
      if (!sd || !ed) return null
      return [sd.getTime(), new Date(ed.getFullYear(), ed.getMonth(), ed.getDate() + 1).getTime()]
    }
    default: return null
  }
}

const txs = ref([])
async function loadReport() {
  const range = currentPreset()
  if (!range) { txs.value = []; return }
  txs.value = await listTransactions({ startTs: range[0], endTs: range[1], limit: 5000 })
}
watch([() => rangeType.value, () => customStart.value, () => customEnd.value], loadReport, { immediate: true })
watch(typeFilter, () => { if (categoryFilter.value !== 'all') categoryFilter.value = 'all' })

const result = computed(() => {
  let totalIncome = 0, totalExpense = 0, txCount = 0
  const catMap = new Map()
  for (const t of txs.value) {
    if (t.type === 'transfer') continue
    if (typeFilter.value !== 'all' && t.type !== typeFilter.value) continue
    if (accountFilter.value !== 'all' && t.account_id !== accountFilter.value) continue
    const isIncome = t.type === 'income'
    txCount++
    const parts = (t.splits && t.splits.length) ? t.splits : [{ category_id: t.category_id, amount: t.amount }]
    for (const p of parts) {
      const cid = p.category_id || 'other'
      if (categoryFilter.value !== 'all' && cid !== categoryFilter.value) continue
      const amt = Number(p.amount) || 0
      if (isIncome) totalIncome += amt; else totalExpense += amt
      const o = catMap.get(cid) || { id: cid, count: 0, amount: 0 }
      o.count++
      if (isIncome) o.amount += amt; else o.amount -= amt
      catMap.set(cid, o)
    }
  }
  const cats = [...catMap.values()].sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
  return { totalIncome, totalExpense, balance: totalIncome - totalExpense, txCount, cats }
})

function catInfo(cid) {
  for (const group of [store.categories.expense, store.categories.income]) {
    const c = (group || []).find(x => x.id === cid)
    if (c) return { name: c.name, icon: c.icon, color: c.color }
  }
  return { name: '其他', icon: '📦', color: '#94A3B8' }
}

const barOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 20, bottom: 28 },
  xAxis: { type: 'category', data: result.value.cats.map(c => catInfo(c.id).name), axisLabel: { fontSize: 10, interval: 0, rotate: result.value.cats.length > 4 ? 30 : 0 } },
  yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
  series: [{ name: '金额', type: 'bar', data: result.value.cats.map(c => Math.abs(c.amount).toFixed(2)), itemStyle: { color: '#6366F1', borderRadius: [4, 4, 0, 0] } }]
}))

// ===== 保存 / 历史视图 =====
const savedViews = ref([])
const currentViewId = ref(-1)

function loadViews() {
  try { savedViews.value = JSON.parse(localStorage.getItem(VIEW_KEY) || '[]') } catch { savedViews.value = [] }
}

async function saveReport() {
  const name = await showPrompt('给这个报表命名', '', '保存报表', { placeholder: '如：本月餐饮' })
  if (name === null) return
  const view = {
    name: String(name).trim() || '报表 ' + (savedViews.value.length + 1),
    rangeType: rangeType.value,
    customStart: customStart.value,
    customEnd: customEnd.value,
    typeFilter: typeFilter.value,
    categoryFilter: categoryFilter.value,
    accountFilter: accountFilter.value,
    ts: Date.now()
  }
  savedViews.value.push(view)
  localStorage.setItem(VIEW_KEY, JSON.stringify(savedViews.value))
  showToast('已保存报表', 'success')
}

function applyView(e) {
  const i = Number(currentViewId.value)
  if (i < 0 || !savedViews.value[i]) return
  const v = savedViews.value[i]
  rangeType.value = v.rangeType || 'month'
  customStart.value = v.customStart || ''
  customEnd.value = v.customEnd || ''
  typeFilter.value = v.typeFilter || 'all'
  categoryFilter.value = v.categoryFilter || 'all'
  accountFilter.value = v.accountFilter || 'all'
}

async function deleteView() {
  const i = Number(currentViewId.value)
  if (i < 0) return
  if (!await showConfirm('删除该视图?', '确认')) return
  savedViews.value.splice(i, 1)
  localStorage.setItem(VIEW_KEY, JSON.stringify(savedViews.value))
  currentViewId.value = -1
  showToast('已删除', 'success')
}

function rangeLabelText(v) {
  const m = { month: '本月', lastMonth: '上月', '3m': '近3月', '6m': '近6月', year: '今年', custom: '自定义' }
  const t = { all: '全部', expense: '支出', income: '收入' }
  return (m[v.rangeType] || v.rangeType) + ' · ' + (t[v.typeFilter] || v.typeFilter)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

onMounted(() => {
  loadViews()
  if (store.categories.expense.length === 0 && store.ready) store.refreshCategories()
})
</script>

<style scoped>
.report { padding: 12px 14px 100px; }
.page-header { display: flex; align-items: center; gap: 6px; }
.back-btn {
  width: 32px; height: 32px; border-radius: 10px;
  background: var(--card); color: var(--text-1);
  border: 1px solid var(--border-light); box-shadow: var(--shadow-sm);
  font-size: 16px; display: flex; align-items: center; justify-content: center;
}

.rb-label { font-size: 12px; font-weight: 600; color: var(--text-3); margin-bottom: 10px; }
.range-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  padding: 5px 12px; font-size: 12px; font-weight: 500;
  background: var(--card); border: 1px solid var(--border-light);
  border-radius: 16px; color: var(--text-2); cursor: pointer;
}
.chip.active {
  background: linear-gradient(135deg, var(--primary), #8B5CF6);
  border-color: transparent; color: #fff;
}
.rb-custom-hint { font-size: 11px; margin-top: 8px; }

.filter-row { display: flex; align-items: center; padding: 7px 0; }
.f-label { width: 44px; font-size: 13px; color: var(--text-2); flex-shrink: 0; }
.f-select {
  flex: 1; font-size: 13px; color: var(--text-1);
  background: var(--bg-2); border: 1px solid var(--border-light);
  border-radius: 10px; padding: 8px 10px;
}
.f-select.full { width: 100%; }

.rb-3 { display: flex; gap: 8px; margin-bottom: 6px; }
.rb-stat { flex: 1; text-align: center; }
.rb-val { font-size: 17px; font-weight: 800; margin-top: 3px; font-variant-numeric: tabular-nums; }
.rb-val.pos { color: #10B981; }
.rb-val.neg { color: #EF4444; }
.rb-count { font-size: 11px; text-align: center; }

.rb-cat-list { margin-top: 10px; }
.rb-cat-row { display: flex; align-items: center; gap: 8px; padding: 7px 0; border-top: 1px solid var(--border-light); }
.rb-cat-row:first-child { border-top: none; }
.rb-cat-icon {
  width: 28px; height: 28px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0;
}
.rb-cat-name { flex: 1; font-size: 13px; color: var(--text-1); }
.rb-cat-count { font-size: 11px; }
.rb-cat-amt { font-size: 13px; font-weight: 700; color: var(--text-1); font-variant-numeric: tabular-nums; }

.rb-actions { display: flex; gap: 8px; margin-top: 12px; }
.btn-save {
  flex: 1; background: var(--gradient); color: #fff;
  border: none; border-radius: var(--radius); padding: 12px;
  font-size: 14px; font-weight: 700; cursor: pointer;
}
.btn-del {
  flex-shrink: 0; background: rgba(239,68,68,0.1); color: #EF4444;
  border: 1px solid rgba(239,68,68,0.25); border-radius: var(--radius);
  padding: 12px 14px; font-size: 14px; cursor: pointer;
}
.report .card { margin-bottom: 12px; }
</style>