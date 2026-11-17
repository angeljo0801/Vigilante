package com.vigilante.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BusinessSync {
 private static final Object QUEUE_LOCK=new Object();
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("business_engine",0);}
 static JSONArray array(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
 static JSONArray reports(Context c){return array(prefs(c).getString("reports","[]"));}
 static JSONArray requests(Context c){return array(prefs(c).getString("requests","[]"));}
 static JSONArray visibleRequests(Context c){JSONArray all=array(prefs(c).getString("sent","[]"));JSONArray queued=requests(c);for(int i=0;i<queued.length();i++)all.put(queued.optJSONObject(i));return all;}
 static JSONObject report(Context c,String id){JSONArray a=reports(c);for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p;}return new JSONObject();}
 static String enqueue(Context c,String action,String project,String title,String type,String market,String description)throws JSONException{
  String id=UUID.randomUUID().toString();
  synchronized(QUEUE_LOCK){JSONArray a=requests(c);
  if("submit".equals(action))project=id;
  a.put(new JSONObject().put("id",id).put("action",action).put("projectId",project).put("title",title).put("type",type).put("market",market).put("description",description).put("createdAt",System.currentTimeMillis()));
  prefs(c).edit().putString("requests",a.toString()).commit();
  return "proposal".equals(action)?id:project;}
 }
 static JSONArray decode(JSONObject file)throws Exception{return file==null?new JSONArray():new JSONArray(new String(Base64.decode(file.getString("content"),Base64.DEFAULT),StandardCharsets.UTF_8));}
 static synchronized String sync(Context c)throws Exception{
  if(!PrivateSync.configured(c)||!ChatInbox.enabled(c)||!ChatInbox.editsEnabled(c))return "Activa la conexión privada bidireccional en Vigilante.";
  String repo=PrivateSync.repo(c),token=PrivateSync.token(c);
  if(!PrivateSync.request(repo,token,"",null,false).optBoolean("private"))throw new java.io.IOException("Se requiere repositorio privado");
  String in="/contents/business-reports.json",out="/contents/business-requests.json";
  JSONObject remote=PrivateSync.request(repo,token,in,null,true);
  if(remote!=null){JSONArray data=decode(remote);prefs(c).edit().putString("reports",data.toString()).commit();}
  JSONArray local; synchronized(QUEUE_LOCK){local=requests(c);}
  if(local.length()>0){
   JSONObject old=PrivateSync.request(repo,token,out,null,true);
   JSONArray merged=decode(old);java.util.HashSet<String> ids=new java.util.HashSet<>();
   for(int i=0;i<merged.length();i++){JSONObject x=merged.optJSONObject(i);if(x!=null)ids.add(x.optString("id"));}
   boolean changed=false;
   for(int i=0;i<local.length();i++){JSONObject x=local.optJSONObject(i);if(x!=null&&ids.add(x.optString("id"))){merged.put(x);changed=true;}}
   if(changed){
    JSONObject body=new JSONObject().put("message","Business Engine device requests").put("content",Base64.encodeToString(merged.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
    if(old!=null)body.put("sha",old.getString("sha"));
    PrivateSync.request(repo,token,out,body,false);
   }
   synchronized(QUEUE_LOCK){JSONArray sent=array(prefs(c).getString("sent","[]"));java.util.HashSet<String> delivered=new java.util.HashSet<>();for(int i=0;i<local.length();i++){JSONObject x=local.optJSONObject(i);if(x!=null){delivered.add(x.optString("id"));x.put("sent",true);sent.put(x);}}JSONArray remaining=new JSONArray(),latest=requests(c);for(int i=0;i<latest.length();i++){JSONObject x=latest.optJSONObject(i);if(x!=null&&!delivered.contains(x.optString("id")))remaining.put(x);}prefs(c).edit().putString("sent",sent.toString()).putString("requests",remaining.toString()).commit();}
  }
  String status="Sincronizado · "+reports(c).length()+" proyectos · "+local.length()+" solicitudes enviadas";
  prefs(c).edit().putString("status",status).putLong("last",System.currentTimeMillis()).apply();return status;
 }
}
