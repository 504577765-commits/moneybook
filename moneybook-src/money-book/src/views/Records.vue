<template>
  <div class="page records">
    <div class="page-header">账单</div>

    <div class="filter-bar card">
      <div class="filter-row">
        <span class="muted">类型</span>
        <span :class="['chip', filter.type === '' ? 'active':'']" @click="setType('')">全部</span>
        <span :class="['chip', filter.type === 'expense' ? 'active':'']" @click="setType('expense')">支出</span>
        <span :class="['chip', filter.type === 'income' ? 'active':'']" @click="setType('income')">收入</span>
      </div>
      <div class="filter-row">
        <span class="muted">分类</span>
        <span :class="['chip', filter.categoryId === '' ? 'active':'']" @click="setCat('')">全部</span>
        <span v-for="c in catList" :key="c.id"
          :class="['chip', filter.categoryId === c.id ? 'active':'']"
          @click="setCat(c.id)">{{ c.icon }} {{ c.name }}</span>
      </div>
      <div class="filter-row">
        <input v-model="filter.keyword" placeholder="🔍 搜索商户或备注" class="search" @input="onSearch" />
      </div>
    </div>

    <div v-if="filtered.length === 0" class="empty fade-in-up">
      <div class="ico">📋</div>
      <div class="empty-title">暂无符合条件的记录</div>
      <div class="muted" style="margin-top:8px;">试试调整筛选条件</div>
    </div>

    <div v-for="(g, gi) in grouped" :key="g.date" class="day-group fade-in-up" :style="`animation-delay: ${gi * 0.05}s`">
      <div class="day-title">
        <span class="day-name">{{ formatDay(g.date) }}</span>
        <span class="day-amounts amount-num">
          <span v-if="dayExpense(g.items) > 0" class="amount-neg">-¥{{ fmtAmount(dayExpense(g.items)) }}</span>
          <span v-if="dayIncome(g.items) > 0" class="amount-pos" style="margin-left:8px;">+¥{{ fmtAmount(dayIncome(g.items)) }}</span>
        </span>
      </div>
      <div class="card tx-list">
        <div v-for="tx in g.items" :key="tx.id" class="tx-item tappable" @click="openEdit(tx)">
          <div class="cat-icon" :style="{background: catColor(tx.category_id, tx.type)}">
            <span class="cat-emoji">{{ catIcon(tx.category_id, tx.type) }}</span>
          </div>
          <div class="tx-info">
            <div class="tx-merchant">{{ tx.merchant || catName(tx.category_id, tx.type) }}</div>
            <div class="tx-meta">
              <span class="tx-cat">{{ catName(tx.category_id, tx.type) }}</span>
              <span v-if="tx.auto" class="auto-tag">自动</span>
              <span class="tx-time">· {{ fmtTime(tx.occurred_at, 'HH:mm') }}</span>
            </div>
            <div v-if="tx.note" class="note-badge">{{ tx.note }}</div>
          </div>
          <div :class="['tx-amount', 'amount-num', tx.type === 'expense' ? 'amount-neg' : 'amount-pos']">
            {{ tx.type === 'expense' ? '-' : '+' }}{{ fmtAmount(tx.amount) }}
          </div>
        </div>
      </div>
    </div>

    <EditTxDialog :visible="editVisible" :tx="editingTx" @close="closeEdit" @saved="onSaved" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useBookStore } from '../stores/book'
import { fmtAmount, fmtTime, groupByDate } from '../utils/format'
import { listTransactions } from '../db'
import dayjs from 'dayjs'
import EditTxDialog from '../components/EditTxDialog.vue'

const store = useBookStore()
const filter = ref({ type: '', categoryId: '', keyword: '' })
const all = ref([])

const catList = computed(() => {
  if (filter.value.type === 'expense') return store.categories.expense
  if (filter.value.type === 'income')  return store.categories.income
  return [...store.categories.expense, ...store.categories.income]
})

const filtered = computed(() => {
  let r = all.value
  if (filter.value.type) r = r.filter(t => t.type === filter.value.type)
  if (filter.value.categoryId) r = r.filter(t => t.category_id === filter.value.categoryId)
  if (filter.value.keyword) {
    const kw = filter.value.keyword.toLowerCase()
    r = r.filter(t => (t.merchant||'').toLowerCase().includes(kw) || (t.note||'').toLowerCase().includes(kw))
  }
  return r
})

const grouped = computed(() => groupByDate(filtered.value))

async function refresh() {
  // 最近 90 天
  const end = Date.now()
  const start = end - 90 * 86400000
  all.value = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
}

function setType(t) { filter.value.type = t; filter.value.categoryId = '' }
function setCat(id) { filter.value.categoryId = id }
function onSearch() { /* reactive */ }

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
  return c?.color || '#BDC3C7'
}

function dayExpense(items) { return items.filter(t => t.type === 'expense').reduce((s, t) => s + t.amount, 0) }
function dayIncome(items)  { return items.filter(t => t.type === 'income').reduce((s, t) => s + t.amount, 0) }

function formatDay(d) {
  const today = dayjs().format('YYYY-MM-DD')
  const yest  = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  if (d === today) return '今天'
  if (d === yest)  return '昨天'
  return dayjs(d).format('YYYY年M月D日 dddd')
}

onMounted(refresh)
function onTxAdded() { refresh() }
window.addEventListener('moneybook:tx-added', onTxAdded)
onUnmounted(() => window.removeEventListener('moneybook:tx-added', onTxAdded))

// v2.2.77: 编辑弹窗
const editVisible = ref(false)
const editingTx = ref({})
function openEdit(tx) { editingTx.value = { ...tx }; editVisible.value = true }
function closeEdit() { editVisible.value = false }
async function onSaved(updated) {
  // 立即更新本地数据
  const idx = txs.value.findIndex(t => t.id === updated.id)
  if (idx >= 0) txs.value[idx] = { ...txs.value[idx], ...updated }
  await store.refreshTransactions()
}
</script>

<style scoped>
.records .page-header { margin-bottom: 12px; }
.filter-bar {
  padding: 12px 14px;
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 14px;
  animation: fadeInUp 0.4s ease-out;
}
.filter-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin: 6px 0; }
.filter-row .muted { font-size: 12px; min-width: 32px; font-weight: 500; }
.search {
  flex: 1;
  border: 1.5px solid #E0E4F0;
  border-radius: 10px;
  padding: 9px 14px;
  font-size: 14px;
  outline: none;
  background: #F8F9FC;
  transition: all var(--transition);
}
.search:focus { border-color: var(--primary); background: #fff; box-shadow: 0 0 0 3px rgba(91,108,255,0.08); }

.day-group { margin-bottom: 14px; }
.day-title {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 4px; font-size: 14px; font-weight: 700;
}
.day-name { letter-spacing: -0.2px; }
.day-amounts { font-size: 13px; }
.tx-list { padding: 4px 0; }
.tx-item {
  display: flex; align-items: center;
  padding: 12px; border-bottom: 1px solid rgba(236, 240, 244, 0.6);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.tx-item:last-child { border-bottom: none; }
.tx-item:active { background: rgba(91, 108, 255, 0.04); }
.cat-icon {
  width: 42px; height: 42px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}
.cat-emoji { font-size: 20px; }
.tx-info { flex: 1; margin-left: 12px; min-width: 0; }
.tx-merchant {
  font-size: 15px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  letter-spacing: -0.2px;
}
.tx-meta { font-size: 12px; margin-top: 3px; display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
.auto-tag {
  background: linear-gradient(135deg, #FFE9E9, #FFD6D6);
  color: #EB3B5A;
  font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600;
}
.note { color: var(--text-muted); }

/* v2.2.77: 备注 badge */
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
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:root[data-theme="dark"] .note-badge {
  background: rgba(129, 140, 248, 0.2);
  border-color: rgba(129, 140, 248, 0.3);
  color: #A5B4FC;
}

.tx-amount { font-size: 16px; font-weight: 700; letter-spacing: -0.3px; white-space: nowrap; }
</style>
