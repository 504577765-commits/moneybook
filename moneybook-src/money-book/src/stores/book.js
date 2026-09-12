import { defineStore } from 'pinia'
import {
  getAllCategories, updateTransaction,
  deleteTransaction, listTransactions, sumByCategory, dailySum
} from '../db'

export const useBookStore = defineStore('book', {
  state: () => ({
    ready: false,
    categories: { expense: [], income: [] },
    transactions: [],
    currentRange: 'month', // day / week / month
    currentAnchor: Date.now(),
    autoListen: true,
    pendingAutoTx: []  // 自动识别但未确认的交易
  }),

  actions: {
    async init() {
      // v2.2.3:JS 端所有数据走 Java 端,不再 initDB
      await this.refreshCategories()
      await this.refreshTransactions()
      this.ready = true
    },

    async refreshCategories() {
      const expense = await getAllCategories('expense')
      const income = await getAllCategories('income')
      this.categories = { expense, income }
    },

    async refreshTransactions() {
      const { start, end } = this.getRangeBounds()
      this.transactions = await listTransactions({ startTs: start, endTs: end })
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

    async addTransaction(tx) {
      // v2.2.60: 纯自动,不再支持手动添加交易(用户要求)
      console.warn('addTransaction 已废弃 — 记账本只支持自动识别,不再支持手动添加')
      return false
    },

    async updateTransaction(tx) {
      await updateTransaction(tx)
      await this.refreshTransactions()
      this.refreshWidget()
    },

    async deleteTransaction(id) {
      await deleteTransaction(id)
      await this.refreshTransactions()
      this.refreshWidget()
    },

    async handleNotification({ title, content, pkg, postedAt }) {
      // v2.2.47: 精简 — JS 端不解析通知,所有记账由 Java 端负责
      // 保留函数体为空,避免破坏其他可能的调用
      return null
    },

    // 统计相关
    async getSummary() {
      const { start, end } = this.getRangeBounds()
      const rows = await sumByCategory({ startTs: start, endTs: end })
      let expense = 0, income = 0
      const byCat = {}
      for (const r of rows) {
        byCat[r.category_id] = r.total
        // 区分需要看 type
      }
      const all = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
      for (const t of all) {
        if (t.type === 'expense') expense += t.amount
        else income += t.amount
      }
      return { expense, income, balance: income - expense, byCat }
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
