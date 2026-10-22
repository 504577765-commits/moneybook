// 系统返回键处理栈
// 弹窗/面板打开时注册,返回键优先关闭最上层,避免直接退出应用
const handlers = []

export function pushBackHandler(fn) {
  handlers.push(fn)
  return () => {
    const i = handlers.indexOf(fn)
    if (i >= 0) handlers.splice(i, 1)
  }
}

// 返回 true 表示已消费本次返回事件
export function runBackHandler() {
  for (let i = handlers.length - 1; i >= 0; i--) {
    try {
      if (handlers[i]() === true) return true
    } catch (e) { /* 单个处理器异常不影响后续 */ }
  }
  return false
}
