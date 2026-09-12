<template>
  <div class="page home">
    <!-- 顶部问候 -->
    <div class="greeting fade-in-up">
      <div>
        <div class="greet-hi">{{ greeting }}</div>
        <div class="greet-date">{{ todayStr }}</div>
      </div>
      <div class="greet-emoji">{{ greetEmoji }}</div>
    </div>

    <!-- 合并 Hero + 预算条 -->
    <div class="hero fade-in-up" style="animation-delay: 0.05s">
      <div class="hero-bg"></div>
      <div class="hero-pattern"></div>
      <div class="hero-content">
        <div class="hero-top">
          <div>
            <div class="hero-label">{{ rangeLabel }}</div>
            <div :class="['hero-amount']">
              <span class="currency">¥</span> {{ monthSpent.toFixed(2) }}
            </div>
          </div>
          <div class="hero-stats">
            <div class="hs-item">
              <div class="hs-label">支出</div>
              <div class="hs-val">¥{{ fmtAmount(summary.expense) }}</div>
            </div>
            <div class="hs-divider"></div>
            <div class="hs-item">
              <div class="hs-label">收入</div>
              <div class="hs-val">¥{{ fmtAmount(summary.income) }}</div>
            </div>
          </div>
        </div>
        <!-- 内嵌预算条 -->
        <div v-if="monthlyBudget > 0" class="hero-budget">
          <div class="hb-text">
            <span>本月已花 ¥{{ monthSpent.toFixed(0) }}</span>
            <span v-if="budgetPct > 100" style="color:#FECACA;font-weight:700;">⚠️ 超</span>
            <span v-else>剩 ¥{{ (monthlyBudget - monthSpent).toFixed(0) }}</span>
          </div>
          <div class="hb-bar">
            <div :class="['hb-fill', budgetPct > 100 ? 'over' : budgetPct > 80 ? 'warn' : '']"
                 :style="`width: ${Math.min(budgetPct, 100)}%`"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 时间范围 + 跳转统计 -->
    <div class="range-row fade-in-up" style="animation-delay: 0.1s">
      <div class="range-chips">
        <span v-for="r in ranges" :key="r.v"
          :class="['chip', store.currentRange === r.v ? 'active':'']"
          @click="setRange(r.v)">{{ r.label }}</span>
      </div>
      <button class="nav-btn tappable" @click="prev" aria-label="上一段">‹</button>
      <button class="nav-btn tappable" @click="next" aria-label="下一段">›</button>
    </div>

    <!-- 按日折叠:日范围全展开,周/月范围默认展开今天+昨天,其他折叠 -->
    <div v-if="grouped.length > 0" class="recent-section fade-in-up" style="animation-delay: 0.12s">
      <div class="recent-title">
        <span>{{ store.currentRange === 'day' ? '今日明细' : '按日查看' }}</span>
        <router-link to="/records" class="link-all">全部 ›</router-link>
      </div>
      <div class="day-groups">
        <div v-for="g in grouped" :key="g.date" class="day-fold">
          <div class="day-fold-head" @click="toggleDay(g.date)">
            <div class="df-left">
              <span class="df-chev" :class="{open: isDayOpen(g.date)}">›</span>
              <span class="df-name">{{ formatDay(g.date) }}</span>
              <span class="df-count">{{ g.items.length }} 笔</span>
            </div>
            <div class="df-right">
              <span v-if="dayExpense(g.items) > 0" class="amount-neg">-¥{{ fmtAmount(dayExpense(g.items)) }}</span>
              <span v-if="dayIncome(g.items) > 0" class="amount-pos" style="margin-left:6px;">+¥{{ fmtAmount(dayIncome(g.items)) }}</span>
            </div>
          </div>
          <transition name="day-expand">
          <div v-show="isDayOpen(g.date)" class="day-fold-body">
            <div v-for="tx in g.items" :key="tx.id" class="recent-item tappable" @click="openEdit(tx)">
              <div class="cat-mini" :style="{background: catColor(tx.category_id, tx.type)}">
                <span class="cat-emoji-sm">{{ catIcon(tx.category_id, tx.type) }}</span>
              </div>
              <div class="recent-info">
                <div class="recent-merchant">{{ tx.merchant || catName(tx.category_id, tx.type) }}</div>
                <div class="recent-time">{{ fmtTime(tx.occurred_at, 'HH:mm') }} · {{ catName(tx.category_id, tx.type) }}</div>
                <div v-if="tx.note" class="note-badge">{{ tx.note }}</div>
              </div>
              <div :class="['recent-amount', tx.type === 'expense' ? 'neg' : 'pos']">
                {{ tx.type === 'expense' ? '-' : '+' }}¥{{ fmtAmount(tx.amount) }}
              </div>
            </div>
          </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else class="empty card fade-in-up" style="animation-delay: 0.12s">
      <span class="ico">📒</span>
      <div class="empty-title">还没有记账记录</div>
      <div class="muted">APP 会自动监听银行短信并记账</div>
    </div>

    <!-- v2.2.77: 手动记账按钮(右下角悬浮) -->
    <button class="fab-add" @click="openAdd" aria-label="手动记一笔">+</button>

    <EditTxDialog :visible="editVisible" :tx="editingTx" @close="closeEdit" @saved="onSaved" />
    <AddTxDialog :visible="addVisible" @close="closeAdd" @saved="onAdded" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useBookStore } from '../stores/book'
import { fmtAmount, fmtTime, groupByDate } from '../utils/format'
import { listTransactions } from '../db'
import dayjs from 'dayjs'
import EditTxDialog from '../components/EditTxDialog.vue'
import AddTxDialog from '../components/AddTxDialog.vue'

const store = useBookStore()
const ranges = [
  { v: 'day', label: '日' },
  { v: 'week', label: '周' },
  { v: 'month', label: '月' }
]

const summary = computed(() => {
  let expense = 0, income = 0
  for (const t of store.transactions) {
    if (t.type === 'expense') expense += t.amount
    else income += t.amount
  }
  return { expense, income, balance: income - expense }
})

const grouped = computed(() => groupByDate(store.transactions))

// v2.2.73: 按日折叠 — 展开的日期集合
const expandedDays = ref(new Set())
function isDayOpen(date) { return expandedDays.value.has(date) }
function toggleDay(date) {
  if (expandedDays.value.has(date)) expandedDays.value.delete(date)
  else expandedDays.value.add(date)
  // 触发响应式
  expandedDays.value = new Set(expandedDays.value)
}
function dayExpense(items) {
  let s = 0; for (const t of items) if (t.type === 'expense') s += t.amount; return s
}
function dayIncome(items) {
  let s = 0; for (const t of items) if (t.type === 'income') s += t.amount; return s
}

// 默认展开:日范围全展开,周/月范围只展开今天+昨天
watch([() => store.currentRange, () => store.currentAnchor, grouped], () => {
  const open = new Set()
  const today = dayjs().format('YYYY-MM-DD')
  const yest = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  if (store.currentRange === 'day') {
    // 日范围:全部展开
    for (const g of grouped.value) open.add(g.date)
  } else {
    // 周/月范围:展开今天+昨天
    for (const g of grouped.value) {
      if (g.date === today || g.date === yest) open.add(g.date)
    }
  }
  expandedDays.value = open
}, { immediate: true })

const rangeLabel = computed(() => {
  const a = store.currentAnchor
  // v2.2.80: 主数字固定为"当月(当前查看的月份)已花"
  return dayjs(a).format('M月') + '已花'
})

const currentRange = computed(() => store.currentRange)

function setRange(v) { store.setRange(v) }
function prev() {
  const offset = store.currentRange === 'day' ? -86400000
    : store.currentRange === 'week' ? -7 * 86400000
    : -30 * 86400000
  store.setAnchor(store.currentAnchor + offset)
}
function next() {
  const offset = store.currentRange === 'day' ? 86400000
    : store.currentRange === 'week' ? 7 * 86400000
    : 30 * 86400000
  store.setAnchor(store.currentAnchor + offset)
}

function catIcon(cid, type) {
  const c = store.categories[type]?.find(x => x.id === cid)
  return c?.icon || '📦'
}
function catName(cid, type) {
  const c = store.categories[type]?.find(x => x.id === cid)
  return c?.name || '其他'
}
function catColor(cid, type) {
  const c = store.categories[type]?.find(x => x.id === cid)
  return c?.color || '#94A3B8'
}

function formatDay(d) {
  const today = dayjs().format('YYYY-MM-DD')
  const yest  = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  if (d === today) return '今天'
  if (d === yest)  return '昨天'
  return dayjs(d).format('MM月DD日')
}

// v2.2.71: 顶部问候
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了 🌙'
  if (h < 11) return '上午好 ☀️'
  if (h < 14) return '中午好 🌞'
  if (h < 18) return '下午好 ☕'
  if (h < 22) return '晚上好 🌆'
  return '夜深了 🌙'
})
const greetEmoji = computed(() => {
  const h = new Date().getHours()
  if (h < 6 || h >= 22) return '🌙'
  if (h < 11) return '☀️'
  if (h < 14) return '🌞'
  if (h < 18) return '☕'
  return '🌆'
})
const todayStr = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日 周${['日','一','二','三','四','五','六'][d.getDay()]}`
})

// v2.2.71: 月度预算
const monthlyBudget = ref(parseInt(localStorage.getItem('moneybook_monthly_budget') || '0') || 0)
const monthTxs = ref([])
const monthSpent = computed(() => {
  let s = 0
  for (const t of monthTxs.value) {
    if (t.type === 'expense') s += t.amount
  }
  return s
})
const budgetPct = computed(() => monthlyBudget.value > 0 ? (monthSpent.value / monthlyBudget.value) * 100 : 0)
// v2.2.80: 当月已花跟随当前查看的月份(currentAnchor)刷新,切月/跨月自动重拉
async function loadMonthTxs() {
  const now = new Date()
  // 当前查看的月份(= 按 currentAnchor 所在月)
  const anchor = new Date(store.currentAnchor)
  const anchorMonthStart = new Date(anchor.getFullYear(), anchor.getMonth(), 1).getTime()
  const anchorIsCurrentMonth =
    anchor.getFullYear() === now.getFullYear() && anchor.getMonth() === now.getMonth()
  // 本月:截止到当前时刻;其他月:截止到该月结束
  const end = anchorIsCurrentMonth
    ? now.getTime()
    : new Date(anchor.getFullYear(), anchor.getMonth() + 1, 1).getTime()
  monthTxs.value = await listTransactions({ startTs: anchorMonthStart, endTs: end, limit: 5000 })
}
watch(() => store.ready, (ready) => {
  if (ready) loadMonthTxs()
})
// v2.2.80: 切换日/周/月 或 前后翻动锚点时,刷新"当月已花"
watch([() => store.currentAnchor, () => store.currentRange], () => {
  if (store.ready) loadMonthTxs()
})

// v2.2.75: 监听预算变化(从 Me.vue 设置后)
function onBudgetChanged(e) {
  monthlyBudget.value = e.detail.value
  loadMonthTxs()
}
window.addEventListener('moneybook:budget-changed', onBudgetChanged)

// v2.2.76: 新交易入账后,重拉月数据,预算进度实时更新
function onTxAdded() {
  loadMonthTxs()
  store.refreshTransactions() // 同时刷新当前 range 列表
}
window.addEventListener('moneybook:tx-added', onTxAdded)
onUnmounted(() => {
  window.removeEventListener('moneybook:budget-changed', onBudgetChanged)
  window.removeEventListener('moneybook:tx-added', onTxAdded)
})

onMounted(() => {
  if (store.ready) {
    store.refreshTransactions()
    loadMonthTxs() // v2.2.80: 修复从统计页返回后"本月已花"归零
  }
})

// v2.2.77: 编辑 + 手动记账弹窗
const editVisible = ref(false)
const editingTx = ref({})
function openEdit(tx) { editingTx.value = { ...tx }; editVisible.value = true }
function closeEdit() { editVisible.value = false }
async function onSaved(updated) {
  const idx = store.transactions.findIndex(t => t.id === updated.id)
  if (idx >= 0) store.transactions[idx] = { ...store.transactions[idx], ...updated }
  await store.refreshTransactions()
  loadMonthTxs()
}

const addVisible = ref(false)
function openAdd() { addVisible.value = true }
function closeAdd() { addVisible.value = false }
async function onAdded() {
  await store.refreshTransactions()
  loadMonthTxs()
}
</script>

<style scoped>
.home { padding: 12px 14px 100px; }

/* === v2.2.71 顶部问候 === */
.greeting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 0 2px;
}
.greet-hi { font-size: 16px; font-weight: 700; color: var(--text-1); }
.greet-date { font-size: 12px; color: var(--text-3); margin-top: 2px; }
.greet-emoji { font-size: 32px; }

/* === v2.2.71 Hero 紧凑 === */
.hero {
  position: relative;
  background: linear-gradient(135deg, #6366F1 0%, #8B5CF6 50%, #EC4899 100%);
  color: #fff;
  border-radius: 20px;
  padding: 16px 18px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(99,102,241,0.25);
  margin-bottom: 12px;
}
.hero-bg {
  position: absolute; inset: 0;
  background: radial-gradient(circle at 80% -20%, rgba(255,255,255,0.3) 0%, transparent 50%);
  pointer-events: none;
}
.hero-pattern {
  position: absolute; inset: 0;
  background-image: radial-gradient(circle at 1px 1px, rgba(255,255,255,0.15) 1px, transparent 0);
  background-size: 20px 20px;
  opacity: 0.35;
  pointer-events: none;
}
.hero-content { position: relative; z-index: 1; }
.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.hero-label { font-size: 11px; opacity: 0.85; margin-bottom: 2px; }
.hero-amount {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -1px;
  text-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.hero-amount .currency { font-size: 18px; font-weight: 600; opacity: 0.9; margin-right: 2px; }
.hero-amount.neg { color: #FFE4E4; }

.hero-stats { display: flex; gap: 0; align-items: center; }
.hs-item { padding: 0 8px; }
.hs-label { font-size: 10px; opacity: 0.85; margin-bottom: 1px; }
.hs-val { font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; }
.hs-divider { width: 1px; height: 24px; background: rgba(255,255,255,0.25); }

/* === v2.2.71 内嵌预算条 === */
.hero-budget {
  background: rgba(0,0,0,0.15);
  border-radius: 10px;
  padding: 8px 12px;
  margin-top: 4px;
}
.hb-text { display: flex; justify-content: space-between; font-size: 11px; margin-bottom: 4px; opacity: 0.95; }
.hb-bar { height: 4px; background: rgba(255,255,255,0.2); border-radius: 999px; overflow: hidden; }
.hb-fill { height: 100%; background: rgba(255,255,255,0.85); border-radius: 999px; transition: width 0.4s; }
.hb-fill.warn { background: #FBBF24; }
.hb-fill.over { background: #F87171; }

/* === v2.2.71 时间范围 === */
.range-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 8px;
}
.range-chips { display: flex; gap: 4px; flex: 1; }
.chip {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  background: var(--card);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  color: var(--text-2);
  cursor: pointer;
}
.chip.active {
  background: linear-gradient(135deg, var(--primary), #8B5CF6);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 4px 10px rgba(99,102,241,0.3);
}
.nav-btn {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--card);
  color: var(--text-2);
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}
.nav-btn:active { background: var(--primary-bg); }

/* === v2.2.71 最近交易 === */
.recent-section { margin-bottom: 8px; }
.recent-title {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; font-weight: 700; color: var(--text-1);
  padding: 0 2px; margin-bottom: 6px;
}
.link-all { font-size: 12px; color: var(--primary); font-weight: 500; }

/* v2.2.73: 按日折叠 */
.day-groups { display: flex; flex-direction: column; gap: 8px; }
.day-fold {
  background: var(--card);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  overflow: hidden;
}
.day-fold-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.day-fold-head:active { background: var(--primary-bg); }
.df-left { display: flex; align-items: center; gap: 6px; }
.df-chev {
  font-size: 18px; color: var(--text-3);
  display: inline-block;
  transition: transform 0.25s ease;
  font-weight: 600;
}
.df-chev.open { transform: rotate(90deg); color: var(--primary); }
.df-name { font-size: 14px; font-weight: 700; color: var(--text-1); }
.df-count {
  font-size: 11px;
  color: var(--text-3);
  background: var(--bg-2);
  padding: 2px 6px;
  border-radius: 8px;
  margin-left: 4px;
}
.df-right { font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; }
.amount-neg { color: #EF4444; }
.amount-pos { color: #10B981; }

.day-fold-body {
  border-top: 1px solid var(--border-light);
  background: var(--bg-1);
}
.day-expand-enter-active, .day-expand-leave-active {
  transition: max-height 0.3s ease, opacity 0.2s;
  overflow: hidden;
  max-height: 2000px;
}
.day-expand-enter-from, .day-expand-leave-to {
  max-height: 0;
  opacity: 0;
}

.recent-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light);
}
.recent-item:last-child { border-bottom: none; }
.cat-mini {
  width: 32px; height: 32px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.08);
}
.cat-emoji-sm { font-size: 16px; }
.recent-info { flex: 1; min-width: 0; }
.recent-merchant {
  font-size: 13px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--text-1);
}
.recent-time { font-size: 11px; color: var(--text-3); margin-top: 1px; }
.recent-amount {
  font-size: 14px; font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-1);
}
.recent-amount.neg { color: #EF4444; }
.recent-amount.pos { color: #10B981; }

/* === v2.2.71 空态 === */
.empty { text-align: center; padding: 30px 14px; }
.empty .ico { font-size: 40px; margin-bottom: 8px; }
.empty-title { font-size: 15px; font-weight: 600; color: var(--text-1); margin-bottom: 4px; }

/* === v2.2.77 备注 badge === */
.note-badge {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 500;
  color: #6366F1;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.2);
  padding: 2px 8px;
  border-radius: 8px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:root[data-theme="dark"] .note-badge {
  background: rgba(129, 140, 248, 0.2);
  border-color: rgba(129, 140, 248, 0.3);
  color: #A5B4FC;
}

/* v2.2.77: 点击交互 */
.recent-item { cursor: pointer; transition: background 0.15s; }
.recent-item:active { background: var(--primary-bg); }

/* v2.2.77: 右下角悬浮 + 按钮 */
.fab-add {
  position: fixed;
  right: 16px;
  bottom: 80px;
  width: 56px; height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366F1, #8B5CF6);
  color: #fff;
  font-size: 28px;
  font-weight: 300;
  border: none;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
  cursor: pointer;
  z-index: 100;
  transition: transform 0.2s;
  display: flex; align-items: center; justify-content: center;
}
.fab-add:active { transform: scale(0.92); }
</style>
