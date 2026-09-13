import { defineStore } from 'pinia'
import {
  getAllCategories, updateTransaction,
  deleteTransaction, deleteTransactions, batchSetCategory,
  insertTransaction, upsertTransaction, saveTxSplits,
  listTransactions, sumByCategory, dailySum,
  getAllAccounts, insertAccount, updateAccount, deleteAccount,
  getMerchantCategory, setMerchantCategory
} from '../db'

export const useBookStore = defineStore('book', {
  state: () => ({
    ready: false,
    categories: { expense: [], income: [] },
    transactions: [],
    currentRange: 'month', // day / week / month
    currentAnchor: Date.now(),
    autoListen: true,
    pendingAutoTx: [],  // 自动识别但未确认的交易
    accounts: [],       // v2.3.0 多账户
    // v2.3.0 撤销/重做栈(会话级)
    undoStack: [],
    redoStack: []
  }),

  getters: {
    // 当前范围非转账类交易金额统计(排除 transfer, 拆分按明细归集)
    stats(s) {
      let expense = 0, income = 0
      const byCat = {}
      for (const t of s.transactions) {
        if (t.type === 'transfer') continue
        const isIncome = t.type === 'income'
        const parts = (t.splits && t.splits.length) ? t.splits : [{ category_id: t.category_id, amount: t.amount }]
        for (const p of parts) {
          const amt = Number(p.amount) || 0
          if (isIncome) income += amt; else expense += amt
          const key = p.category_id || 'other'
          byCat[key] = (byCat[key] || 0) + amt
        }
      }
      return { expense, income, balance: income - expense, byCat }
    },
    accountMap(s) {
      const m = {}
      for (const a of s.accounts) m[a.id] = a
      return m
    }
  },

  actions: {
    async init() {
      // v2.2.3:JS 端所有数据走 Java 端,不再 initDB
      await this.refreshCategories()
      await this.refreshAccounts()
      await this.refreshTransactions()
      this.ready = true
    },

    async refreshCategories() {
      const expense = await getAllCategories('expense')
      const income = await getAllCategories('income')
      this.categories = { expense, income }
    },

    async refreshAccounts() {
      this.accounts = await getAllAccounts()
    },

    async refreshTransactions() {
      const { start, end } = this.getRangeBounds()
      this.transactions = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
    },

    getRangeBounds() {
      const t = this.currentAnchor
      const d = new Date(t)
      if (this.currentRange === 'day') {
        const s = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
        const e = s + 86400000
        return { start: s, end: e }
      } else if (this.currentRange === 'week') {
        const day = d.getDay() || 7
        const monday = new Date(d.getFullYear(), d.getMonth(), d.getDate() - (day - 1))
        const s = monday.getTime()
        const e = s + 7 * 86400000
        return { start: s, end: e }
      } else {
        const s = new Date(d.getFullYear(), d.getMonth(), 1).getTime()
        const e = new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime()
        return { start: s, end: e }
      }
    },

    setRange(range) {
      this.currentRange = range
      this.refreshTransactions()
    },

    setAnchor(ts) {
      this.currentAnchor = ts
      this.refreshTransactions()
    },

    // ===== v2.3.0 撤销/重做 =====

    /** 记录一次交易操作前的快照(更新/删除前调用) */
    pushUndoSnapshot(id, beforeTx) {
      this.undoStack.push({ id, before: beforeTx, existed: !!beforeTx })
      if (this.undoStack.length > 100) this.undoStack.shift()
      this.redoStack = []
    },

    canUndo() { return this.undoStack.length > 0 },
    canRedo() { return this.redoStack.length > 0 },

    /** 撤销最近一次交易操作 */
    async undo() {
      const last = this.undoStack.pop()
      if (!last) return false
      // 当前状态入 redo
      const cur = this.transactions.find(t => t.id === last.id)
      this.redoStack.push({ id: last.id, before: last.existed ? last.before : null, existed: false, after: cur ? { ...cur } : null })
      try {
        if (last.existed) {
          // 恢复为快照(upsert + splits)
          await upsertTransaction(last.before)
          if (last.before.splits && last.before.splits.length) {
            await saveTxSplits(last.id, last.before.splits)
          }
        } else {
          // 撤销新增 = 删除
          await deleteTransaction(last.id)
        }
      } finally {
        await this.refreshTransactions()
        this.refreshWidget()
      }
      return true
    },

    /** 重做 */
    async redo() {
      const last = this.redoStack.pop()
      if (!last) return false
      this.undoStack.push({ id: last.id, before: last.after ? last.after : null, existed: !!last.after })
      try {
        if (last.after) {
          await upsertTransaction(last.after)
          if (last.after.splits && last.after.splits.length) {
            await saveTxSplits(last.id, last.after.splits)
          }
        } else {
          await deleteTransaction(last.id)
        }
      } finally {
        await this.refreshTransactions()
        this.refreshWidget()
      }
      return true
    },

    // ===== v2.3.0 交易操作(带快照) =====

    async addTransactionTx(tx) {
      if (!tx.id) tx.id = 'tx_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
      // 新增入撤销栈(undo = 删除)
      this.pushUndoSnapshot(tx.id, null)
      const r = await insertTransaction(tx)
      if (r.ok) {
        if (tx.splits && tx.splits.length) {
          await saveTxSplits(tx.id, tx.splits)
        }
        await this.refreshTransactions()
        this.refreshWidget()
        window.dispatchEvent(new CustomEvent('moneybook:tx-added'))
      }
      return r
    },

    async saveTransactionTx(tx, beforeTx) {
      if (beforeTx) this.pushUndoSnapshot(tx.id, beforeTx)
      const ok = await updateTransaction(tx)
      if (ok && tx.splits && tx.splits.length) {
        await saveTxSplits(tx.id, tx.splits)
      }
      await this.refreshTransactions()
      this.refreshWidget()
      return ok
    },

    async removeTransactionTx(tx) {
      this.pushUndoSnapshot(tx.id, tx)
      const ok = await deleteTransaction(tx.id)
      await this.refreshTransactions()
      this.refreshWidget()
      return ok
    },

    /** 批量删除(带快照,支持撤销) */
    async batchDeleteTransactions(txs) {
      const ids = []
      for (const t of txs) {
        this.pushUndoSnapshot(t.id, t)
        ids.push(t.id)
      }
      const n = await deleteTransactions(ids)
      await this.refreshTransactions()
      this.refreshWidget()
      return n
    },

    /** 批量改分类(带快照,支持撤销; 有拆分的交易跳过) */
    async batchSetCategoryTx(txs, categoryId) {
      const ids = []
      for (const t of txs) {
        if (t.splits && t.splits.length) continue
        this.pushUndoSnapshot(t.id, t)
        ids.push(t.id)
      }
      const n = await batchSetCategory(ids, categoryId)
      await this.refreshTransactions()
      this.refreshWidget()
      return n
    },

    // ===== v2.3.0 账户 =====

    async saveAccount(acc) {
      const ok = acc.id
        ? await updateAccount(acc)
        : (await insertAccount({ name: acc.name, type: acc.type, icon: acc.icon })).ok
      await this.refreshAccounts()
      return ok
    },

    async removeAccount(id) {
      const ok = await deleteAccount(id)
      await this.refreshAccounts()
      return ok
    },

    /** 转账: 创建 type=transfer 交易, 转出 account_id / 转入 to_account_id */
    async createTransfer({ fromAcc, toAcc, amount, occurredAt = Date.now(), note = '' }) {
      const id = 'tx_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
      const tx = {
        id,
        type: 'transfer',
        amount,
        category_id: null,
        source: 'manual',
        merchant: '转账',
        note,
        occurred_at: occurredAt,
        account_id: fromAcc,
        to_account_id: toAcc,
        splits: []
      }
      return this.addTransactionTx(tx)
    },

    // ===== v2.3.0 商户记忆 =====

    /** 记录商户→分类 记忆(供下次手动记账自动带出) */
    async rememberMerchantCategory(merchant, categoryId) {
      if (!merchant || merchant === '未知商户') return
      try {
        await setMerchantCategory(merchant, categoryId)
      } catch (e) { console.error('rememberMerchantCategory err', e) }
    },

    // ===== 兼容旧接口(内部自动带上快照) =====

    async addTransaction(tx) {
      // v2.2.60: 纯自动,不再支持手动添加交易(用户要求)
      console.warn('addTransaction 已废弃 — 请使用 addTransactionTx')
      return false
    },

    async updateTransaction(tx, beforeTx) {
      if (beforeTx) await this.saveTransactionTx(tx, beforeTx)
      else { await updateTransaction(tx); await this.refreshTransactions(); this.refreshWidget() }
    },

    async deleteTransaction(id, tx) {
      if (tx) await this.removeTransactionTx(tx)
      else { await deleteTransaction(id); await this.refreshTransactions(); this.refreshWidget() }
    },

    async handleNotification({ title, content, pkg, postedAt }) {
      // v2.2.47: 精简 — JS 端不解析通知,所有记账由 Java 端负责
      return null
    },

    // 统计相关(兼容旧接口,内部基于当前范围交易,支持拆分)
    async getSummary() {
      const s = this.stats
      // 当前范围的分类/商户排行等补充
      return { expense: s.expense, income: s.income, balance: s.balance, byCat: s.byCat }
    },

    async getDailyTrend() {
      const { start, end } = this.getRangeBounds()
      return await dailySum({ startTs: start, endTs: end })
    },

    // v2.2.1:实时刷新桌面小组件
    async refreshWidget() {
      try {
        if (window.Capacitor?.Plugins?.MoneyNotifier?.refreshWidget) {
          await window.Capacitor.Plugins.MoneyNotifier.refreshWidget()
        }
      } catch (e) {
        console.error('refreshWidget err', e)
      }
    }
  }
})