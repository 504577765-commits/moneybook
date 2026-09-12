<template>
  <!-- v2.2.78: 手动记一笔弹窗 -->
  <transition name="add-fade">
    <div v-if="visible" class="add-overlay" @click.self="onCancel">
      <transition name="add-pop" appear>
        <div v-if="visible" class="add-card">
          <div class="add-head">
            <div class="add-title">手动记一笔</div>
            <button class="add-close" @click="onCancel">✕</button>
          </div>

          <!-- 类型切换 -->
          <div class="type-toggle">
            <div :class="['type-pill', form.type === 'expense' ? 'active neg' : '']"
                 @click="form.type = 'expense'">支出</div>
            <div :class="['type-pill', form.type === 'income' ? 'active pos' : '']"
                 @click="form.type = 'income'">收入</div>
          </div>

          <!-- 金额 -->
          <div class="amount-input-wrap">
            <span class="amount-yen">¥</span>
            <input v-model="form.amount" type="number" inputmode="decimal" step="0.01"
                   class="amount-input" placeholder="0.00" autofocus>
          </div>

          <!-- 商户 -->
          <div class="add-field">
            <label class="add-label">商户</label>
            <input v-model="form.merchant" class="add-inp" placeholder="如:美团 / 工资" maxlength="32">
          </div>

          <!-- 分类 -->
          <div class="add-field">
            <label class="add-label">分类</label>
            <div class="cat-grid">
              <div v-for="c in store.categories[form.type]" :key="c.id"
                   :class="['cat-pick', form.category_id === c.id ? 'active' : '']"
                   :style="form.category_id === c.id ? {background: c.color, color: '#fff', borderColor: c.color} : {}"
                   @click="form.category_id = c.id">
                <span class="cat-emoji">{{ c.icon }}</span>
                <span class="cat-name">{{ c.name }}</span>
              </div>
            </div>
          </div>

          <!-- 备注 -->
          <div class="add-field">
            <label class="add-label">备注</label>
            <div class="note-chips">
              <span v-for="tag in SUGGESTED_TAGS" :key="tag"
                    :class="['note-chip', form.note === tag ? 'active' : '']"
                    @click="form.note = form.note === tag ? '' : tag">
                {{ tag }}
              </span>
            </div>
            <input v-model="form.note" class="add-inp" placeholder="添加备注(可选)" maxlength="60">
          </div>

          <!-- 操作 -->
          <div class="add-actions">
            <button class="add-btn cancel" @click="onCancel">取消</button>
            <button class="add-btn confirm" :disabled="saving || !canSave" @click="onSave">
              {{ saving ? '记账中…' : '记一笔' }}
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
import { insertTransaction } from '../db'
import { showToast } from '../utils/dialog'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'saved'])

const store = useBookStore()
const saving = ref(false)
const SUGGESTED_TAGS = ['#出差', '#报销', '#公司', '#家庭', '#还款', '#退款', '#聚餐', '#约会']

const form = reactive({
  type: 'expense',
  amount: '',
  merchant: '',
  category_id: 0,
  note: ''
})

// 切换类型时,重置分类为该类型第一个
watch(() => form.type, (t) => {
  ensureCategory(t)
})

function ensureCategory(t) {
  const cats = store.categories[t] || []
  const catId = Number(form.category_id)
  const exists = cats.some(c => Number(c.id) === catId)
  if (!cats.length) { form.category_id = 0; return }
  if (!exists) form.category_id = cats[0].id
}

// 第一次打开时,初始化分类
watch(() => props.visible, (v) => {
  if (v) ensureCategory(form.type)
})

// v2.2.80: 等待分类就绪后再初始化,避免打开弹窗时分类为空导致无法保存
watch(() => store.ready, (ready) => {
  if (ready && props.visible) ensureCategory(form.type)
})

const canSave = computed(() => {
  const n = Number(form.amount) || 0
  // v2.2.80: category_id 是字符串 id(如 "food"/"salary"),不能用 > 0 数字比较
  const cid = form.category_id
  const hasCat = cid && cid !== 0 && cid !== '0'
  return n > 0 && hasCat
})

function onCancel() { emit('close') }

async function onSave() {
  if (!canSave.value) {
    showToast('请输入金额并选择分类', 'error')
    return
  }
  saving.value = true
  try {
    const ok = await insertTransaction({
      type: form.type,
      amount: Number(form.amount),
      category_id: form.category_id,
      merchant: form.merchant,
      note: form.note,
      source: 'manual',
      auto: 0
    })
    if (ok) {
      showToast('已记一笔', 'success')
      // v2.2.79: 手动记账后派发 tx-added 事件,触发 Home 预算进度更新
      window.dispatchEvent(new CustomEvent('moneybook:tx-added', {
        detail: {
          preAmount: Number(form.amount),
          preType: form.type,
          preMerchant: form.merchant || '手动记账',
          source: 'manual'
        }
      }))
      // 重置
      form.amount = ''
      form.merchant = ''
      form.note = ''
      emit('saved')
      emit('close')
    } else {
      showToast('记账失败', 'error')
    }
  } catch (e) {
    showToast('记账失败: ' + e.message, 'error')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.add-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 9998;
  padding: 16px;
}
.add-card {
  background: var(--card);
  border-radius: 20px;
  width: 100%;
  max-width: 420px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 20px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.add-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px;
}
.add-title { font-size: 16px; font-weight: 700; color: var(--text-1); }
.add-close {
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--bg-2); border: none; color: var(--text-2);
  font-size: 14px; cursor: pointer;
}

.type-toggle {
  display: flex; background: var(--bg-2);
  border-radius: 12px; padding: 4px;
  margin-bottom: 14px;
}
.type-pill {
  flex: 1; padding: 8px;
  text-align: center; font-size: 14px; font-weight: 600;
  color: var(--text-2);
  border-radius: 8px; cursor: pointer;
  transition: all 0.2s;
}
.type-pill.active.neg { background: linear-gradient(135deg, #EF4444, #F87171); color: #fff; }
.type-pill.active.pos { background: linear-gradient(135deg, #10B981, #34D399); color: #fff; }

.amount-input-wrap {
  display: flex; align-items: baseline; justify-content: center;
  padding: 18px 0;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 16px;
}
.amount-yen {
  font-size: 24px; color: var(--text-2); margin-right: 4px; font-weight: 600;
}
.amount-input {
  font-size: 36px; font-weight: 800;
  border: none; outline: none; background: transparent;
  color: var(--text-1);
  width: 70%;
  text-align: center;
  font-variant-numeric: tabular-nums;
  letter-spacing: -1px;
}

.add-field { margin-bottom: 14px; }
.add-label {
  display: block; font-size: 12px; font-weight: 600;
  color: var(--text-2); margin-bottom: 6px;
}
.add-inp {
  width: 100%; padding: 10px 12px;
  font-size: 14px; background: var(--bg-2);
  border: 1.5px solid var(--border-light);
  border-radius: 10px; color: var(--text-1);
  outline: none; box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.add-inp:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.cat-grid {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 6px; max-height: 180px; overflow-y: auto;
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

.note-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.note-chip {
  padding: 5px 10px; font-size: 12px;
  background: var(--bg-2); color: var(--text-2);
  border-radius: 12px; cursor: pointer;
  border: 1.5px solid transparent;
}
.note-chip.active {
  background: var(--primary-bg);
  color: var(--primary);
  border-color: var(--primary);
  font-weight: 600;
}

.add-actions { display: flex; gap: 8px; margin-top: 12px; }
.add-btn {
  flex: 1; padding: 12px; font-size: 14px; font-weight: 600;
  border: none; border-radius: 12px; cursor: pointer;
  transition: transform 0.1s, opacity 0.2s;
}
.add-btn:active { transform: scale(0.97); }
.add-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.add-btn.cancel { background: var(--bg-2); color: var(--text-2); }
.add-btn.confirm {
  background: linear-gradient(135deg, #6366F1, #8B5CF6);
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.add-fade-enter-active, .add-fade-leave-active { transition: opacity 0.25s; }
.add-fade-enter-from, .add-fade-leave-to { opacity: 0; }
.add-pop-enter-active { animation: add-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1); }
.add-pop-leave-active { animation: add-out 0.2s ease; }
@keyframes add-in {
  0% { transform: scale(0.9) translateY(20px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}
@keyframes add-out {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.92); opacity: 0; }
}
:root[data-theme="dark"] .add-overlay { background: rgba(0, 0, 0, 0.7); }
</style>
