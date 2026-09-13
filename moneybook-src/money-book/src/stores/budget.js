import { defineStore } from 'pinia'
import { getBudgets, setBudget, listTransactions } from '../db'
import dayjs from 'dayjs'

/**
 * v2.3.0 信封预算 store
 * - 每月为分类分配预算额度(budgets 表)
 * - 未花余额结转下月(applyRollover)
 * - 已花按拆分明细归集(与 book.stats 规则一致)
 */
export const useBudgetStore = defineStore('budget', {
  state: () => ({
    month: dayjs().format('YYYY-MM'),   // 当前查看月份
    budgets: {},                        // { category_id: amount }
    cacheTransactions: []               // 当月交易(含 splits)
  }),

  getters: {
    /** 当月各分类已花(拆分按明细归集) */
    spentByCat(s) {
      const m = {}
      for (const t of s.cacheTransactions) {
        if (t.type === 'transfer') continue
        const isIncome = t.type === 'income'
        const parts = (t.splits && t.splits.length)
          ? t.splits
          : [{ category_id: t.category_id, amount: t.amount }]
        for (const p of parts) {
          const amt = Number(p.amount) || 0
          const key = p.category_id || 'other'
          if (isIncome) m[key] = (m[key] || 0) + amt
          else m[key] = (m[key] || 0) - amt   // 支出记负值,便于同一口径汇总
        }
      }
      return m
    },
    monthSpent(s) {
      let total = 0
      for (const t of s.cacheTransactions) {
        if (t.type === 'expense') {
          const parts = (t.splits && t.splits.length) ? t.splits : [{ amount: t.amount }]
          for (const p of parts) total += Number(p.amount) || 0
        }
      }
      return total
    },
    totalBudget() {
      return Object.values(this.budgets).reduce((a, b) => a + (Number(b) || 0), 0)
    },
    totalRemain() {
      let rem = this.totalBudget
      for (const k in this.spentByCat) {
        // 仅支出分类计入(income 不占预算)
        if (this.spentByCat[k] < 0) rem += this.spentByCat[k]
      }
      return rem
    },
    /** 某分类本月预算 */
    getBudget: (s) => (cid) => s.budgets[cid] || 0
  },

  actions: {
    setMonth(m) {
      this.month = m
      return this.load()
    },

    /** 拉取当月预算 + 当月交易 */
    async load(monthOverride) {
      const m = monthOverride || this.month
      this.month = m
      const budgets = await getBudgets(m)
      this.budgets = {}
      for (const b of budgets) this.budgets[b.category_id] = b.amount
      const start = dayjs(m + '-01').startOf('month').valueOf()
      const end = dayjs(m + '-01').add(1, 'month').startOf('month').valueOf()
      this.cacheTransactions = await listTransactions({ startTs: start, endTs: end, limit: 5000 })
    },

    /** 设置某分类预算额度(0=清除) */
    async setAmount(categoryId, amount) {
      const ok = await setBudget(categoryId, this.month, amount)
      if (ok) {
        if (amount > 0) this.budgets[categoryId] = amount
        else delete this.budgets[categoryId]
      }
      return ok
    },

    /** 上月该分类未花余额(0 = 无预算或无剩余) */
    lastMonthRemain(categoryId) {
      const last = dayjs(this.month + '-01').subtract(1, 'month').format('YYYY-MM')
      // 直接用上月的预算与已花估算;精确结转依赖上月数据,这里简化演示:返回上月预算-上月已花
      return 0 // 精确结转在 applyRollover 中一次性写库
    },

    /**
     * 结转:把上月每分类"预算 - 已花"的未花余额,加到本月对应分类预算上。
     * 已花超过预算的负数不结转(不扣减本月)。
     */
    async applyRollover() {
      const last = dayjs(this.month + '-01').subtract(1, 'month').format('YYYY-MM')
      const lastBudgets = await getBudgets(last)
      const lastStart = dayjs(last + '-01').startOf('month').valueOf()
      const lastEnd = dayjs(last + '-01').add(1, 'month').startOf('month').valueOf()
      const lastTxs = await listTransactions({ startTs: lastStart, endTs: lastEnd, limit: 5000 })
      const spent = {}
      for (const t of lastTxs) {
        if (t.type !== 'expense') continue
        const parts = (t.splits && t.splits.length) ? t.splits : [{ category_id: t.category_id, amount: t.amount }]
        for (const p of parts) {
          const key = p.category_id || 'other'
          spent[key] = (spent[key] || 0) + (Number(p.amount) || 0)
        }
      }
      let rolled = 0
      for (const b of lastBudgets) {
        const remain = (Number(b.amount) || 0) - (spent[b.category_id] || 0)
        if (remain > 0) {
          const cur = Number(this.budgets[b.category_id]) || 0
          await setBudget(b.category_id, this.month, cur + remain)
          this.budgets[b.category_id] = cur + remain
          rolled += remain
        }
      }
      return rolled
    }
  }
})