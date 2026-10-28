// SPDX-License-Identifier: GPL-3.0-only
// Inert fixtures verify production layout/geometry scripts without accounts or challenges.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../kotlin-tv/app/src/main/kotlin/com/hongguotv/nativeapp/OfficialLoginLayout.kt'),'utf8');
function extract(name) {
 const match=source.match(new RegExp('fun '+name+'\\([^)]*\\): String(?: \\{| =)\\s*(?:[\\s\\S]*?return )?"""([\\s\\S]*?)"""\\.trimIndent\\(\\)'));
 assert.ok(match,name);return match[1].replaceAll('$STYLE_ID','guoying-phone-login-layout').replaceAll('${width}','360').replaceAll('${height}','480');
}
const install=extract('installScript'),remove=extract('removeScript'),geometry=extract('geometryScript');
let style=null,viewportContent='',events=0;
const env={
 location:{protocol:'https:',hostname:'hongguoduanju.com'},
 Event:function(name){this.type=name;},
 window:{dispatchEvent(){events++;},getComputedStyle:node=>({display:node.hidden?'none':'block',visibility:'visible'}),scrollX:0,scrollY:0},
 document:{getElementById:()=>style,createElement:tag=>({tagName:tag}),head:{appendChild(node){style=node;node.parentNode={removeChild(){style=null;}};}},querySelector:()=>({setAttribute(name,value){assert.equal(name,'content');viewportContent=value;}})},
};
assert.equal(vm.runInNewContext(install,env),'installed');
assert.ok(style.textContent.includes('width:360px'));assert.ok(style.textContent.includes('height:480px'));
assert.ok(style.textContent.includes('.web-login-union__login__scan-code'));
assert.ok(style.textContent.includes('overflow-y:auto'));assert.ok(viewportContent.includes('width=1280, initial-scale=1'));
const installedCss=style.textContent;
const noteRule=installedCss.match(/\[class\*="pc-agreement-note-"\] \{([^}]+)\}/)[1];
const inputRule=installedCss.match(/\[class\*="pc-agreement-note__input-"\] \{([^}]+)\}/)[1];
const checkRule=installedCss.match(/\[class\*="pc-agreement-note__check-"\] \{([^}]+)\}/)[1];
assert.ok(noteRule.includes('position:relative!important'));
assert.ok(noteRule.includes('left:auto!important') && noteRule.includes('top:auto!important'));
assert.ok(inputRule.includes('width:44px!important') && inputRule.includes('height:44px!important'));
assert.ok(inputRule.includes('left:0!important') && inputRule.includes('top:calc(50% - 22px)'));
assert.ok(checkRule.includes('left:14px!important') && checkRule.includes('top:calc(50% - 8px)'));
assert.ok(checkRule.includes('pointer-events:none!important'));
assert.ok(!install.includes('.checked') && !install.includes('.dispatchEvent(new MouseEvent'));
assert.equal(events,1);assert.ok(!install.includes('.click('));assert.ok(!install.includes('document.cookie'));
assert.ok(!style.textContent.includes('captcha')); // challenge pixels and slider styles stay untouched
vm.runInNewContext(remove,env);assert.equal(style,null);
function node(tag,x,y,width,height,hidden=false) {
 const n={tagName:tag,hidden,getBoundingClientRect:()=>({left:x,top:y,width,height}),querySelectorAll:()=>n.frames||[]};
 for(const key of ['value','contentDocument','contentWindow']) Object.defineProperty(n,key,{get(){throw Error('Must not read form values or cross-origin content');}});
 return n;
}
let candidates=[],login=node('DIV',0,0,360,440);
env.document.querySelector=()=>login;env.document.querySelectorAll=()=>candidates;
function measure(){return JSON.parse(JSON.stringify(vm.runInNewContext(geometry,env)));}
assert.equal(measure().kind,'login');
candidates=[node('DIV',0,0,1280,1000),node('IMG',640,150,320,300),node('DIV',500,100,340,440),node('DIV',0,0,500,500,true)];
let r=measure();assert.deepEqual(r,{x:500,y:100,width:340,height:440,kind:'challenge'});
candidates=[node('IFRAME',450,80,320,400)];assert.equal(measure().kind,'challenge');
// 官方真实结构：有标识的全屏遮罩里放一个无 id/class 的跨域 iframe。
const mask=node('DIV',0,0,1280,600), anonymous=node('IFRAME',470,100,340,400);
mask.frames=[anonymous];candidates=[mask];
assert.deepEqual(measure(),{x:470,y:100,width:340,height:400,kind:'challenge'});
anonymous.hidden=true;assert.equal(measure().kind,'challenge-loading');
anonymous.hidden=false;assert.equal(measure().kind,'challenge');
candidates=[];login=null;assert.equal(measure(),null);
env.location.hostname='evil.test';assert.equal(measure(),null);assert.equal(vm.runInNewContext(install,env),'blocked');
console.log('Phone login layout fixtures passed: agreement hit-area anchoring,  reversible scoped CSS, readable single column, viewport, challenge geometry, hidden/image exclusion and cross-origin guards.');
