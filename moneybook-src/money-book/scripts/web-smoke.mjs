/**
 * Web 冒烟测试 — 会员系统 UI + 兑换流程 (临时脚本,测后可删)
 * 依赖: d:\jizhangapp\server 后端运行在 8700, vite preview 在 4173
 * 运行: node scripts/web-smoke.mjs
 */
import { chromium } from 'playwright'
import fs from 'node:fs'

const BASE = 'http://localhost:4173'
const results = []
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? '✅' : '❌'} ${name} ${extra}`) }

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 390, height: 844 } })

// ---------- 1. 我的页: 非会员状态 ----------
await page.goto(`${BASE}/#/me`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const meText = await page.locator('body').innerText()
ok('我的页显示"免费版"会员卡', /免费版/.test(meText))
ok('状态卡显示"自动记账未开通"', /自动记账未开通/.test(meText))
ok('显示"解锁自动记账"按钮', /解锁自动记账/.test(meText))
ok('监听源带"会员专属"标记', /监听源/.test(meText) && /会员专属/.test(meText))

// 点击监听源 → 应弹出会员弹窗(非会员拦截)
await page.getByText('监听源').first().click()
await page.waitForTimeout(500)
const dlg = await page.locator('body').innerText()
ok('点击监听源弹出会员引导弹窗', /会员功能/.test(dlg) && /去开通/.test(dlg))
await page.getByText('再想想').click().catch(() => {})

// ---------- 2. 会员页: 权益/套餐/激活码 UI ----------
await page.goto(`${BASE}/#/vip`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const vipText = await page.locator('body').innerText()
ok('会员页标题",会员中心"', /会员中心/.test(vipText))
ok('自动记账标记为"会员"权益', /自动记账/.test(vipText))
ok('显示连续包月 ¥6', /连续包月/.test(vipText) && /6/.test(vipText))
ok('显示连续包年 ¥48', /连续包年/.test(vipText) && /48/.test(vipText))
ok('激活码输入框存在', await page.locator('input.code-input').count() > 0)
await page.screenshot({ path: 'D:/jizhangapp/smoke_web_vip_before.png' })

// ---------- 3. 兑换激活码端到端 ----------
const data = JSON.parse(fs.readFileSync('D:/jizhangapp/server/data.json', 'utf8'))
const unused = data.codes.find(c => !c.usedBy)
const code = unused ? unused.code : null
if (!code) {
  console.log('⚠️ 无可用激活码,跳过兑换验证')
} else {
  await page.fill('input.code-input', code)
  await page.getByRole('button', { name: '兑换' }).click()
  await page.waitForTimeout(1500)
  const afterRedeem = await page.locator('body').innerText()
  ok('兑换成功后会员状态更新(会员已生效)', /会员已生效/.test(afterRedeem))
  ok('显示到期日', /到期/.test(afterRedeem))
  await page.screenshot({ path: 'D:/jizhangapp/smoke_web_vip_after.png' })
}

// ---------- 4. 回到我的页验证会员态 ----------
await page.goto(`${BASE}/#/me`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const meAfter = await page.locator('body').innerText()
ok('我的页会员卡变为"👑 会员"', /会员/.test(meAfter) && /👑/.test(meAfter))
ok('会员开通后无"会员专属"锁定标记', !/会员专属/.test(meAfter))
await page.screenshot({ path: 'D:/jizhangapp/smoke_web_me_after.png' })

await browser.close()

const failed = results.filter(r => !r.pass)
console.log(`\n===== 结果: ${results.length - failed.length}/${results.length} 通过 =====`)
process.exit(failed.length ? 1 : 0)