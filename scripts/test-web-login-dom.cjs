// SPDX-License-Identifier: GPL-3.0-only
// Run the production auto-open script against inert DOM fixtures; never contact a service.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../kotlin-tv/app/src/main/kotlin/com/hongguotv/nativeapp/OfficialWebLogin.kt'), 'utf8');
const match = source.match(/internal fun openLoginScript\(\): String = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, 'Production auto-open script must exist');
const script = match[1];
function element(text, options = {}) {
  const node = {
    textContent: text, disabled: Boolean(options.disabled), clicks: 0,
    getBoundingClientRect: () => ({width: options.hidden ? 0 : 80, height: 48}),
    closest: selector => selector === 'form' && options.form ? {} : null,
    click() { this.clicks++; },
  };
  Object.defineProperty(node, 'value', {get() { throw Error('Must not read form values'); }});
  return node;
}
function run(entries = [], dialogs = [], hostname = 'hongguoduanju.com', protocol = 'https:', others = []) {
  return vm.runInNewContext(script, {
    location: {hostname, protocol},
    document: {querySelectorAll(selector) {
      if (selector === 'button[class*="pc-entry-"]') return entries;
      if (selector === '[class*="pc-login-dialog-"]') return dialogs;
      if (selector === '[role="dialog"]') return others;
      throw Error('Unexpected DOM access');
    }},
    window: {getComputedStyle: () => ({display: 'block', visibility: 'visible'}),dispatchEvent() {}},
    Event:function(type){this.type=type;},
  });
}
const login = element('登录');
assert.equal(run([login]), 'requested'); assert.equal(login.clicks, 1);
assert.equal(run([]), 'waiting'); // lazy account/header rendering
const hidden = element('登录', {hidden: true});
const disabled = element('登录', {disabled: true});
const form = element('登录', {form: true});
const register = element('注册');
const submit = element('登录/注册');
assert.equal(run([hidden, disabled, form, register, submit]), 'waiting');
for (const node of [hidden, disabled, form, register, submit]) assert.equal(node.clicks, 0);
const existing = element('登录');
assert.equal(run([existing], [element('官方弹窗')]), 'opened');
assert.equal(existing.clicks, 0); // never click underneath an existing dialog/challenge
for (const [host, protocol] of [['evil.test', 'https:'], ['hongguoduanju.com.evil.test', 'https:'], ['hongguoduanju.com', 'http:']]) {
  assert.equal(run([existing], [], host, protocol), 'blocked');
  assert.equal(existing.clicks, 0);
}
assert.equal(run([existing], [], 'www.hongguoduanju.com'), 'requested');
assert.equal(existing.clicks, 1);
console.log('Official login DOM fixtures passed: entry, delayed render, hidden/disabled/form/register/submit exclusion, existing modal and origin guards.');

assert.equal(run([existing], [], 'hongguoduanju.com', 'https:', [element('其他模态窗口')]), 'waiting');
// SSR 入口先出现，但事件尚未水合：请求打开不等于已打开，必须重试并确认实际弹窗。
let now=2000, modal=[], requested=0;
const delayed=element('登录');
const env={location:{hostname:'hongguoduanju.com',protocol:'https:'},Event:function(type){this.type=type;},Date:{now:()=>now},
 document:{querySelectorAll:selector=>selector==='button[class*="pc-entry-"]'?[delayed]:selector==='[class*="pc-login-dialog-"]'?modal:[]},
 window:{getComputedStyle:()=>({display:'block',visibility:'visible'}),dispatchEvent(event){assert.equal(event.type,'hongguo:pc-open-login');requested++;}}};
assert.equal(vm.runInNewContext(script,env),'requested');
now=2100;assert.equal(vm.runInNewContext(script,env),'waiting');assert.equal(delayed.clicks,1);
now=3500;assert.equal(vm.runInNewContext(script,env),'requested');assert.equal(delayed.clicks,2);
modal=[element('真实登录窗口')];assert.equal(vm.runInNewContext(script,env),'opened');assert.equal(requested,2);
console.log('Confirmed-dialog and delayed-hydration retries passed.');
