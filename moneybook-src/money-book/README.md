# 记账本 APP

一款本地运行、自动识别支付信息的手机记账 APP,完全离线、隐私安全。

## ✨ 核心功能

- 📲 **自动识别支付通知** — 监听支付宝、微信、银行 APP 的支付通知,自动记账
- 🏷️ **智能分类** — 自动按消费类别分类(餐饮/交通/购物/娱乐/...)
- 📅 **多维度查看** — 按日/周/月切换查看
- 💰 **收入支出双记** — 工资、奖金、退款、红包都支持
- 💾 **本地存储** — 数据存 SQLite,不上传云端,完全隐私
- 📊 **可视化统计** — 趋势图、饼图、TOP 5 排行
- 📤 **数据导出** — 一键导出 CSV

## 🏗️ 技术架构

- **前端**: Vue 3 + Pinia + Vue Router + ECharts
- **构建**: Vite 5
- **移动壳**: Capacitor 6 (Web → Android APK)
- **数据库**: Capacitor SQLite
- **自动识别**: Android NotificationListenerService + SMS Receiver

## 📦 项目结构

```
money-book/
├── src/                    # Vue 前端
│   ├── views/             # 页面: 首页/账单/统计/我的/编辑
│   ├── components/        # 公共组件
│   ├── stores/            # Pinia 状态
│   ├── db/                # 数据库封装
│   ├── parser/            # 支付信息解析器
│   ├── utils/             # 工具函数
│   └── router/            # 路由
├── android/                # Android 原生层
│   └── app/src/main/java/com/mavis/moneybook/
│       ├── MainActivity.java
│       ├── MoneyNotifier.java          # 权限管理插件
│       ├── MoneyBookPlugin.java        # 桥接
│       ├── PaymentNotificationListener.java  # 通知监听
│       └── SmsReceiver.java            # 短信监听
└── capacitor.config.json
```

## 🚀 本地运行

### 1. 准备环境

- Node.js 18+
- Android Studio (用来打包 APK)
- JDK 17

### 2. 安装依赖

```bash
cd money-book
npm install
```

### 3. 开发调试(Web 模式)

```bash
npm run dev
# 打开浏览器访问 http://localhost:5173
```

⚠️ **Web 模式下通知监听/短信监听不可用**(浏览器没权限),但能完整体验记账功能。

### 4. 打包 APK

```bash
# 1. 构建前端
npm run build

# 2. 同步到 Android
npx cap sync android

# 3. 用 Android Studio 打开 android 目录
npx cap open android

# 4. 在 Android Studio 中:
#    Build → Build Bundle(s) / APK(s) → Build APK(s)
#    生成的 APK 在 android/app/build/outputs/apk/debug/app-debug.apk
```

### 5. 安装到手机

把 `app-debug.apk` 拷到手机点击安装。**首次安装需要授予以下权限**:

1. **通知监听权限** (关键) — 打开 APP → "我的" → 前往开启通知权限 → 找到"记账本"打开
2. **短信权限** — 用于识别银行交易短信

## 🧪 解析器测试样例

解析器支持以下通知/短信格式:

```
✅ 支付宝付款成功 ¥38.50 给美团外卖
✅ 微信支付付款给星巴克 ¥45.00
✅ 您的尾号8888信用卡消费 156.80元
✅ 微信收到转账 ¥200.00 来自小明
✅ 银行短信:您尾号6666于10月1日消费888.00元
```

## 🔐 隐私说明

- 所有数据存本地 SQLite
- 不上传任何信息到服务器
- 通知/短信权限仅用于识别支付信息
- 开源,代码可审计

## 📋 已知限制

- Android 12+ 短信权限需要用户手动授权
- 通知监听需要用户在"通知使用权"设置中开启
- 部分银行 APP 通知不展示金额,无法识别

## 📝 License

MIT
