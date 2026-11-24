const assert=require('node:assert/strict'), vm=require('node:vm'), fs=require('node:fs');
const script=fs.readFileSync(__dirname+'/index.html','utf8').match(/<script>([\s\S]+)<\/script>/)[1];
class Stub {
  constructor(){this.handlers={};this.children=[];this.attrs={};this.className='';this.value='search';}
  addEventListener(type, fn){this.handlers[type]=fn}
  append(e){this.children.push(e)}
  replaceChildren(...e){this.children=e}
  setAttribute(k,v){this.attrs[k]=v}
  focus(){active=this;this.handlers.focus?.()}
}
let active;
const dom={};
let timerId=0,pending=new Map(),cancelled=[];
const document={getElementById:id=>dom[id]??(dom[id]=new Stub()),createElement:()=>new Stub()};
let seed=77;
const math=Object.create(Math);
math.random=()=> (seed = (Math.imul(seed,1664525)+1013904223) >>> 0)/2**32;
vm.runInNewContext(script,{document,Math:math,performance,Int8Array,Set,Array,Error,setTimeout:fn=>{pending.set(++timerId,fn);return timerId},clearTimeout:id=>{if(pending.has(id)){cancelled.push(pending.get(id));pending.delete(id)}}});
const cells=dom.board.children,f=(k)=>cells.filter(c=>c.className.split(' ').includes(k));
const play=()=>f('legal')[0].handlers.click();
const tick=()=>{const q=[...pending.values()];pending.clear();q.forEach(fn=>fn())};
const reset=()=>dom.restart.handlers.click();
assert.equal(cells.length,49);
assert.equal(f('human').length,1);
assert.equal(f('computer').length,1);
assert.equal(cells.filter(c=>c.tabIndex===0).length,1);
f('computer')[0].handlers.click();
assert.equal(f('burned').length,0); assert.equal(pending.size,0);
const original=f('human')[0];
const first=f('legal')[0];const pi=cells.indexOf(original), di=cells.indexOf(first);
assert.equal(Math.abs((pi%7)-(di%7))*Math.abs(Math.floor(pi/7)-Math.floor(di/7)),2);
play();assert.match(original.className,/burned/);assert.equal(pending.size,1);assert.equal(f('legal').length,0);
first.handlers.click();assert.equal(pending.size,1);
reset();assert.equal(f('burned').length,0);assert.equal(cancelled.length,1);
cancelled[0]();assert.equal(f('burned').length,0);assert.match(dom.status.textContent,/Your turn/);
const legal=f('legal')[0], pos=cells.indexOf(legal);
legal.focus();
assert.equal(active,legal);assert.equal(legal.tabIndex,0);
let prevented=false;
legal.handlers.keydown({key:pos%7<6?'ArrowRight':'ArrowLeft',preventDefault(){prevented=true}});
assert(prevented); assert.equal(active,cells[pos%7<6?pos+1:pos-1]);
let completed=0;
for(const level of ['random','greedy','search']){
  dom.difficulty.value=level;
  for(let game=0;game<8;game++){
    reset();
    let moveCount=0;
    while(f('legal').length){
      assert(moveCount++<25);
      play();tick();
      assert.equal(f('human').length,1);assert.equal(f('computer').length,1);
      const n=f('burned').length;
      assert(n===2*moveCount-1||n===2*moveCount);
    }
    assert.match(dom.status.textContent,/(You win|Computer wins)/);const n=f('burned').length;
    cells[0].handlers.click();tick();assert.equal(f('burned').length,n);
    if(level==='search'){assert.match(dom.stats.textContent,/depth 4.*states.*cutoffs/);assert(dom.scores.children.some(c=>c.className==='chosen'));}
    completed++;
  }
}
console.log(`PASS: ${completed} complete games at all 3 levels; invalid move, keyboard focus, burn count, AI stats, terminal freeze, and stale timer after reset.`);
