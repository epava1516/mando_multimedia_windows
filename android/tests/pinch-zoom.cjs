'use strict';
// Optional browser regression test. PLAYWRIGHT_MODULE and MANDO_CHROMIUM can select local installs.
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assets = path.resolve(__dirname, '../assets');
const server = http.createServer((request, response) => {
  const names = {'/':'index.html','/app.js':'app.js','/style.css':'style.css'};
  const file = names[request.url];
  if (!file) {response.writeHead(404); response.end(); return;}
  response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html');
  response.end(fs.readFileSync(path.join(assets, file)));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({headless:true, ...(process.env.MANDO_CHROMIUM ? {executablePath:process.env.MANDO_CHROMIUM} : {})});
    const page = await browser.newPage({viewport:{width:390,height:800},hasTouch:true});
    const errors=[]; page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      window.actions=[];window.pairRequests=[];window.irActions=[];window.qrRequests=0;window.scanHost="192.168.1.10";
      window.MandoNative={request(id, raw) {
        const {path, data}=JSON.parse(raw);
        if (path==='/api/action') window.actions.push(data);
        if (path==='/api/ir/send') window.irActions.push(data);
        if (path==='/api/pair') window.pairRequests.push(data);
        if(path==='/api/qr-scan')window.qrRequests++;
        const result = path==='/api/scan' ? {servers:[{host:window.scanHost,mode:'own',version:'Mando',port:1979}]} : path==='/api/pair' ? {mode:data.mode,version:'Mando v1 TLS',directLock:true,zoomHelperInstalled:true} : path==='/api/status' ? {paired:true} : path==='/api/settings' ?
          {sensitivity:.65,irTabVisible:true,zoomHelperInstalled:true} : path==='/api/ir/status' ? {available:true} : {ok:true};
        setTimeout(()=>window.nativeReply(id,result),0);
      }};
    });
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.locator('#touchpad').waitFor({state:'visible'});
    const bounds = await page.locator('#touchpad').boundingBox();
    const cx=bounds.x+bounds.width/2, cy=bounds.y+bounds.height/2;
    const cdp=await page.context().newCDPSession(page);
    const point=(id,x,y)=>({id,x,y,radiusX:3,radiusY:3,force:1});
    async function send(type, points) {
      await cdp.send('Input.dispatchTouchEvent',{type,touchPoints:points});
      await page.waitForTimeout(35);
    }
    async function reset() {await page.evaluate(()=>window.actions=[]);}
    async function actions() {await page.waitForTimeout(80);return page.evaluate(()=>window.actions);}
    async function start(span=100) {await reset();await send('touchStart',[point(1,cx-span/2,cy),point(2,cx+span/2,cy)]);}
    const finish=()=>send('touchEnd',[]);
    // Hold repeats without a queued tail after release; a tap still sends one step.
    const volume=page.locator('[data-key=VOLUME_UP]');
    const volumeBounds=await volume.boundingBox();
    await page.mouse.move(volumeBounds.x+volumeBounds.width/2,volumeBounds.y+volumeBounds.height/2);
    await reset();await page.mouse.down();await page.waitForTimeout(680);
    await page.mouse.up();
    let volumeActions=await actions();
    assert(volumeActions.length>=3&&volumeActions.every(a=>a.key==='VOLUME_UP'),'held PC volume must repeat');
    const releasedCount=volumeActions.length;
    await page.waitForTimeout(400);
    assert.equal((await actions()).length,releasedCount,'volume must stop on release');
    await reset();await volume.click();
    assert.equal((await actions()).length,1,'volume tap must send exactly one step');
    await reset();await page.mouse.down();await page.waitForTimeout(400);
    await page.evaluate(()=>window.mandoSuspended());
    await page.mouse.up();
    const pausedCount=(await actions()).length;await page.waitForTimeout(350);
    assert.equal((await actions()).length,pausedCount,'pause must stop a held volume');
    await page.evaluate(()=>window.mandoSessionResult({paired:true,mode:'official'}));
    await page.locator('[data-tab=ir]').click();
    const irVolume=page.locator('[data-ir=VOLUME_DOWN]');
    const irBounds=await irVolume.boundingBox();
    await page.mouse.move(irBounds.x+irBounds.width/2,irBounds.y+irBounds.height/2);
    await page.mouse.down();await page.waitForTimeout(680);await page.mouse.up();
    const irCount=await page.evaluate(()=>window.irActions.length);
    assert(irCount>=3,'held IR volume must repeat');await page.waitForTimeout(350);
    assert.equal(await page.evaluate(()=>window.irActions.length),irCount,'IR volume must stop on release');
    await page.locator('[data-tab=touch]').click();
    await start();
    await send('touchMove',[point(1,cx-80,cy),point(2,cx+80,cy)]);
    await finish();
    let sent=await actions();
    assert(sent.some(action=>action.type==='zoom'&&action.steps>0),'spread must enlarge');
    assert(sent.every(action=>action.type==='zoom'),'spread must not also scroll, click or move');
    await start(160);
    await send('touchMove',[point(1,cx-45,cy),point(2,cx+45,cy)]);
    await finish();
    sent=await actions();
    assert(sent.some(action=>action.type==='zoom'&&action.steps<0),'pinch must reduce');
    assert(sent.every(action=>action.type==='zoom'),'pinch must not also scroll, click or move');
    await start();
    await send('touchMove',[point(1,cx-50,cy+50),point(2,cx+50,cy+50)]);
    await finish();
    sent=await actions();
    assert(sent.some(action=>action.type==='scroll'),'parallel fingers must scroll');
    assert(sent.every(action=>action.type==='scroll'),'scroll must not also zoom');
    // Vertically aligned fingers deliver separate pointer updates; this is scroll, not pinch.
    await reset();
    await send('touchStart',[point(1,cx,cy-40),point(2,cx,cy+40)]);
    await send('touchMove',[point(1,cx,cy),point(2,cx,cy+40)]);
    assert.deepEqual(await actions(),[],'wait for the second finger before classifying');
    await send('touchMove',[point(1,cx,cy),point(2,cx,cy+80)]);
    await finish();
    assert((await actions()).every(action=>action.type==='scroll'),'staggered parallel updates must not pinch');
    assert((await actions()).some(action=>action.type==='scroll'),'staggered parallel updates must scroll');
    // Dedicated edge starts choose the wheel axis for the entire drag.
    await reset();
    const edgeX=bounds.x+bounds.width-22, edgeY=bounds.y+bounds.height-22;
    for (const horizontal of [false,true]) {
      await reset();
      const x=horizontal?cx:edgeX, y=horizontal?edgeY:cy;
      await send('touchStart',[point(1,x,y)]);
      await send('touchMove',[point(1,x+(horizontal?4:0),y+(horizontal?0:4))]);
      await finish();
      const fine=await actions();
      assert.deepEqual(fine,[{type:horizontal?'scroll_horizontal':'scroll',steps:1}], '4px edge motion must send one fine sample');
    }
    await reset();
    await send('touchStart',[point(1,edgeX,cy)]);
    await send('touchMove',[point(1,edgeX,cy+60)]);
    await finish();
    sent=await actions();
    assert(sent.length>0&&sent.every(action=>action.type==='scroll'&&action.steps>0),'right edge must wheel down without moving or clicking');
    await reset();
    await send('touchStart',[point(1,cx,edgeY)]);
    await send('touchMove',[point(1,cx+60,edgeY)]);
    await finish();
    sent=await actions();
    assert(sent.length>0&&sent.every(action=>action.type==='scroll_horizontal'&&action.steps>0),'bottom edge must wheel right');
    await reset();
    await send('touchStart',[point(1,cx,edgeY)]);
    await send('touchMove',[point(1,cx-60,edgeY)]);
    await finish();
    sent=await actions();
    assert(sent.length>0&&sent.every(action=>action.type==='scroll_horizontal'&&action.steps<0),'bottom edge must wheel left');
    await reset();
    await send('touchStart',[point(1,edgeX,cy)]);
    await finish();
    assert.deepEqual(await actions(),[],'tapping an edge must not click the PC');
    await start();
    await send('touchMove',[point(1,cx-52,cy+2),point(2,cx+52,cy+2)]);
    await finish();
    assert.deepEqual(await actions(),[],'small two-finger jitter must not send controls');
    await start();
    await send('touchCancel',[]);
    assert.deepEqual(await actions(),[],'cancellation must not click');
    await reset();
    await send('touchStart',[point(1,cx,cy)]);
    await send('touchMove',[point(1,cx+30,cy+20)]);
    await finish();
    sent=await actions();
    assert(sent.some(action=>action.type==='move'),'one finger must still move cursor');
    assert(!sent.some(action=>action.type==='zoom'||action.type==='scroll'),'one finger must not zoom or scroll');
    await reset();
    await send('touchStart',[point(1,cx,cy)]);
    await finish();
    assert.equal((await actions()).filter(action=>action.type==='click').length,1,'tap must click once');
    await reset();
    await send('touchStart',[point(1,cx-60,cy),point(2,cx,cy),point(3,cx+60,cy)]);
    await send('touchMove',[point(1,cx-60,cy-70),point(2,cx,cy-70),point(3,cx+60,cy-70)]);
    await send('touchMove',[point(1,cx-60,cy-90),point(2,cx,cy-90),point(3,cx+60,cy-90)]);
    await finish();
    assert.deepEqual(await actions(),[{type:'task_view'}],'three fingers up must open Task View exactly once');
    await reset();
    await send('touchStart',[point(1,cx-60,cy),point(2,cx,cy),point(3,cx+60,cy)]);
    await send('touchMove',[point(1,cx-60,cy+70),point(2,cx,cy+70),point(3,cx+60,cy+70)]);
    await finish();
    assert.deepEqual(await actions(),[],'three fingers down must not open Task View or scroll');
    await page.evaluate(()=>window.mandoZoomChanged(false));
    await start();
    await send('touchMove',[point(1,cx-80,cy),point(2,cx+80,cy)]);
    await finish();
    assert.deepEqual(await actions(),[],'unconfigured zoom must not send keyboard zoom fallback');
    await page.locator('[data-tab=settings]').click();
    await page.locator('#disconnect').click();
    await page.locator('[data-tab=touch]').click();
    await page.locator('#scan').click();
    await page.locator('#servers button').first().click();
    assert.equal(await page.locator('#server-mode').inputValue(),'own','discovered Mando must select own mode');
    assert.equal(await page.locator('#own-code').inputValue(),'','discovery must not provide pairing secrets');
    assert(await page.locator('#scan').isVisible(),'own mode must support local discovery without skipping pairing');
    assert(await page.locator('#pin').isDisabled(),'hidden official host must not block own pairing');
    const code='mando1:192.168.1.10:1979:'+('a'.repeat(64))+':12345678';
    await page.locator('#scan-qr').click();
    await page.waitForTimeout(60);
    assert.equal(await page.evaluate(()=>window.qrRequests),1);
    await page.evaluate(value=>window.mandoQrScanned(value),code);
    assert.equal(await page.locator('#own-code').inputValue(),code,'QR must fill complete identity and endpoint');
    assert.equal(await page.evaluate(()=>window.pairRequests.length),0,'reading QR must wait for explicit Connect');
    await page.locator('#pair-button').click();
    await page.locator('#touchpad').waitFor({state:'visible'});
    assert.equal(await page.locator('#own-code').inputValue(),'','pairing secret must clear after connecting');
    const pair=await page.evaluate(()=>window.pairRequests.at(-1));
    assert.equal(pair.mode,'own');assert.equal(pair.code,code);
    // A remembered PC can move IP without requiring a new QR or new device registration.
    await page.locator('[data-tab=settings]').click();await page.locator('#disconnect').click();
    await page.locator('[data-tab=touch]').click();await page.evaluate(()=>window.scanHost='192.168.1.22');
    await page.locator('#scan').click();await page.locator('#servers button').first().click();
    assert.equal(await page.locator('#own-code').inputValue(),'');
    assert.equal(await page.locator('#own-code').getAttribute('required'),null);
    await page.locator('#pair-button').click();await page.locator('#touchpad').waitFor({state:'visible'});
    const moved=await page.evaluate(()=>window.pairRequests.at(-1));
    assert.equal(moved.ownHost,'192.168.1.22');assert.equal(moved.ownPort,1979);assert.equal(moved.code,'');

    // Returning must retain Control until native reconnect succeeds or fails.
    await page.locator('[data-tab=touch]').click();
    await page.evaluate(()=>{window.mandoSuspended();window.mandoResuming();});
    assert(await page.locator('#pairing').isHidden(),'must not flash the connection form while recovering');
    await page.locator('[data-key=PLAY_PAUSE]').dispatchEvent('click');
    await page.waitForTimeout(60);
    await page.evaluate(()=>window.mandoSessionResult({paired:true,mode:'own',recovered:true}));
    assert(await page.locator('#pairing').isHidden(),'successful recovery stays in controls');
    assert.equal(await page.locator('#connection').textContent(),'PC vinculado');
    await page.evaluate(()=>{window.mandoSuspended();window.mandoResuming();window.mandoSessionResult({paired:false,failed:true,error:'PC apagado'});});
    assert(await page.locator('#pairing').isVisible(),'show connection form after actual failure');
    await page.evaluate(()=>window.mandoSessionResult({paired:true,mode:'official'}));
    // Leave one native action pending and a second queued, then suspend and recover.
    await page.evaluate(()=>{
      const original=window.MandoNative.request;
      window.heldActions=[];
      window.MandoNative.request=(id,raw)=>{const q=JSON.parse(raw);if(q.path==='/api/action'){window.heldActions.push(id);return;}original(id,raw);};
      document.querySelector('[data-key=NEXT]').click();
      document.querySelector('[data-key=PREVIOUS]').click();
    });
    await page.waitForFunction(()=>window.heldActions.length===1);
    await page.evaluate(()=>{
      window.mandoSuspended();window.mandoResuming();window.mandoSessionResult({paired:true,mode:'official',recovered:true});
      window.nativeReply(window.heldActions[0],{error:'Old socket closed'});
    });
    await page.waitForTimeout(100);
    assert.equal(await page.evaluate(()=>window.heldActions.length),1,'queued actions must not replay on recovered PC');
    assert(await page.locator('#pairing').isHidden(),'stale socket error must not undo successful recovery');
    assert.deepEqual(errors,[]);
    console.log('Pinch zoom, scrolling, jitter, cancellation, cursor movement, tap, Task View, zoom setup guard own-server pairing UI resume UI and wheel edges: passed');
  } finally {
    if(browser)await browser.close();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
