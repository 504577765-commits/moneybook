<template>
  <div class="page budget">
    <div class="page-header fade-in-up">预算</div>

    <!-- 头部:本月预算总览 -->
    <div class="b-hero fade-in-up">
      <div class="b-hero-top">
        <div class="b-hero-month">
          <button class="b-month-btn" @click="prevMonth">‹</button>
          <span>{{ displayMonth }}</span>
          <button class="b-month-btn" @click="nextMonth">›</button>
          <span v-if="!isCurrentMonth" class="b-back-link" @click="backToNow">回到本月</span>
        </div>
        <div class="b-hero-actions">
          <button class="b-mini-btn" @click="rollover">结转上月</button>
        </div>
      </div>
      <div class="b-hero-nums">
        <div class="b-hn-item">
          <div class="b-hn-label">本月预算</div>
          <div class="b-hn-val">¥{{ fmt(budget.totalBudget) }}</div>
        </div>
        <div class="b-hn-divider"></div>
        <div class="b-hn-item">
          <div class="b-hn-label">已花</div>
          <div class="b-hn-val">{{ fmt(budget.monthSpent) }}</div>
        </div>
        <div class="b-hn-divider"></div>
        <div class="b-hn-item">
          <div class="b-hn-label">剩余可花</div>
          <div class="b-hn-val" :style="{ color: budget.totalRemain < 0 ? '#FECACA' : '#fff' }">
            {{ fmt(budget.totalRemain) }}
          </div>
        </div>
      </div>
      <div class="b-hero-bar">
        <div :class="['b-hero-fill', pct > 100 ? 'over' : pct > 80 ? 'warn' : '']"
             :style="{ width: Math.min(pct, 100) + '%' }"></div>
      </div>
      <div class="b-hero-tip" v-if="rolloverMsg">{{ rolloverMsg }}</div>
    </div>

    <!-- 分类预算列表 -->
    <div class="group fade-in-up" style="animation-delay: 0.06s">
      <div class="group-label">分类预算</div>
      <div class="group-card b-list">
        <div v-for="c in expenseCats" :key="c.id" class="b-row tappable" @click="editCat(c)">
          <span class="b-cat-icon" :style="{ background: c.color + '22' }">{{ c.icon }}</span>
          <div class="b-cat-main">
            <div class="b-cat-name">
              {{ c.name }}
              <span v-if="!hasBudget(c.id)" class="b-unalloc">未分配</span>
            </div>
            <div v-if="hasBudget(c.id)" class="b-progress">
              <div :class="['b-fill', progressState(c.id)]"
                   :style="{ width: Math.min(progressPct(c.id), 100) + '%' }"></div>
            </div>
          </div>
          <div class="b-cat-right">
            <div class="b-cat-amt" v-if="hasBudget(c.id)">¥{{ budget.getBudget(c.id) }}</div>
            <div class="b-cat-sub" v-if="hasBudget(c.id)">
              已花 {{ fmt(Math.abs(spent(c.id))) }}
              <span v-if="remain(c.id) < 0" class="b-over">超 {{ fmt(-remain(c.id)) }}</span>
              <span v-else-if="remain(c.id) < budget.getBudget(c.id) * 0.2" class="b-warn">剩 {{ fmt(remain(c.id)) }}</span>
            </div>
            <span class="b-chev">›</span>
          </div>
        </div>
        <div v-if="!expenseCats.length" class="empty">
          <div class="ico">📋</div>
          <div>暂无支出分类</div>
        </div>
      </div>
    </div>

    <div class="muted b-hint fade-in-up" style="animation-delay: 0.1s">
      点击分类设置"本月分配额度"。结转会把上月未花余额并入本月对应分类。
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useBookStore } from '../stores/book'
import { useBudgetStore } from '../stores/budget'
import { showPrompt, showToast, showAlert } from '../utils/dialog'
import dayjs from 'dayjs'

const store = useBookStore()
const budget = useBudgetStore()
const rolloverMsg = ref('')

const expenseCats = computed(() => store.categories.expense || [])
const displayMonth = computed(() => {
  const d = dayjs(budget.month + '-01')
  return d.format('YYYY年M月')
})
const isCurrentMonth = computed(() => budget.month === dayjs().format('YYYY-MM'))
const pct = computed(() => {
  if (budget.totalBudget <= 0) return 0
  return (budget.monthSpent / budget.totalBudget) * 100
})

const fmt = (n) => (Number(n) || 0).toFixed(2)
function hasBudget(cid) { return (budget.budgets[cid] || 0) > 0 }
function spent(cid) { return budget.spentByCat[cid] || 0 }  // 支出为负,收入为正
function remain(cid) { return (budget.budgets[cid] || 0) + (budget.spentByCat[cid] || 0) }
function progressPct(cid) {
  const b = budget.budgets[cid] || 0
  if (b <= 0) return 0
  return (Math.abs(spent(cid)) / b) * 100
}
function progressState(cid) {
  const p = progressPct(cid)
  return p > 100 ? 'over' : p > 80 ? 'warn' : ''
}

async function editCat(cat) {
  const cur = budget.budgets[cat.id] || 0
  const v = await showPrompt(
    `为「${cat.name}」分配本月预算额度(元)`, cur > 0 ? String(cur) : '', '预算分配',
    { placeholder: '如 500, 填 0 清除' }
  )
  if (v === null) return
  const n = Number(v) || 0
  if (n < 0 || n > 999999) { showToast('额度需在 0~999999 之间', 'error'); return }
  await budget.setAmount(cat.id, n)
  showToast(n > 0 ? `已分配 ¥${n} 给「${cat.name}」` : `已清除「${cat.name}」预算`, 'success')
}

function prevMonth() { budget.setMonth(dayjs(budget.month + '-01').subtract(1, 'month').format('YYYY-MM')) }
function nextMonth() { budget.setMonth(dayjs(budget.month + '-01').add(1, 'month').format('YYYY-MM')) }
function backToNow() { budget.setMonth(dayjs().format('YYYY-MM')) }

async function rollover() {
  if (!isCurrentMonth.value) { showToast('请在当月操作结转', 'error'); return }
  const n = await budget.applyRollover()
  rolloverMsg.value = n > 0 ? `已将上月 ${n >= 100 ? fmt(n) : n} 元未花余额结转到本月` : ''
  if (n > 0) showToast(`结转未花余额 ¥${fmt(n)}`, 'success')
  else showAlert('上月没有可结转的未花余额', '结转完成')
}

onMounted(async () => {
  if (store.ready) {
    await budget.load()
  } else {
    // 等待 book store 初始化完成
    const timer = setInterval(async () => {
      if (store.ready) { clearInterval(timer); await budget.load() }
    }, 300)
  }
})
</script>

<style scoped>
.budget { padding: 12px 14px 100px; }

.b-hero {
  position: relative;
  background: var(--gradient);
  color: #fff;
  border-radius: var(--radius-xl);
  padding: 18px;
  box-shadow: var(--shadow-lg);
  margin-bottom: 14px;
  overflow: hidden;
}
.b-hero::after {
  content: ''; position: absolute; inset: 0;
  background-image: radial-gradient(circle at 1px 1px, rgba(255,255,255,.14) 1px, transparent 0);
  background-size: 20px 20px; opacity: .35; pointer-events: none;
}
.b-hero-top, .b-hero-nums, .b-hero-bar { position: relative; z-index: 1; }
.b-hero-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.b-hero-month { display: flex; align-items: center; gap: 10px; font-size: 15px; font-weight: 700; }
.b-month-btn {
  width: 26px; height: 26px; border-radius: 50%;
  background: rgba(255,255,255,.18); color: #fff; font-size: 16px;
  display: flex; align-items: center; justify-content: center;
}
.b-back-link { font-size: 12px; opacity: .85; text-decoration: underline; }
.b-mini-btn {
  background: rgba(255,255,255,.2); color: #fff; font-size: 12px;
  padding: 6px 12px; border-radius: 999px;
}
.b-hero-nums { display: flex; align-items: center; justify-content: space-between; }
.b-hn-item { text-align: center; flex: 1; }
.b-hn-label { font-size: 11px; opacity: .85; }
.b-hn-val { font-size: 22px; font-weight: 800; margin-top: 2px; font-variant-numeric: tabular-nums; }
.b-hn-divider { width: 1px; height: 30px; background: rgba(255,255,255,.25); }
.b-hero-bar {
  height: 6px; background: rgba(255,255,255,.22); border-radius: 999px;
  margin-top: 14px; overflow: hidden;
}
.b-hero-fill { height: 100%; background: rgba(255,255,255,.9); border-radius: 999px; transition: width .4s; }
.b-hero-fill.warn { background: #FBBF24; }
.b-hero-fill.over { background: #F87171; }
.b-hero-tip { position: relative; z-index: 1; font-size: 12px; opacity: .95; margin-top: 10px; }

.b-list { display: flex; flex-direction: column; }
.b-row { display: flex; align-items: center; gap: 12px; padding: 13px 4px; border-bottom: 1px solid var(--border-light); }
.b-row:last-child { border-bottom: none; }
.b-cat-icon {
  width: 40px; height: 40px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 20px;
}
.b-cat-main { flex: 1; min-width: 0; }
.b-cat-name { font-size: 15px; font-weight: 600; color: var(--text-1); }
.b-unalloc { font-size: 11px; color: var(--text-3); font-weight: 400; margin-left: 6px; }
.b-progress { height: 5px; background: var(--bg-2); border-radius: 999px; margin-top: 6px; overflow: hidden; }
.b-fill { height: 100%; background: var(--budget-ok); border-radius: 999px; transition: width .4s; }
.b-fill.warn { background: var(--budget-warn); }
.b-fill.over { background: var(--budget-over); }
.b-cat-right { text-align: right; flex-shrink: 0; }
.b-cat-amt { font-size: 15px; font-weight: 800; color: var(--text-1); font-variant-numeric: tabular-nums; }
.b-cat-sub { font-size: 11px; color: var(--text-3); margin-top: 2px; font-variant-numeric: tabular-nums; }
.b-over { color: var(--budget-over); font-weight: 600; }
.b-warn { color: var(--budget-warn); }
.b-chev { color: var(--text-4); font-size: 16px; margin-left: 4px; }
.b-hint { font-size: 12px; text-align: center; margin-top: 8px; padding: 0 10px; }
</style>