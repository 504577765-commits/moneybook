<template>
  <div class="page records">
    <div class="page-header">
      <span>账单</span>
      <span class="header-actions">
        <span v-if="store.canUndo()" class="undo-btn" @click="onUndo">↩ 撤销</span>
        <span v-if="!batchMode" class="organize-btn" @click="enterBatch">整理</span>
      </span>
    </div>

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
        <div v-for="tx in g.items" :key="tx.id" class="tx-item tappable"
             @click="onItemClick(tx)"
             @mousedown="onLongStart($event, tx)" @mouseup="onLongEnd" @mouseleave="onLongEnd"
             @touchstart.passive="onTouchStart(tx)" @touchend.passive="onTouchEnd" @touchmove.passive="onLongEnd"
             @contextmenu.prevent="onContextMenu(tx)">
          <div v-if="batchMode" :class="['tx-check', isSelected(tx.id) ? 'on' : '']" @click.stop="toggleSelect(tx)">✓</div>
          <div class="cat-icon" :style="{background: catColor(tx.category_id, tx.type)}">
            <span class="cat-emoji">{{ catIcon(tx.category_id, tx.type) }}</span>
          </div>
          <div class="tx-info">
            <div class="tx-merchant">{{ tx.merchant || catName(tx.category_id, tx.type) }}</div>
            <div class="tx-meta">
              <span class="tx-cat">{{ catName(tx.category_id, tx.type) }}</span>
              <span v-if="tx.auto" class="auto-tag">自动</span>
              <span v-if="tx.type === 'transfer'" class="acct-tag transfer">转账</span>
              <span v-else-if="accOf(tx.account_id)" class="acct-tag">{{ accOf(tx.account_id).icon }} {{ accOf(tx.account_id).name }}</span>
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

    <!-- v2.3.0: 批量底部操作条 -->
    <div v-if="batchMode" class="batch-bar">
      <span class="batch-count">已选 {{ selected.size }} 项</span>
      <button class="batch-btn" @click="toggleSelectAll">全选/反选</button>
      <button class="batch-btn" @click="onBatchCategory">改分类</button>
      <button class="batch-btn danger" @click="onBatchDelete">删除</button>
      <button class="batch-btn" @click="exitBatch">退出</button>
    </div>

    <!-- v2.3.0: 批量改分类选择器 -->
    <div v-if="catPickerVisible" class="cat-picker-overlay" @click.self="catPickerVisible = false">
      <div class="cat-picker-card">
        <div class="cp-title">选择分类</div>
        <div class="cp-grid">
          <div v-for="c in allCategories" :key="c.id" class="cp-item" @click="applyCategory(c.id)">
            <span class="cp-emoji">{{ c.icon }}</span>
            <span class="cp-name">{{ c.name }}</span>
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
import { showToast, showConfirm } from '../utils/dialog'
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
  const idx = all.value.findIndex(t => t.id === updated.id)
  if (idx >= 0) all.value[idx] = { ...all.value[idx], ...updated }
  await store.refreshTransactions()
}

// ===== v2.3.0: 批量管理 =====
const batchMode = ref(false)
const selected = ref(new Set())
const catPickerVisible = ref(false)
let longPressTimer = null

const allCategories = computed(() => [...store.categories.expense, ...store.categories.income])

function accOf(accountId) {
  if (!accountId) return null
  return store.accountMap[accountId]
}
function isSelected(id) { return selected.value.has(id) }

function enterBatch() {
  batchMode.value = true
  selected.value = new Set()
  catPickerVisible.value = false
}
function exitBatch() {
  batchMode.value = false
  selected.value = new Set()
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
}

function onItemClick(tx) {
  if (batchMode.value) { toggleSelect(tx); return }
  openEdit(tx)
}
function toggleSelect(tx) {
  const s = new Set(selected.value)
  if (s.has(tx.id)) s.delete(tx.id); else s.add(tx.id)
  selected.value = s
}
// 全选/反选
function toggleSelectAll() {
  const ids = filtered.value.map(t => t.id)
  const allSel = ids.every(id => selected.value.has(id))
  const s = new Set(selected.value)
  if (allSel) { ids.forEach(id => s.delete(id)) }
  else { ids.forEach(id => s.add(id)) }
  selected.value = s
}

const selectedTxs = computed(() => all.value.filter(t => selected.value.has(t.id)))

async function onBatchCategory() {
  if (!selectedTxs.value.length) { showToast('请先选择交易', 'error'); return }
  catPickerVisible.value = true
}
async function applyCategory(categoryId) {
  catPickerVisible.value = false
  const txs = selectedTxs.value
  if (!txs.length) return
  const n = await store.batchSetCategoryTx(txs, categoryId)
  showToast(`已更新分类 ${n} 笔`, 'success')
  await refresh()
  exitBatch()
}

async function onBatchDelete() {
  const txs = selectedTxs.value
  if (!txs.length) { showToast('请先选择交易', 'error'); return }
  const ok = await showConfirm(`确定删除选中的 ${txs.length} 笔交易吗？可撤销。`, '批量删除', {
    confirmText: '删除', cancelText: '取消', danger: true
  })
  if (!ok) return
  const n = await store.batchDeleteTransactions(txs)
  showToast(`已删除 ${n} 笔`, 'success')
  await refresh()
  exitBatch()
}

// v2.3.0: 撤销最近一次交易操作
async function onUndo() {
  const done = await store.undo()
  showToast(done ? '已撤销' : '无可撤销操作', done ? 'success' : 'info')
  await refresh()
}

// 长按进入批量模式
function onLongStart(e, tx) {
  if (e && e.button !== 0 && e.button !== undefined) return // 仅左键
  longPressTimer = setTimeout(() => {
    enterBatch()
    toggleSelect(tx)
  }, 500)
}
function onTouchStart(tx) {
  if (longPressTimer) clearTimeout(longPressTimer)
  longPressTimer = setTimeout(() => {
    enterBatch()
    toggleSelect(tx)
  }, 500)
}
function onLongEnd() {
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
}
function onContextMenu(tx) {
  if (!batchMode.value) {
    enterBatch()
    toggleSelect(tx)
  }
}

onUnmounted(() => {
  window.removeEventListener('moneybook:tx-added', onTxAdded)
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
})
</script>

<style scoped>
.records .page-header {
  margin-bottom: 12px;
  display: flex; align-items: center; justify-content: space-between;
}
.header-actions { display: flex; gap: 8px; }
.undo-btn, .organize-btn {
  font-size: 12px; font-weight: 600;
  padding: 5px 12px; border-radius: 14px; cursor: pointer;
  transition: all var(--transition-fast);
}
.undo-btn {
  background: var(--primary-bg); color: var(--primary);
  border: 1px solid var(--primary);
}
.organize-btn {
  background: var(--bg-2); color: var(--text-2);
  border: 1px solid var(--border-light);
}
.undo-btn:active, .organize-btn:active { transform: scale(0.96); }

/* v2.3.0: 账户/转账小标签 */
.acct-tag {
  font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600;
  background: rgba(99, 102, 241, 0.1);
  color: #6366F1;
  border: 1px solid rgba(99, 102, 241, 0.2);
  white-space: nowrap;
}
.acct-tag.transfer {
  background: rgba(236, 72, 153, 0.1);
  color: #EC4899;
  border-color: rgba(236, 72, 153, 0.2);
}
:root[data-theme="dark"] .acct-tag {
  background: rgba(129, 140, 248, 0.2); border-color: rgba(129, 140, 248, 0.3); color: #A5B4FC;
}
:root[data-theme="dark"] .acct-tag.transfer {
  background: rgba(236, 72, 153, 0.2); border-color: rgba(236, 72, 153, 0.3); color: #F472B6;
}

/* v2.3.0: 批量勾选框 */
.tx-check {
  width: 22px; height: 22px; border-radius: 50%;
  border: 2px solid var(--border-light);
  margin-right: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: transparent;
  background: var(--card);
  transition: all 0.15s;
}
.tx-check.on {
  background: var(--primary); border-color: var(--primary); color: #fff;
}

/* v2.3.0: 批量底部操作条 */
.batch-bar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 999;
  display: flex; align-items: center; gap: 8px;
  padding: 12px 14px;
  background: var(--card);
  border-top: 1px solid var(--border-light);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);
}
.batch-count { font-size: 12px; color: var(--text-2); white-space: nowrap; }
.batch-btn {
  padding: 9px 12px; font-size: 13px; font-weight: 600;
  border: none; border-radius: 10px;
  background: var(--bg-2); color: var(--text-1);
  cursor: pointer; white-space: nowrap;
  transition: transform 0.1s, opacity 0.2s;
}
.batch-btn:active { transform: scale(0.96); }
.batch-btn.danger {
  background: linear-gradient(135deg, #FEE2E2, #FECACA); color: #DC2626;
}

/* v2.3.0: 批量改分类选择器 */
.cat-picker-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  padding: 16px;
}
.cat-picker-card {
  width: 100%; max-width: 420px;
  max-height: 70vh; overflow-y: auto;
  background: var(--card); border-radius: 20px; padding: 20px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.cp-title { font-size: 16px; font-weight: 700; color: var(--text-1); margin-bottom: 12px; }
.cp-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;
}
.cp-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 4px; background: var(--bg-2);
  border-radius: 10px; cursor: pointer;
  transition: all 0.15s;
}
.cp-item:active { transform: scale(0.95); }
.cp-emoji { font-size: 20px; }
.cp-name { font-size: 11px; color: var(--text-2); }
:root[data-theme="dark"] .cat-picker-overlay { background: rgba(0, 0, 0, 0.7); }
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
