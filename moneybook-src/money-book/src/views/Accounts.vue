<template>
  <div class="page accounts">
    <div class="page-header fade-in-up">
      账户
      <span class="header-action" @click="router.push('/')">返回</span>
    </div>

    <!-- 净资产总览 -->
    <div class="a-hero fade-in-up">
      <div class="a-hero-bg"></div>
      <div class="a-label">净资产(全部账户)</div>
      <div class="a-total">¥{{ fmt(netWorth) }}</div>
      <div class="a-sub">{{ activeAccounts.length }} 个账户 · 含期初余额</div>
    </div>

    <!-- 账户列表 -->
    <div class="group fade-in-up" style="animation-delay: 0.06s">
      <div class="group-label">
        我的账户
        <button class="a-add-btn" @click="openAdd">＋ 新账户</button>
      </div>
      <div class="group-card a-list">
        <div v-for="a in activeAccounts" :key="a.id" class="a-row tappable" @click="openEdit(a)">
          <span class="a-icon">{{ a.icon || '💰' }}</span>
          <div class="a-main">
            <div class="a-name">{{ a.name }}</div>
            <div class="a-type">{{ typeLabel(a.type) }}</div>
          </div>
          <div class="a-balance">¥{{ fmt(a.balance) }}</div>
        </div>
        <div v-if="!activeAccounts.length" class="empty">
          <div class="ico">👛</div>
          <div>还没有账户,点击右上角新增</div>
        </div>
      </div>
    </div>

    <!-- 已归档账户 -->
    <div v-if="archivedAccounts.length" class="group fade-in-up" style="animation-delay: 0.1s">
      <div class="group-label">已归档</div>
      <div class="group-card a-list">
        <div v-for="a in archivedAccounts" :key="a.id" class="a-row">
          <span class="a-icon" style="filter: grayscale(1); opacity: .6;">{{ a.icon || '💰' }}</span>
          <div class="a-main">
            <div class="a-name" style="color: var(--text-3);">{{ a.name }}</div>
            <div class="a-type">{{ typeLabel(a.type) }} · 归档</div>
          </div>
          <div class="a-balance" style="color: var(--text-3);">¥{{ fmt(a.balance) }}</div>
        </div>
      </div>
    </div>

    <!-- 转账按钮 -->
    <button class="a-transfer-btn fade-in-up" @click="openTransfer">🔁 账户间转账</button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '../stores/book'
import { showPrompt, showConfirm, showToast, showAlert } from '../utils/dialog'

const router = useRouter()
const store = useBookStore()

const ACCOUNT_TYPES = [
  { v: 'cash', n: '现金', icon: '💵' },
  { v: 'bank', n: '银行卡', icon: '💳' },
  { v: 'alipay', n: '支付宝', icon: '💙' },
  { v: 'wechat', n: '微信', icon: '💚' },
  { v: 'other', n: '其他', icon: '💰' }
]
function typeLabel(t) { return ACCOUNT_TYPES.find(x => x.v === t)?.n || t || '其他' }

const accounts = computed(() => store.accounts)
const activeAccounts = computed(() => accounts.value.filter(a => !a.archived))
const archivedAccounts = computed(() => accounts.value.filter(a => a.archived))
const netWorth = computed(() => accounts.value.reduce((s, a) => s + (Number(a.balance) || 0), 0))
const fmt = (n) => (Number(n) || 0).toFixed(2)

function pickType(current) {
  return new Promise((resolve) => {
    const labels = ACCOUNT_TYPES.map(t => `${t.icon} ${t.n}`)
    showAlert(
      labels.map((l, i) => `${i + 1}. ${l}`).join('\n'),
      '选择账户类型', { confirmText: '取消' }
    ).then(() => resolve(current))
  })
}

// 新增账户入口
function openAdd() {
  showAccountForm(null)
}

async function showAccountForm(template) {
  const isNew = !template
  // 名称
  const name = await showPrompt('账户名称', template?.name || '', isNew ? '新增账户' : '编辑账户', { maxlength: 12 })
  if (name === null) return
  if (!String(name).trim()) { showToast('名称不能为空', 'error'); return }
  // 类型
  const typeLabels = ACCOUNT_TYPES.map((t, i) => `${i + 1}.${t.n}`).join('  ')
  const typeStr = await showPrompt(`账户类型\n${typeLabels}\n(输入数字 1-${ACCOUNT_TYPES.length})`, typeIndex(template?.type) + 1, '账户类型')
  if (typeStr === null) return
  const ti = Math.min(Math.max(parseInt(typeStr) || 1, 1), ACCOUNT_TYPES.length) - 1
  const type = ACCOUNT_TYPES[ti].v
  const icon = ACCOUNT_TYPES[ti].icon
  if (isNew) {
    const ok = await store.saveAccount({ name: name.trim(), type, icon })
    showToast(ok ? '已新增账户' : '新增失败', ok ? 'success' : 'error')
  } else {
    // 编辑: 保留期初余额(当前余额=期初+流水,若覆盖会双重计入),仅改名称/类型/图标
    const ok = await store.saveAccount({
      id: template.id, name: name.trim(), type, icon,
      initial_balance: template.initial_balance || 0
    })
    if (ok) showToast('已保存', 'success')
  }
  store.refreshAccounts()
}

function typeIndex(t) { return Math.max(0, ACCOUNT_TYPES.findIndex(x => x.v === t)) }

async function openEdit(a) {
  await showAccountForm(a)
  const del = await showConfirm('要归档该账户吗?(历史流水保留)', '归档账户', { confirmText: '归档', cancelText: '取消' })
  if (del) {
    await store.removeAccount(a.id)
    showToast('已归档', 'success')
  }
}

// 转账
async function openTransfer() {
  const acts = activeAccounts.value
  if (acts.length < 2) { showToast('至少需要 2 个账户才能转账', 'error'); return }
  const fromLabels = acts.map((a, i) => `${i + 1}.${a.icon}${a.name}`).join('\n')
  const fromStr = await showPrompt(`从哪个账户转出?\n${fromLabels}`, '1', '转账')
  if (fromStr === null) return
  const fi = Math.min(Math.max(parseInt(fromStr) || 1, 1), acts.length) - 1
  const from = acts[fi]
  const toActs = acts.filter((_, i) => i !== fi)
  const toLabels = toActs.map((a, i) => `${i + 1}.${a.icon}${a.name}`).join('\n')
  const toStr = await showPrompt(`转到哪个账户?\n${toLabels}`, '1', '转账')
  if (toStr === null) return
  const ti = Math.min(Math.max(parseInt(toStr) || 1, 1), toActs.length) - 1
  const to = toActs[ti]
  const amountStr = await showPrompt(`转账金额(元)\n${from.icon}${from.name} → ${to.icon}${to.name}`, '', '转账', { placeholder: '0.00', type: 'number' })
  if (amountStr === null) return
  const amount = Number(amountStr) || 0
  if (amount <= 0) { showToast('金额必须大于 0', 'error'); return }
  await store.createTransfer({ fromAcc: from.id, toAcc: to.id, amount })
  showToast(`已转账 ¥${amount.toFixed(2)}`, 'success')
}

onMounted(() => {
  if (store.ready) store.refreshAccounts()
})
</script>

<style scoped>
.accounts { padding: 12px 14px 100px; }

.a-hero {
  position: relative;
  background: var(--gradient);
  color: #fff;
  border-radius: var(--radius-xl);
  padding: 20px 18px;
  box-shadow: var(--shadow-lg);
  margin-bottom: 14px;
  overflow: hidden;
}
.a-hero-bg {
  position: absolute; inset: 0;
  background: radial-gradient(circle at 85% -20%, rgba(255,255,255,.3) 0%, transparent 50%);
  pointer-events: none;
}
.a-label { font-size: 12px; opacity: .85; position: relative; }
.a-total { font-size: 34px; font-weight: 800; margin: 4px 0 2px; font-variant-numeric: tabular-nums; position: relative; }
.a-sub { font-size: 12px; opacity: .8; position: relative; }

.group-label { display: flex; justify-content: space-between; align-items: center; }
.a-add-btn {
  font-size: 13px; color: var(--primary);
  background: var(--primary-bg); padding: 5px 12px; border-radius: 999px; font-weight: 600;
}
.a-list { display: flex; flex-direction: column; }
.a-row { display: flex; align-items: center; gap: 12px; padding: 12px 4px; border-bottom: 1px solid var(--border-light); }
.a-row:last-child { border-bottom: none; }
.a-icon {
  width: 42px; height: 42px; border-radius: 12px; background: var(--primary-bg);
  display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0;
}
.a-main { flex: 1; min-width: 0; }
.a-name { font-size: 15px; font-weight: 700; color: var(--text-1); }
.a-type { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.a-balance { font-size: 16px; font-weight: 800; color: var(--text-1); font-variant-numeric: tabular-nums; }

.a-transfer-btn {
  position: fixed; left: 50%; bottom: 84px; transform: translateX(-50%);
  background: var(--gradient); color: #fff;
  padding: 12px 28px; border-radius: 999px; font-size: 15px; font-weight: 600;
  box-shadow: var(--shadow-lg); z-index: 90;
}
</style>