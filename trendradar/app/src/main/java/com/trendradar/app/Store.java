package com.trendradar.app;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.*;
import java.io.*;
import org.json.*;

final class Store {
 static final String ROOT="https://api.github.com/repos/angeljo0801/Vigilante-Sync/contents/";
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("radar",0);}
 static javax.crypto.SecretKey key() throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
  if(!ks.containsAlias("trendradar-sync")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("trendradar-sync",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
  return (javax.crypto.SecretKey)ks.getKey("trendradar-sync",null);
 }
 static void token(Context c,String value)throws Exception{
  if(value.trim().isEmpty()){prefs(c).edit().remove("token").apply();return;}
  Cipher a=Cipher.getInstance("AES/GCM/NoPadding");a.init(Cipher.ENCRYPT_MODE,key());
  prefs(c).edit().putString("token",Base64.encodeToString(a.getIV(),2)+":"+Base64.encodeToString(a.doFinal(value.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)),2)).apply();
 }
 static String token(Context c)throws Exception{
  String v=prefs(c).getString("token",""); if(v.isEmpty())return ""; String[] x=v.split(":");
  Cipher a=Cipher.getInstance("AES/GCM/NoPadding");a.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(x[0],2)));
  return new String(a.doFinal(Base64.decode(x[1],2)),java.nio.charset.StandardCharsets.UTF_8);
 }
 static JSONObject http(Context c,String file,String method,JSONObject body)throws Exception{
  String token=token(c);if(token.isEmpty())throw new IOException("Conecta el repositorio privado en Ajustes.");
  HttpURLConnection h=(HttpURLConnection)new URL(ROOT+file).openConnection();
  h.setRequestMethod(method); h.setConnectTimeout(15000);h.setReadTimeout(20000);h.setInstanceFollowRedirects(false);
  h.setRequestProperty("Authorization","Bearer "+token);h.setRequestProperty("Accept","application/vnd.github+json");h.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
  try{if(body!=null){h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json");try(OutputStream o=h.getOutputStream()){o.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
  int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("GitHub HTTP "+code+" · "+file);
  try(InputStream in=h.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new IOException("Archivo demasiado grande");}return new JSONObject(out.toString("UTF-8"));}}finally{h.disconnect();}
 }
 static JSONObject decode(JSONObject envelope)throws Exception{return new JSONObject(new String(Base64.decode(envelope.getString("content"),Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8));}
 static JSONObject read(Context c,String file)throws Exception{return decode(http(c,file,"GET",null));}
 static void saveConfig(Context c,JSONObject config,String expectedSha)throws Exception{
  JSONObject latest=http(c,"trendradar-config.json","GET",null);
  if(!latest.getString("sha").equals(expectedSha))throw new IOException("Configuración cambió remotamente. Recarga antes de guardar.");
  JSONObject b=new JSONObject().put("message","TrendRadar: configuración desde Android").put("sha",expectedSha).put("content",Base64.encodeToString(config.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8),2));
  http(c,"trendradar-config.json","PUT",b);
 }
 static JSONObject feed(Context c)throws Exception{return new JSONObject(prefs(c).getString("feed","{\"items\":[]}"));}
 static synchronized int sync(Context c)throws Exception{
  JSONObject f=read(c,"trendradar-feed.json");JSONArray items=f.getJSONArray("items");if(items.length()>500)throw new IOException("Feed demasiado grande");
  java.util.Set<String> seen=new java.util.HashSet<>(prefs(c).getStringSet("seen",new java.util.HashSet<>()));
  boolean initial=!prefs(c).getBoolean("initialized",false);int count=0;java.util.List<JSONObject> fresh=new java.util.ArrayList<>();
  for(int i=0;i<items.length();i++){JSONObject v=items.getJSONObject(i);String id=v.getString("id");if(!seen.contains(id)&&!initial){count++;fresh.add(v);}seen.add(id);}
  if(seen.size()>10000){seen.clear();for(int i=0;i<items.length();i++)seen.add(items.getJSONObject(i).getString("id"));}
  prefs(c).edit().putString("feed",f.toString()).putStringSet("seen",seen).putBoolean("initialized",true).putLong("last",System.currentTimeMillis()).putString("error","").apply();
  if(count>0)RadarWorker.notifyNew(c,fresh);return count;
 }
}
