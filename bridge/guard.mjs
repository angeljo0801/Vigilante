import dns from 'node:dns/promises';
import ipaddr from 'ipaddr.js';
export function allowedHost(host, allowed){
  return allowed.some(x=>host===x);
}
export async function verifyUrl(value, allowed, lookup=dns.lookup){
  const u=new URL(value);
  if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.port&&u.port!=='80'&&u.port!=='443'||!allowedHost(u.hostname,allowed))throw new Error('Dominio no autorizado en el puente');
  const addresses=await lookup(u.hostname,{all:true});
  if(!addresses.length||addresses.some(a=>ipaddr.process(a.address).range()!=='unicast'))throw new Error('Dirección no pública');
  return u;
}
export function challenge(status,title,text){
  return [401,403,429].includes(status)||/just a moment|attention required|verify (that )?you are human|checking your browser|access denied|robot check|verifica que eres humano/i.test(title)||(/verify (that )?you are human|checking your browser|complete the security check|verifica que eres humano|comprueba que eres humano|please solve the captcha/i.test(text)&&text.length<1800);
}
