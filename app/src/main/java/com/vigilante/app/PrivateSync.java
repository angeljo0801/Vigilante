package com.vigilante.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class PrivateSync {
 private static final String ALIAS="vigilante_private_sync";
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("private_sync",0);}
 static String repo(Context c){return prefs(c).getString("repo","");}
 static boolean configured(Context c){return !repo(c).isEmpty()&&prefs(c).contains("token");}
 static void clear(Context c){prefs(c).edit().clear().commit();}
 static java.security.Key key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
  return ks.getKey(ALIAS,null);
 }
 static void configure(Context c,String repo,String token)throws Exception{
  repo=repo.trim();token=token.trim();if(!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||token.isEmpty())throw new IllegalArgumentException("Repositorio y token requeridos");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  prefs(c).edit().putString("repo",repo).putString("iv",Base64.encodeToString(cipher.getIV(),2)).putString("token",Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),2)).commit();
 }
 static String token(Context c)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs(c).getString("iv",""),2)));
  return new String(cipher.doFinal(Base64.decode(prefs(c).getString("token",""),2)),StandardCharsets.UTF_8);
 }
 static JSONObject request(String repo,String token,String path,JSONObject body,boolean missing)throws Exception{
  HttpURLConnection con=(HttpURLConnection)new URL("https://api.github.com/repos/"+repo+path).openConnection();
  con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(20000);
  con.setRequestProperty("Authorization","Bearer "+token);con.setRequestProperty("Accept","application/vnd.github+json");con.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
  try{
   if(body!=null){con.setRequestMethod("PUT");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");try(OutputStream out=con.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
   int status=con.getResponseCode();if(status==404&&missing)return null;if(status<200||status>=300)throw new IOException("GitHub HTTP "+status);
   try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2000000)throw new IOException("Respuesta demasiado grande");out.write(buf,0,n);}return new JSONObject(out.toString("UTF-8"));}
  }finally{con.disconnect();}
 }
 static synchronized String sync(Context c)throws Exception{
  String repo=repo(c),token=token(c);JSONObject meta=request(repo,token,"",null,false);
  if(!meta.optBoolean("private"))throw new IOException("El repositorio debe ser privado");
  String path="/contents/vigilante-state.json",commands="/contents/vigilante-commands.json";
  JSONObject queue=request(repo,token,commands,null,true);int applied=0;
  JSONArray receipts=new JSONArray();
  if(queue!=null&&ChatInbox.editsEnabled(c)){
   JSONArray rows=new JSONArray(new String(Base64.decode(queue.getString("content"),Base64.DEFAULT),StandardCharsets.UTF_8));
   if(rows.length()>100)throw new IOException("Demasiados comandos");
   for(int i=0;i<rows.length()&&ChatInbox.enabled(c)&&ChatInbox.editsEnabled(c);i++){
    JSONObject row=rows.getJSONObject(i);String id=row.getString("id");if(!id.matches("[a-f0-9-]{36}"))continue;
    String result=prefs(c).getString("receipt:"+id,"");
    if(result.isEmpty()){try{RemoteEdit.apply(c,"VIGILANTE-EDIT/1\n"+row.getJSONObject("command").toString(),id);result="applied";applied++;}catch(Exception e){result="error: "+e.getMessage();}prefs(c).edit().putString("receipt:"+id,result).commit();}
    receipts.put(new JSONObject().put("id",id).put("result",result));
   }
  }
  if(!ChatInbox.enabled(c)||!ChatInbox.editsEnabled(c))return "Acceso bidireccional desactivado";
  JSONObject state=new JSONObject().put("updatedAt",System.currentTimeMillis()).put("alarms",new JSONArray(RemoteEdit.export(c))).put("receipts",receipts);
  JSONObject old=request(repo,token,path,null,true);
  JSONObject body=new JSONObject().put("message","Vigilante device sync").put("content",Base64.encodeToString(state.toString().getBytes(StandardCharsets.UTF_8),2));
  if(old!=null)body.put("sha",old.getString("sha"));
  request(repo,token,path,body,false);try{BusinessSync.sync(c);}catch(Exception e){BusinessSync.prefs(c).edit().putString("status","Error de sincronización: "+e.getMessage()).apply();}return "Conexión privada activa · Modificaciones: "+applied;
 }
}
