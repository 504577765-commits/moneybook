/**
 * v2.2.3 重写
 * 之前 bug:JS 端用 @capacitor-community/sqlite 创建了一份独立的 moneybook.db
 *          和 Java 端的 moneybook.db 是不同文件!所以 widget 看到 Java 端数据(对),
 *          APP 内部看到 JS 端数据(可能为空/不全)。
 * 修复:所有读写都走 Java 端 MoneyNotifier 桥接。JS 端不再用本地 SQLite。
 *
 * v2.2.47: 精简 - 删 categories.js 冗余,只留桥接 + 常量
 */

const cap = () => window.Capacitor?.Plugins?.MoneyNotifier
const hasCap = () => !!cap()

// v2.3.1: Web/预览环境内存降级数据层(无原生桥时全功能可用)
import MEM from './memory'

/** 支付来源常量(原 categories.js) */
export const PAYMENT_SOURCES = {
  ALIPAY:  { id: 'alipay',  name: '支付宝', icon: '💙' },
  WECHAT:  { id: 'wechat',  name: '微信',   icon: '💚' },
  BANK:    { id: 'bank',    name: '银行卡', icon: '💳' },
  UNIONPAY:{ id: 'unionpay',name: '银联',   icon: '💠' },
  CASH:    { id: 'cash',    name: '现金',   icon: '💵' },
  OTHER:   { id: 'other',   name: '其他',   icon: '📦' }
}

/** v2.2.47 精简: JS 端不写自己的分类库,改用 Java 端返回的 store.categories */
/* DEFAULT_EXPENSE_CATEGORIES / DEFAULT_INCOME_CATEGORIES 删 — JS 端用 store.categories */


// ===== 分类 =====

export async function getAllCategories(type) {
  if (!hasCap()) return MEM.getAllCategories(type)
  try {
    // v2.2.5:Java 端支持 type 参数,减少数据传输
    const r = await cap().getCategoriesJs({ type: type || null })
    const cats = (r.categories || []).map(c => ({
      ...c,
      keywords: safeParse(c.keywords)
    }))
    return cats
  } catch (e) {
    console.error('getAllCategories err', e)
    return []
  }
}

export async function addCategory(cat) {
  // v2.2.3: 暂不通过 JS 改分类
  console.warn('addCategory: JS 端改分类功能暂未实现')
}

export async function deleteCategory(id) {
  console.warn('deleteCategory: 暂未实现')
}

// ===== 交易(全部走 Java 端) =====

// v2.2.78: 恢复手动记账(用户需求变更) — 仍走 Java 端保证 5 层去重
export async function insertTransaction(tx) {
  if (!hasCap()) return MEM.insertTransaction(tx)
  try {
    const r = await cap().insertTransactionJs({
      type: tx.type,
      amount: tx.amount,
      category_id: String(tx.category_id || 0),
      source: tx.source || 'manual',
      merchant: tx.merchant || '',
      note: tx.note || '',
      raw_text: tx.raw_text || '',
      occurred_at: tx.occurred_at || Date.now(),
      account_id: tx.account_id || 0,
      to_account_id: tx.to_account_id || 0
    })
    return { ok: r.success === true, id: tx.id || null }
  } catch (e) {
    console.error('insertTransaction err', e)
    return { ok: false, id: null }
  }
}

export async function updateTransaction(tx) {
  if (!hasCap()) return MEM.updateTransaction(tx)
  try {
    const r = await cap().updateTransactionJs({
      id: tx.id,
      type: tx.type,
      amount: tx.amount,
      category_id: tx.category_id,
      merchant: tx.merchant,
      note: tx.note,
      occurred_at: tx.occurred_at,
      account_id: tx.account_id || 0,
      to_account_id: tx.to_account_id || 0
    })
    return r.success === true
  } catch (e) {
    console.error('updateTransaction err', e)
    return false
  }
}

// v2.3.0: upsert(撤销/恢复用)
export async function upsertTransaction(tx) {
  if (!hasCap()) return MEM.upsertTransaction(tx)
  try {
    const r = await cap().upsertTransactionJs({
      id: tx.id,
      type: tx.type,
      amount: tx.amount,
      category_id: String(tx.category_id || 0),
      source: tx.source || 'other',
      merchant: tx.merchant || '',
      note: tx.note || '',
      raw_text: tx.raw_text || '',
      occurred_at: tx.occurred_at || Date.now(),
      account_id: tx.account_id || 0,
      to_account_id: tx.to_account_id || 0
    })
    return r.success === true
  } catch (e) {
    console.error('upsertTransaction err', e)
    return false
  }
}

// v2.3.0: 批量删除
export async function deleteTransactions(ids) {
  if (!hasCap()) return MEM.deleteTransactions(ids)
  if (!ids?.length) return 0
  try {
    const r = await cap().deleteTransactionsJs({ ids })
    return r?.removed || 0
  } catch (e) {
    console.error('deleteTransactions err', e)
    return 0
  }
}

// v2.3.0: 批量改分类
export async function batchSetCategory(ids, categoryId) {
  if (!hasCap()) return MEM.batchSetCategory(ids, categoryId)
  if (!ids?.length) return 0
  try {
    const r = await cap().batchSetCategoryJs({ ids, category_id: categoryId })
    return r?.updated || 0
  } catch (e) {
    console.error('batchSetCategory err', e)
    return 0
  }
}

// v2.3.0: 保存拆分明细
export async function saveTxSplits(txId, splits) {
  if (!hasCap()) return MEM.saveTxSplits(txId, splits)
  try {
    const r = await cap().saveTxSplitsJs({ tx_id: txId, splits: splits || [] })
    return r.success === true
  } catch (e) {
    console.error('saveTxSplits err', e)
    return false
  }
}

export async function deleteTransaction(id) {
  if (!hasCap()) return MEM.deleteTransaction(id)
  try {
    const r = await cap().deleteTransactionJs({ id })
    return r.success === true
  } catch (e) {
    console.error('deleteTransaction err', e)
    return false
  }
}

export async function listTransactions({ startTs, endTs, type, categoryId, keyword, limit = 500 } = {}) {
  if (!hasCap()) return MEM.listTransactions({ startTs, endTs, type, categoryId, keyword, limit })
  try {
    const r = await cap().listTransactionsJs({
      startTs: startTs || 0,
      endTs: endTs || Date.now() + 86400000,
      limit
    })
    let txs = r.transactions || []
    if (type) txs = txs.filter(t => t.type === type)
    if (categoryId) txs = txs.filter(t => t.category_id === categoryId)
    if (keyword) {
      const kw = keyword.toLowerCase()
      txs = txs.filter(t => (t.merchant || '').toLowerCase().includes(kw) || (t.note || '').toLowerCase().includes(kw))
    }
    return txs
  } catch (e) {
    console.error('listTransactions err', e)
    return []
  }
}

/**
 * v2.2.17: 清理数据库里的重复交易
 * @returns {Promise<number>} 删除的条数
 */
export async function cleanDuplicates() {
  if (!hasCap()) return MEM.cleanDuplicates()
  if (!cap().cleanDuplicatesJs) {
    console.warn('cleanDuplicatesJs 不可用')
    return 0
  }
  try {
    const r = await cap().cleanDuplicatesJs()
    return r?.removed || 0
  } catch (e) {
    console.error('cleanDuplicates err', e)
    return 0
  }
}

/**
 * v2.2.40: 获取最近 N 条通知原文(用于显示给用户)
 * @returns {Promise<{items, count}>}
 */
export async function getRecentRawTexts(limit = 50) {
  if (!hasCap()) return MEM.getRecentRawTexts(limit)
  if (!cap().getRecentRawTextsJs) {
    return { items: [], count: 0 }
  }
  try {
    const r = await cap().getRecentRawTextsJs({ limit })
    return r || { items: [], count: 0 }
  } catch (e) {
    console.error('getRecentRawTexts err', e)
    return { items: [], count: 0 }
  }
}

/**
 * v2.2.40: 清空通知记录
 * @returns {Promise<number>} 清除条数
 */
export async function clearNotificationLog() {
  if (!cap().clearNotificationLogJs) {
    return 0
  }
  try {
    const r = await cap().clearNotificationLogJs()
    return r?.cleared || 0
  } catch (e) {
    return 0
  }
}

/**
 * v2.2.43: 强力清理 — 把所有 merchant 里含"余额/付款额/支付额/付款/扣款/费用"等垃圾关键词的清成"未知商户"
 * @returns {Promise<number>} 清理条数
 */
export async function forceCleanJunkMerchants() {
  if (!cap().forceCleanJunkMerchantsJs) {
    console.warn('forceCleanJunkMerchantsJs 不可用')
    return 0
  }
  try {
    const r = await cap().forceCleanJunkMerchantsJs()
    return r?.cleaned || 0
  } catch (e) {
    console.error('forceCleanJunkMerchants err', e)
    return 0
  }
}

/**
 * v2.2.37: 把所有商户重置为"未知商户"
 * @returns {Promise<number>} 重置的条数
 */
export async function resetAllMerchants() {
  if (!cap().resetAllMerchantsJs) {
    console.warn('resetAllMerchantsJs 不可用')
    return 0
  }
  try {
    const r = await cap().resetAllMerchantsJs()
    return r?.reset || 0
  } catch (e) {
    console.error('resetAllMerchants err', e)
    return 0
  }
}

/**
 * v2.2.34: 重新识别所有商户,返回详细统计
 * @returns {Promise<{updated, total, skippedEmptyRaw, sameAsBefore}>}
 */
export async function reExtractMerchants() {
  if (!cap().reExtractMerchantsJs) {
    console.warn('reExtractMerchantsJs 不可用')
    return { updated: 0, total: 0, skippedEmptyRaw: 0, sameAsBefore: 0 }
  }
  try {
    const r = await cap().reExtractMerchantsJs()
    return r || { updated: 0, total: 0, skippedEmptyRaw: 0, sameAsBefore: 0 }
  } catch (e) {
    console.error('reExtractMerchants err', e)
    return { updated: 0, total: 0, skippedEmptyRaw: 0, sameAsBefore: 0 }
  }
}

/**
 * v2.2.30: 实时调试 — 粘贴一条文本,显示每步解析结果
 * @param {string} text 原始文本
 * @param {string} pkg 包名 (默认 sms)
 * @returns {Promise<object>} 解析结果
 */
export async function debugParse(text, pkg = 'sms') {
  if (!cap().debugParseJs) {
    console.warn('debugParseJs 不可用')
    return null
  }
  try {
    return await cap().debugParseJs({ text, pkg })
  } catch (e) {
    console.error('debugParse err', e)
    return null
  }
}

export async function sumByCategory({ startTs, endTs, type } = {}) {
  let items
  if (!hasCap()) {
    items = MEM.sumByCategory({ startTs, endTs })
  } else {
    try {
      const r = await cap().sumByCategoryJs({ startTs: startTs || 0, endTs: endTs || Date.now() })
      items = r.items || []
    } catch (e) {
      console.error('sumByCategory err', e)
      return []
    }
  }
  if (type) {
    // JS 端拿到的是全部,需要按 type 过滤需要先 join categories
    const cats = await getAllCategories()
    const typeMap = {}
    cats.forEach(c => { typeMap[c.id] = c.type })
    items = items.filter(i => typeMap[i.category_id] === type)
  }
  return items
}

export async function dailySum({ startTs, endTs, type } = {}) {
  let items
  if (!hasCap()) {
    items = MEM.dailySum({ startTs, endTs })
  } else {
    try {
      const r = await cap().dailySumJs({ startTs: startTs || 0, endTs: endTs || Date.now() })
      items = r.items || []
    } catch (e) {
      console.error('dailySum err', e)
      return []
    }
  }
  if (type) items = items.filter(i => i.type === type)
  return items
}

// ===== 兼容旧 API =====

export async function checkRawTextExists(raw) {
  // Java 端会自动去重,JS 端不用单独检查
  return false
}

export async function initDB() {
  // 不再需要初始化本地 SQLite
  // 但保留这个函数(有些地方可能还在调)
  return null
}

function safeParse(s) {
  if (!s) return []
  try { return JSON.parse(s) } catch { return [] }
}

/**
 * v2.2.62: 导出所有交易到 CSV 文件
 * 返回 {success, filePath, count}
 */
export async function exportTransactionsToCsv() {
  if (!hasCap()) return { success: false, error: 'No Capacitor' }
  try {
    const r = await cap().exportTransactionsToCsv()
    return r || { success: false }
  } catch (e) {
    console.error('exportTransactionsToCsv err', e)
    return { success: false, error: e.message }
  }
}

// ===== v2.3.0 账户 =====

export async function getAllAccounts() {
  if (!hasCap()) return MEM.getAllAccounts()
  try {
    const r = await cap().getAllAccountsJs()
    return r.accounts || []
  } catch (e) {
    console.error('getAllAccounts err', e)
    return []
  }
}

export async function insertAccount({ name, type, icon }) {
  if (!hasCap()) return MEM.insertAccount({ name, type, icon })
  try {
    const r = await cap().insertAccountJs({ name, type, icon })
    return { ok: r.success === true, id: r.id || 0 }
  } catch (e) {
    console.error('insertAccount err', e)
    return { ok: false, id: 0 }
  }
}

export async function updateAccount({ id, name, type, icon, initial_balance }) {
  if (!hasCap()) return MEM.updateAccount({ id, name, type, icon, initial_balance })
  try {
    const r = await cap().updateAccountJs({ id, name, type, icon, initial_balance })
    return r.success === true
  } catch (e) {
    console.error('updateAccount err', e)
    return false
  }
}

export async function deleteAccount(id) {
  if (!hasCap()) return MEM.deleteAccount(id)
  try {
    const r = await cap().deleteAccountJs({ id })
    return r.success === true
  } catch (e) {
    console.error('deleteAccount err', e)
    return false
  }
}

// ===== v2.3.0 预算 =====

/** 某月各分类预算 */
export async function getBudgets(month) {
  if (!hasCap()) return MEM.getBudgets(month)
  try {
    const r = await cap().getBudgetsJs({ month })
    return r.budgets || []
  } catch (e) {
    console.error('getBudgets err', e)
    return []
  }
}

export async function setBudget(categoryId, month, amount) {
  if (!hasCap()) return MEM.setBudget(categoryId, month, amount)
  try {
    const r = await cap().setBudgetJs({ category_id: categoryId, month, amount })
    return r.success === true
  } catch (e) {
    console.error('setBudget err', e)
    return false
  }
}

// ===== v2.3.0 商户映射 =====

export async function getMerchantAccountId(merchant) {
  if (!hasCap()) return MEM.getMerchantAccountId(merchant)
  try {
    const r = await cap().getMerchantAccountIdJs({ merchant })
    return r.account_id || 0
  } catch (e) {
    console.error('getMerchantAccountId err', e)
    return 0
  }
}

export async function setMerchantAccount(merchant, accountId) {
  if (!hasCap()) return MEM.setMerchantAccount(merchant, accountId)
  try {
    const r = await cap().setMerchantAccountJs({ merchant, account_id: accountId })
    return r.success === true
  } catch (e) {
    console.error('setMerchantAccount err', e)
    return false
  }
}

/** 商户记忆分类(null=无) */
export async function getMerchantCategory(merchant) {
  if (!hasCap()) return MEM.getMerchantCategory(merchant)
  try {
    const r = await cap().getMerchantCategoryJs({ merchant })
    return r.category_id || null
  } catch (e) {
    console.error('getMerchantCategory err', e)
    return null
  }
}

export async function setMerchantCategory(merchant, categoryId) {
  if (!hasCap()) return MEM.setMerchantCategory(merchant, categoryId)
  try {
    const r = await cap().setMerchantCategoryJs({ merchant, category_id: categoryId })
    return r.success === true
  } catch (e) {
    console.error('setMerchantCategory err', e)
    return false
  }
}
