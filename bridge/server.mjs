import http from 'node:http';
import crypto from 'node:crypto';
import { chromium } from 'playwright';
import { verifyUrl, challenge } from './guard.mjs';
const token=process.env.VIGILANTE_TOKEN||'';
const allowed=(process.env.VIGILANTE_ALLOWED_HOSTS||'').split(',').map(x=>x.trim().toLowerCase()).filter(Boolean);
const port=Number(process.env.PORT||8787);
if(token.length<32||!allowed.length)throw new Error('Configura VIGILANTE_TOKEN (32+ caracteres) y VIGILANTE_ALLOWED_HOSTS.');
let busy=false, browser;
function authenticated(req){
  const a=Buffer.from(req.headers.authorization||'');
  const b=Buffer.from('Bearer '+token);
  return a.length===b.length&&crypto.timingSafeEqual(a,b);
}
function reply(res,status,data){
  res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});
  res.end(JSON.stringify(data));
}
async function body(req){
  let data='';
  for await(const chunk of req){data+=chunk;if(data.length>8192)throw new Error('Solicitud demasiado grande');}
  return JSON.parse(data);
}
async function render(input){
  const url=await verifyUrl(input.url,allowed);
  const wait=Math.max(3,Math.min(25,Number(input.wait)||8));
  if(!browser?.isConnected())browser=await chromium.launch({headless:true,chromiumSandbox:true});
  const context=await browser.newContext({serviceWorkers:'block',acceptDownloads:false});
  try{
    await context.route('**/*',async route=>{
      try{await verifyUrl(route.request().url(),allowed);await route.continue();}
      catch{await route.abort();}
    });
    await context.routeWebSocket('**/*',ws=>ws.close());
    const page=await context.newPage();
    const response=await page.goto(url.href,{waitUntil:'domcontentloaded',timeout:30000});
    const status=response?.status()||0;
    if([401,403,429].includes(status))return {ok:false,actionRequired:true,error:'Acceso restringido por el sitio (HTTP '+status+').'};
    if(status>=400)throw new Error('HTTP '+status);
    await page.waitForTimeout(wait*1000);
    const title=await page.title(), text=await page.locator('body').innerText({timeout:5000}).catch(()=> '');
    if(challenge(status,title,text))return {ok:false,actionRequired:true,error:'Posible verificación humana. Respeta las restricciones del sitio.'};
    await verifyUrl(page.url(),allowed);
    const html=await page.content();
    if(html.length>4000000)throw new Error('Contenido demasiado grande');
    return {ok:true,url:page.url(),html};
  }finally{await context.close();}
}
const server=http.createServer(async(req,res)=>{
  if(!authenticated(req))return reply(res,401,{ok:false,error:'No autorizado'});
  if(req.method==='GET'&&req.url==='/health')return reply(res,200,{ok:true});
  if(req.method!=='POST'||req.url!=='/render')return reply(res,404,{ok:false,error:'Ruta desconocida'});
  if(busy)return reply(res,503,{ok:false,error:'Puente ocupado; prueba más tarde'});
  busy=true;
  try{reply(res,200,await render(await body(req)));}
  catch(e){reply(res,200,{ok:false,error:String(e.message).slice(0,400)});}
  finally{busy=false;}
});
server.requestTimeout=90000;
server.listen(port,'127.0.0.1',()=>console.log('Vigilante Bridge: http://127.0.0.1:'+port+' (privado)'));
