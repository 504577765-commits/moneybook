// v2.2.73: 美化弹窗系统(动画 + 现代风)
// 替代浏览器原生 alert/confirm/prompt
// 用法:
//   import { showAlert, showConfirm, showPrompt, showToast } from '../utils/dialog'
//   showAlert('操作成功')
//   const ok = await showConfirm('确定删除?')
//   const val = await showPrompt('请输入', '默认值')

import { ref } from 'vue'

const state = ref({
  visible: false,
  type: 'alert', // alert / confirm / prompt
  title: '',
  message: '',
  inputValue: '',
  inputPlaceholder: '',
  confirmText: '确定',
  cancelText: '取消',
  resolve: null,
  // toast
  toastVisible: false,
  toastMessage: '',
  toastType: 'info' // info / success / error
})

let toastTimer = null

function open(config) {
  return new Promise((resolve) => {
    state.value = {
      ...state.value,
      ...config,
      visible: true,
      resolve
    }
  })
}

function close(result) {
  const resolve = state.value.resolve
  state.value.visible = false
  if (resolve) resolve(result)
  setTimeout(() => { state.value.resolve = null }, 300)
}

export function showAlert(message, title = '提示') {
  return open({
    type: 'alert',
    title,
    message,
    confirmText: '知道了'
  })
}

export function showConfirm(message, title = '确认', options = {}) {
  return open({
    type: 'confirm',
    title,
    message,
    confirmText: options.confirmText || '确定',
    cancelText: options.cancelText || '取消',
    danger: options.danger || false
  })
}

export function showPrompt(message, defaultValue = '', title = '输入', options = {}) {
  return open({
    type: 'prompt',
    title,
    message,
    inputValue: defaultValue,
    inputPlaceholder: options.placeholder || '',
    inputType: options.type || 'text',
    confirmText: options.confirmText || '确定',
    cancelText: options.cancelText || '取消'
  })
}

export function showToast(message, type = 'info', duration = 2000) {
  if (toastTimer) clearTimeout(toastTimer)
  state.value.toastVisible = false
  // force restart animation
  setTimeout(() => {
    state.value.toastMessage = message
    state.value.toastType = type
    state.value.toastVisible = true
    toastTimer = setTimeout(() => {
      state.value.toastVisible = false
    }, duration)
  }, 50)
}

export function closeDialog(result) { close(result) }

export const dialogState = state
