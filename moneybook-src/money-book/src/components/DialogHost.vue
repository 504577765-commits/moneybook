<template>
  <!-- v2.2.73: 全局弹窗宿主 -->
  <transition name="dialog-fade">
    <div v-if="s.visible" class="dialog-overlay" @click.self="onCancel">
      <transition name="dialog-pop" appear>
        <div v-if="s.visible" class="dialog-card">
          <!-- icon -->
          <div :class="['dialog-icon', s.type === 'confirm' ? 'warn' : s.type === 'prompt' ? 'info' : 'info']">
            <span v-if="s.type === 'alert'">ℹ️</span>
            <span v-else-if="s.type === 'confirm'">❓</span>
            <span v-else>✏️</span>
          </div>
          <div v-if="s.title" class="dialog-title">{{ s.title }}</div>
          <div class="dialog-message">{{ s.message }}</div>
          <input v-if="s.type === 'prompt'"
                 ref="inputEl"
                 v-model="s.inputValue"
                 :type="s.inputType || 'text'"
                 :placeholder="s.inputPlaceholder"
                 class="dialog-input"
                 @keyup.enter="onConfirm">
          <div class="dialog-actions">
            <button v-if="s.type !== 'alert'" class="dialog-btn cancel" @click="onCancel">{{ s.cancelText || '取消' }}</button>
            <button :class="['dialog-btn', 'confirm', s.danger ? 'danger' : '']" @click="onConfirm">{{ s.confirmText || '确定' }}</button>
          </div>
        </div>
      </transition>
    </div>
  </transition>

  <!-- toast -->
  <transition name="toast-pop">
    <div v-if="s.toastVisible" :class="['toast', s.toastType]">
      <span class="toast-icon">
        <span v-if="s.toastType === 'success'">✅</span>
        <span v-else-if="s.toastType === 'error'">❌</span>
        <span v-else>ℹ️</span>
      </span>
      <span class="toast-text">{{ s.toastMessage }}</span>
    </div>
  </transition>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { dialogState as s, closeDialog } from '../utils/dialog'

const inputEl = ref(null)

watch(() => s.visible, (v) => {
  if (v && s.value.type === 'prompt') {
    nextTick(() => inputEl.value?.focus())
  }
})

function onConfirm() {
  // v2.2.75: prompt 类型返回 inputValue,其他返回 true
  closeDialog(s.value.type === 'prompt' ? s.value.inputValue : true)
}
function onCancel() { closeDialog(s.value.type === 'prompt' ? null : false) }
</script>

<style scoped>
.dialog-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999;
  padding: 20px;
}
.dialog-card {
  background: var(--card);
  border-radius: 20px;
  width: 100%;
  max-width: 320px;
  padding: 22px 20px 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
  text-align: center;
}
.dialog-icon { font-size: 40px; margin-bottom: 8px; line-height: 1; }
.dialog-icon.warn { /* confirm */ }
.dialog-icon.info { /* alert / prompt */ }
.dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 6px;
}
.dialog-message {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.5;
  margin-bottom: 14px;
  word-break: break-word;
}
.dialog-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  background: var(--bg-2);
  border: 1.5px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-1);
  margin-bottom: 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}
.dialog-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.dialog-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.dialog-btn {
  flex: 1;
  padding: 11px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.1s, opacity 0.2s, background 0.2s;
}
.dialog-btn:active { transform: scale(0.97); }
.dialog-btn.cancel {
  background: var(--bg-2);
  color: var(--text-2);
}
.dialog-btn.confirm {
  background: linear-gradient(135deg, #6366F1, #8B5CF6);
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.dialog-btn.confirm.danger {
  background: linear-gradient(135deg, #EF4444, #F87171);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

/* 弹窗动画 */
.dialog-fade-enter-active, .dialog-fade-leave-active {
  transition: opacity 0.25s ease;
}
.dialog-fade-enter-from, .dialog-fade-leave-to { opacity: 0; }

.dialog-pop-enter-active {
  animation: pop-in 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.dialog-pop-leave-active {
  animation: pop-out 0.2s ease;
}
@keyframes pop-in {
  0% { transform: scale(0.85) translateY(10px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}
@keyframes pop-out {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.92); opacity: 0; }
}

/* toast */
.toast {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--card);
  color: var(--text-1);
  padding: 10px 18px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  display: flex; align-items: center; gap: 8px;
  z-index: 99999;
  border: 1px solid var(--border-light);
}
.toast.success { background: linear-gradient(135deg, #10B981, #34D399); color: #fff; border: none; }
.toast.error { background: linear-gradient(135deg, #EF4444, #F87171); color: #fff; border: none; }
.toast-icon { font-size: 18px; }
.toast-text { white-space: nowrap; }

.toast-pop-enter-active {
  animation: toast-in 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.toast-pop-leave-active {
  animation: toast-out 0.3s ease;
}
@keyframes toast-in {
  0% { transform: translateX(-50%) translateY(-30px); opacity: 0; }
  100% { transform: translateX(-50%) translateY(0); opacity: 1; }
}
@keyframes toast-out {
  0% { transform: translateX(-50%) translateY(0); opacity: 1; }
  100% { transform: translateX(-50%) translateY(-30px); opacity: 0; }
}

/* 深色模式 */
:root[data-theme="dark"] .dialog-overlay { background: rgba(0, 0, 0, 0.7); }
:root[data-theme="dark"] .dialog-card { box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6); }
:root[data-theme="dark"] .dialog-input { background: var(--bg-1); }
</style>
