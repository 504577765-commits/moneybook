// 单一真源: 从 package.json 读 version, 同步到 gradle.properties 和 src/config/version.js
// 运行: node scripts/sync-version.cjs (构建时自动触发, 也可手动)
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf-8'))
const [major, minor, patch] = pkg.version.split('.').map(Number)

if (typeof major !== 'number' || isNaN(major)) {
  console.error('version 格式错误:', pkg.version)
  process.exit(1)
}

// Android versionCode: 永远递增, 保证可覆盖安装
// 规则: 从 gradle.properties 读取当前值 + 1
const propsPath = path.join(root, 'android', 'gradle.properties')
let props = fs.readFileSync(propsPath, 'utf-8')
const m = props.match(/versionCode=(\d+)/)
const prevCode = m ? parseInt(m[1], 10) : 0
const newCode = prevCode + 1

props = props.replace(/versionCode=\d+/, `versionCode=${newCode}`)
props = props.replace(/versionName=[\d.]+/, `versionName=${pkg.version}`)
fs.writeFileSync(propsPath, props, 'utf-8')
console.log(`[sync] gradle.properties -> versionCode=${newCode}, versionName=${pkg.version}`)

// Vue 组件版本常量
const vuePath = path.join(root, 'src', 'config', 'version.js')
fs.writeFileSync(vuePath, `// 自动生成, 不要手动改! 改 package.json 的 version 即可
export const APP_VERSION = '${pkg.version}'
export const ANDROID_VERSION_CODE = ${newCode}
`, 'utf-8')
console.log(`[sync] src/config/version.js -> APP_VERSION='${pkg.version}'`)

console.log('[sync] ✅ 版本同步完成')
