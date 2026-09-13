// v2.3.1: Web/预览环境的内存降级数据层
// 当没有 Capacitor 原生桥(hasCap()=false)时,db 接口落到这里,
// 让浏览器里也能完整操作账户/预算/交易/统计(会话内有效,不持久化)。
// 接口形状与 Java MoneyDbHelper 返回一致,方便上层无感切换。

const DEF_CATS = [
  { id: 'food', name: '餐饮', icon: '🍱', color: '#FF6B6B', type: 'expense', sort_order: 0 },
  { id: 'transport', name: '交通', icon: '🚇', color: '#4ECDC4', type: 'expense', sort_order: 1 },
  { id: 'shopping', name: '购物', icon: '🛍️', color: '#FFA502', type: 'expense', sort_order: 2 },
  { id: 'entertainment', name: '娱乐', icon: '🎮', color: '#A55EEA', type: 'expense', sort_order: 3 },
  { id: 'daily', name: '日用', icon: '🧴', color: '#45AAF2', type: 'expense', sort_order: 4 },
  { id: 'medical', name: '医疗', icon: '💊', color: '#26DE81', type: 'expense', sort_order: 5 },
  { id: 'housing', name: '居住', icon: '🏠', color: '#778CA3', type: 'expense', sort_order: 6 },
  { id: 'learning', name: '学习', icon: '📚', color: '#EB3B5A', type: 'expense', sort_order: 7 },
  { id: 'other_out', name: '其他', icon: '💼', color: '#A0A0A0', type: 'expense', sort_order: 8 },
  { id: 'salary', name: '工资', icon: '💰', color: '#26DE81', type: 'income', sort_order: 0 },
  { id: 'bonus', name: '奖金', icon: '🎁', color: '#FF6B6B', type: 'income', sort_order: 1 },
  { id: 'refund', name: '退款', icon: '↩️', color: '#45AAF2', type: 'income', sort_order: 2 },
  { id: 'investment', name: '投资', icon: '📈', color: '#A55EEA', type: 'income', sort_order: 3 },
  { id: 'redpacket', name: '红包', icon: '🧧', color: '#FF4757', type: 'income', sort_order: 4 },
  { id: 'other_in', name: '其他', icon: '💼', color: '#A0A0A0', type: 'income', sort_order: 5 }
]

function nowId() {
  return 'tx_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

const MEM = {
  categories: DEF_CATS.map(c => ({ ...c })),
  nextAccId: 1,
  accounts: [{ id: 1, name: '默认账户', type: 'bank', icon: '💳', initial_balance: 0, sort: 0, archived: 0, created_at: Date.now() }],
  transactions: [],
  budgets: {},          // 'YYYY-MM' -> { category_id: amount }
  merchantAccount: {},  // merchant -> accountId
  merchantCategory: {}  // merchant -> categoryId
}

export default {
  init() {
    // 先拉取默认账户便于首屏
  },

  getAllCategories(type) {
    let list = MEM.categories
    if (type) list = list.filter(c => c.type === type)
    return list.map(c => ({ ...c, keywords: [] }))
  },

  // ---- 交易 ----
  insertTransaction(tx) {
    const id = tx.id || nowId()
    MEM.transactions.push({
      id,
      type: tx.type || 'expense',
      amount: tx.amount || 0,
      category_id: tx.category_id || null,
      source: tx.source || 'other',
      merchant: tx.merchant || '',
      note: tx.note || '',
      raw_text: tx.raw_text || '',
      occurred_at: tx.occurred_at || Date.now(),
      created_at: Date.now(),
      auto: 0,
      account_id: tx.account_id || 0,
      to_account_id: tx.to_account_id || 0,
      splits: []
    })
    return { ok: true, id }
  },

  updateTransaction(tx) {
    const i = MEM.transactions.findIndex(t => t.id === tx.id)
    if (i < 0) return false
    MEM.transactions[i] = {
      ...MEM.transactions[i],
      type: tx.type, amount: tx.amount,
      category_id: tx.category_id || null,
      merchant: tx.merchant || '', note: tx.note || '',
      occurred_at: tx.occurred_at,
      account_id: tx.account_id || 0,
      to_account_id: tx.to_account_id || 0
    }
    return true
  },

  upsertTransaction(tx) {
    const i = MEM.transactions.findIndex(t => t.id === tx.id)
    if (i >= 0) return this.updateTransaction(tx)
    return this.insertTransaction(tx).ok
  },

  deleteTransaction(id) {
    const before = MEM.transactions.length
    MEM.transactions = MEM.transactions.filter(t => t.id !== id)
    return before !== MEM.transactions.length
  },

  deleteTransactions(ids) {
    let n = 0
    const set = new Set(ids)
    const before = MEM.transactions.length
    MEM.transactions = MEM.transactions.filter(t => !set.has(t.id))
    n = before - MEM.transactions.length
    return n
  },

  batchSetCategory(ids, categoryId) {
    let n = 0
    const set = new Set(ids)
    for (const t of MEM.transactions) {
      if (set.has(t.id) && !(t.splits && t.splits.length)) {
        t.category_id = categoryId
        n++
      }
    }
    return n
  },

  saveTxSplits(txId, splits) {
    const t = MEM.transactions.find(x => x.id === txId)
    if (t) t.splits = (splits || []).map(s => ({ ...s }))
    return true
  },

  listTransactions({ startTs = 0, endTs, type, categoryId, keyword, limit = 500 } = {}) {
    const end = endTs === undefined || endTs === Infinity ? Date.now() + 86400000 : endTs
    const kw = keyword ? String(keyword).toLowerCase() : ''
    return MEM.transactions
      .filter(t => {
        if (t.occurred_at < startTs || t.occurred_at >= end) return false
        if (type && t.type !== type) return false
        if (categoryId) {
          const inMain = t.category_id === categoryId
          const inSplits = (t.splits && t.splits.length) ? t.splits.some(s => s.category_id === categoryId) : false
          if (!inMain && !inSplits) return false
        }
        if (kw) {
          const hasKw = (t.merchant || '').toLowerCase().includes(kw) || (t.note || '').toLowerCase().includes(kw)
          if (!hasKw) return false
        }
        return true
      })
      .sort((a, b) => b.occurred_at - a.occurred_at)
      .slice(0, limit)
      .map(t => ({ ...t, splits: t.splits ? t.splits.map(s => ({ ...s })) : [] }))
  },

  // ---- 账户 ----
  getAllAccounts() {
    const calc = (a) => {
      let bal = a.initial_balance || 0
      for (const t of MEM.transactions) {
        if (t.type === 'income' && t.account_id === a.id) bal += t.amount
        else if (t.type === 'expense' && t.account_id === a.id) bal -= t.amount
        else if (t.type === 'transfer' && t.account_id === a.id) bal -= t.amount
        else if (t.type === 'transfer' && t.to_account_id === a.id) bal += t.amount
      }
      return bal
    }
    return MEM.accounts.map(a => ({ ...a, balance: calc(a) }))
  },

  insertAccount({ name, type, icon }) {
    const acc = {
      id: ++MEM.nextAccId, name: String(name || '未命名账户'),
      type: type || 'other', icon: icon || '💰',
      initial_balance: 0, sort: 0, archived: 0, created_at: Date.now()
    }
    MEM.accounts.push(acc)
    return { ok: true, id: acc.id }
  },

  updateAccount({ id, name, type, icon, initial_balance }) {
    const a = MEM.accounts.find(x => x.id === id)
    if (!a) return false
    if (name !== undefined) a.name = String(name)
    if (type !== undefined) a.type = type
    if (icon !== undefined) a.icon = icon
    if (initial_balance !== undefined) a.initial_balance = initial_balance
    return true
  },

  deleteAccount(id) {
    const a = MEM.accounts.find(x => x.id === id)
    if (a) a.archived = 1
    return !!a
  },

  // ---- 预算 ----
  getBudgets(month) {
    const m = MEM.budgets[month] || {}
    return Object.entries(m).map(([category_id, amount]) => ({ category_id, amount }))
  },

  setBudget(categoryId, month, amount) {
    if (!MEM.budgets[month]) MEM.budgets[month] = {}
    if (amount > 0) MEM.budgets[month][categoryId] = amount
    else delete MEM.budgets[month][categoryId]
    return true
  },

  // ---- 商户映射 ----
  getMerchantAccountId(merchant) { return MEM.merchantAccount[merchant] || 0 },
  setMerchantAccount(merchant, accountId) { MEM.merchantAccount[merchant] = accountId; return true },
  getMerchantCategory(merchant) { return MEM.merchantCategory[merchant] || null },
  setMerchantCategory(merchant, categoryId) {
    if (categoryId) MEM.merchantCategory[merchant] = categoryId
    else delete MEM.merchantCategory[merchant]
    return true
  },

  // ---- 统计(由内存交易派生) ----
  sumByCategory({ startTs = 0, endTs = Date.now() } = {}) {
    const txs = this.listTransactions({ startTs, endTs, limit: 100000 })
    const by = {}
    for (const t of txs) {
      if (t.type === 'transfer') continue
      const parts = (t.splits && t.splits.length) ? t.splits : [{ category_id: t.category_id, amount: t.amount }]
      for (const p of parts) {
        const key = p.category_id || 'other'
        by[key] = (by[key] || 0) + (Number(p.amount) || 0)
      }
    }
    return Object.entries(by).map(([category_id, total]) => ({ category_id, total }))
  },

  dailySum({ startTs = 0, endTs = Date.now() } = {}) {
    // 与 Java dailySumJs 结构一致: 按 (day, type) 分组, 返回 {day, type, total}
    const txs = this.listTransactions({ startTs, endTs, limit: 100000 })
    const by = {}
    for (const t of txs) {
      if (t.type === 'transfer') continue
      const d = new Date(t.occurred_at)
      const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      const key = day + '|' + t.type
      by[key] = by[key] || { day, type: t.type, total: 0 }
      by[key].total += t.amount
    }
    return Object.values(by).sort((a, b) => a.day < b.day ? -1 : 1)
  },

  cleanDuplicates() { return 0 },
  getRecentRawTexts() { return { items: [], count: 0 } },
  clearNotificationLog() { return 0 },
  forceCleanJunkMerchants() { return 0 },
  resetAllMerchants() { return 0 },
  reExtractMerchants() { return { updated: 0, total: 0, skippedEmptyRaw: 0, sameAsBefore: 0 } },
  debugParse() { return null }
}