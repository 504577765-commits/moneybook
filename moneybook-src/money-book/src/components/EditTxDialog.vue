<template>
  <!-- v2.2.80: 交易编辑弹窗 — 金额/时间/商户/分类/备注 全可编辑,带删除 -->
  <transition name="edit-fade">
    <div v-if="visible" class="edit-overlay" @click.self="onCancel">
      <transition name="edit-pop" appear>
        <div v-if="visible" class="edit-card">
          <div class="edit-head">
            <div>
              <div class="edit-title">编辑交易</div>
              <div class="edit-type-tag" :class="tx.type === 'expense' ? 'neg' : 'pos'">
                {{ tx.type === 'expense' ? '支出' : '收入' }}
              </div>
            </div>
            <button class="edit-close" @click="onCancel">✕</button>
          </div>

          <!-- v2.2.80: 金额可编辑 -->
          <div class="edit-field amount-field">
            <label class="edit-label">金额</label>
            <div class="amount-edit-wrap">
              <span class="amount-yen" :class="tx.type === 'expense' ? 'neg' : 'pos'">¥</span>
              <input v-model="form.amount" type="number" inputmode="decimal" step="0.01"
                     class="amount-edit-input" placeholder="0.00">
            </div>
          </div>

          <!-- v2.2.80: 时间可编辑 -->
          <div class="edit-field">
            <label class="edit-label">时间</label>
            <input v-model="form.timeInput" type="datetime-local" class="edit-input">
          </div>

          <!-- 商户 -->
          <div class="edit-field">
            <label class="edit-label">商户</label>
            <input v-model="form.merchant" class="edit-input" placeholder="商户名称" maxlength="32">
          </div>

          <!-- 分类 -->
          <div class="edit-field">
            <label class="edit-label">分类</label>
            <div class="cat-grid">
              <div v-for="c in store.categories[tx.type]" :key="c.id"
                   :class="['cat-pick', form.category_id === c.id ? 'active' : '']"
                   :style="form.category_id === c.id ? {background: c.color, color: '#fff', borderColor: c.color} : {}"
                   @click="form.category_id = c.id">
                <span class="cat-emoji">{{ c.icon }}</span>
                <span class="cat-name">{{ c.name }}</span>
              </div>
            </div>
          </div>

          <!-- 备注 -->
          <div class="edit-field">
            <label class="edit-label">备注</label>
            <input v-model="form.note" class="edit-input" placeholder="添加备注(可加 #tag)" maxlength="60">
          </div>

          <!-- v2.2.80: 底部操作 — 删除(左) + 保存(右) -->
          <div class="edit-actions">
            <button class="edit-btn danger" :disabled="deleting" @click="onDelete">
              {{ deleting ? '删除中…' : '🗑 删除' }}
            </button>
            <button class="edit-btn cancel" @click="onCancel">取消</button>
            <button class="edit-btn confirm" :disabled="saving || !canSave" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </transition>
    </div>
  </transition>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useBookStore } from '../stores/book'
import { updateTransaction, deleteTransaction } from '../db'
import { showToast, showConfirm } from '../utils/dialog'
import dayjs from 'dayjs'

const props = defineProps({
  visible: { type: Boolean, default: false },
  tx: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['close', 'saved', 'deleted'])

const store = useBookStore()
const saving = ref(false)
const deleting = ref(false)

// v2.2.80: 完整表单 — 新增 amount 和 timeInput
const form = reactive({
  amount: '',
  merchant: '',
  category_id: 0,
  note: '',
  timeInput: ''  // datetime-local 格式: YYYY-MM-DDTHH:mm
})

watch(() => props.tx, (tx) => {
  if (tx && tx.id) {
    form.amount = String(tx.amount || '')
    form.merchant = tx.merchant || ''
    form.category_id = tx.category_id
    form.note = tx.note || ''
    // JS datetime-local 需要 YYYY-MM-DDTHH:mm 格式
    form.timeInput = dayjs(tx.occurred_at).format('YYYY-MM-DDTHH:mm')
  }
}, { immediate: true })

// v2.2.80: 保存前校验
const canSave = computed(() => {
  const n = Number(form.amount) || 0
  const cid = form.category_id
  const hasCat = cid && cid !== 0 && cid !== '0'
  return n > 0 && hasCat
})

function onCancel() { emit('close') }

// v2.2.80: 统一派发刷新事件(Home 预算/月对月对比都要刷新)
function dispatchRefreshEvent() {
  window.dispatchEvent(new CustomEvent('moneybook:tx-added', {
    detail: { source: 'edit' }
  }))
}

async function onSave() {
  if (!props.tx.id) { showToast('交易数据异常', 'error'); return }
  if (!canSave.value) { showToast('请输入金额并选择分类', 'error'); return }
  saving.value = true
  try {
    // v2.2.80: 解析编辑后的时间
    let occurredAt = props.tx.occurred_at
    if (form.timeInput) {
      const parsed = dayjs(form.timeInput)
      if (parsed.isValid()) occurredAt = parsed.valueOf()
    }

    const ok = await updateTransaction({
      id: props.tx.id,
      type: props.tx.type,
      amount: Number(form.amount),
      category_id: form.category_id,
      merchant: form.merchant,
      note: form.note,
      occurred_at: occurredAt
    })
    if (ok) {
      showToast('已保存', 'success')
      dispatchRefreshEvent()
      emit('saved', { ...props.tx, amount: Number(form.amount), ...form, occurred_at: occurredAt })
      emit('close')
    } else {
      showToast('保存失败', 'error')
    }
  } catch (e) {
    showToast('保存失败: ' + e.message, 'error')
  } finally {
    saving.value = false
  }
}

// v2.2.80: 删除功能(带确认)
async function onDelete() {
  if (!props.tx.id) { showToast('交易数据异常', 'error'); return }
  const ok = await showConfirm('确定删除这笔交易吗？此操作不可恢复。', '删除确认', {
    confirmText: '删除',
    cancelText: '取消',
    danger: true
  })
  if (!ok) return
  deleting.value = true
  try {
    const removed = await deleteTransaction(props.tx.id)
    if (removed) {
      showToast('已删除', 'success')
      dispatchRefreshEvent()
      emit('deleted', props.tx)
      emit('close')
    } else {
      showToast('删除失败', 'error')
    }
  } catch (e) {
    showToast('删除失败: ' + e.message, 'error')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.edit-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 9998;
  padding: 16px;
}
.edit-card {
  background: var(--card);
  border-radius: 20px;
  width: 100%;
  max-width: 420px;
  max-height: 92vh;
  overflow-y: auto;
  padding: 20px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.edit-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin-bottom: 14px;
}
.edit-title { font-size: 13px; color: var(--text-3); }
.edit-type-tag {
  display: inline-block; margin-top: 4px;
  font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 10px;
}
.edit-type-tag.neg { background: linear-gradient(135deg, #FEE2E2, #FECACA); color: #DC2626; }
.edit-type-tag.pos { background: linear-gradient(135deg, #D1FAE5, #A7F3D0); color: #059669; }
.edit-close {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: var(--bg-2);
  border: none;
  color: var(--text-2);
  font-size: 14px;
  cursor: pointer;
}

.edit-field { margin-bottom: 14px; }
.edit-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 8px;
}
.edit-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  background: var(--bg-2);
  border: 1.5px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-1);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}
.edit-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

/* v2.2.80: 金额编辑 */
.amount-field { margin-bottom: 16px; }
.amount-edit-wrap {
  display: flex; align-items: baseline; justify-content: center;
  padding: 10px 0;
  background: var(--bg-2);
  border-radius: 12px;
  border: 1.5px solid var(--border-light);
}
.amount-yen {
  font-size: 22px; font-weight: 700; margin-right: 4px;
}
.amount-yen.neg { color: #EF4444; }
.amount-yen.pos { color: #10B981; }
.amount-edit-input {
  font-size: 34px; font-weight: 800;
  border: none; outline: none; background: transparent;
  color: var(--text-1);
  width: 65%;
  text-align: center;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}

.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  max-height: 180px;
  overflow-y: auto;
}
.cat-pick {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 4px;
  background: var(--bg-2);
  border: 1.5px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.cat-pick:active { transform: scale(0.96); }
.cat-emoji { font-size: 20px; }
.cat-name { font-size: 11px; color: var(--text-2); }
.cat-pick.active .cat-name { color: #fff; font-weight: 600; }

.edit-actions {
  display: flex; gap: 8px;
  margin-top: 12px;
}
.edit-btn {
  flex: 1;
  padding: 12px 8px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.1s, opacity 0.2s;
  white-space: nowrap;
}
.edit-btn:active { transform: scale(0.97); }
.edit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.edit-btn.danger {
  background: linear-gradient(135deg, #FEE2E2, #FECACA);
  color: #DC2626;
  flex: 0.7;
}
.edit-btn.cancel { background: var(--bg-2); color: var(--text-2); }
.edit-btn.confirm {
  background: linear-gradient(135deg, #6366F1, #8B5CF6);
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

/* 动画 */
.edit-fade-enter-active, .edit-fade-leave-active { transition: opacity 0.25s; }
.edit-fade-enter-from, .edit-fade-leave-to { opacity: 0; }

.edit-pop-enter-active { animation: edit-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1); }
.edit-pop-leave-active { animation: edit-out 0.2s ease; }
@keyframes edit-in {
  0% { transform: scale(0.9) translateY(20px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}
@keyframes edit-out {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.92); opacity: 0; }
}

:root[data-theme="dark"] .edit-overlay { background: rgba(0, 0, 0, 0.7); }
</style>
