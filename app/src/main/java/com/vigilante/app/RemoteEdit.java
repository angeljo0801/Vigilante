package com.vigilante.app;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.util.regex.Pattern;

final class RemoteEdit {
 static String export(Context c){
  JSONArray a=new JSONArray();
  for(Watch w:WatchStore.all(c))try{JSONObject j=w.toJson();j.remove("history");a.put(j);}catch(Exception ignored){}
  return a.toString();
 }
 static Watch target(List<Watch> all,JSONObject j)throws Exception{
  String id=j.optString("id"),name=j.optString("name");if(id.isEmpty()&&name.isEmpty())throw new IllegalArgumentException("Falta identificador");
  Watch found=null;for(Watch w:all)if(!w.inboxDraft&&(!id.isEmpty()?id.equals(w.id):name.equals(w.name))){if(found!=null)throw new IllegalArgumentException("Alarma ambigua");found=w;}
  if(found==null)throw new IllegalArgumentException("Alarma no encontrada");return found;
 }
 static void patch(Watch w,JSONObject p)throws Exception{
  Iterator<String> keys=p.keys();while(keys.hasNext()){
   String k=keys.next();if(!Arrays.asList("regex","selector","attribute","keyword","numericUnit","engine","times","intervalMinutes","enabled","pinNumeric","rule","url").contains(k))throw new IllegalArgumentException("Campo no permitido: "+k);
  }
  if(p.has("regex")){String x=p.getString("regex");if(x.length()>2000)throw new IllegalArgumentException("Regex demasiado largo");Pattern.compile(x);w.regex=x;}
  if(p.has("selector"))w.selector=p.getString("selector");
  if(p.has("attribute"))w.attribute=p.getString("attribute");
  if(p.has("keyword"))w.keyword=p.getString("keyword");
  if(p.has("numericUnit"))w.numericUnit=p.getString("numericUnit");
  if(p.has("url")){String x=p.getString("url");if(!TaskImport.validUrl(x))throw new IllegalArgumentException("URL inválida");w.url=x;}
  if(p.has("engine")){String x=p.getString("engine");if(!Arrays.asList(Engines.MODES).contains(x))throw new IllegalArgumentException("Motor inválido");w.engine=x;w.preferredEngine="";w.dynamic=!"HTML".equals(x);}
  if(p.has("times"))w.times=Scheduler.normalizeTimes(p.getString("times"));
  if(p.has("intervalMinutes")){int x=p.getInt("intervalMinutes");if(!Arrays.asList(15,30,60,120,360,720,1440).contains(x))throw new IllegalArgumentException("Intervalo inválido");w.intervalMinutes=x;}
  if(p.has("enabled"))w.enabled=p.getBoolean("enabled");
  if(p.has("pinNumeric"))w.pinNumeric=p.getBoolean("pinNumeric");
  if(p.has("rule")){String x=p.getString("rule");if(!Arrays.asList("CHANGES","ALWAYS","APPEARS","DISAPPEARS","ABOVE","BELOW").contains(x))throw new IllegalArgumentException("Regla inválida");w.rule=x;}
  if(!w.attribute.isEmpty()&&w.selector.isEmpty())throw new IllegalArgumentException("Falta selector");
  if("ABOVE".equals(w.rule)||"BELOW".equals(w.rule))WebScanner.number(w.keyword);
 }
 static void apply(Context c,String raw,String commandId)throws Exception{
  JSONObject j=new JSONObject(raw.trim().substring("VIGILANTE-EDIT/1".length()).trim());
  Watch old=target(WatchStore.all(c),j);String op=j.getString("op");
  if("check".equals(op)){if(!old.enabled)throw new IllegalArgumentException("Alarma pausada");Scheduler.enqueueClock(c,old);return;}
  if("delete".equals(op)){ // reversible: pause instead of destroying history
   WatchStore.mutate(c,old.id,w->w.enabled=false);Scheduler.cancel(c,old.id);AlarmNotifier.refreshValue(c,WatchStore.find(c,old.id));return;
  }
  if(!"patch".equals(op))throw new IllegalArgumentException("Operación desconocida");
  JSONObject p=j.getJSONObject("patch");Watch candidate=Watch.fromJson(old.toJson());patch(candidate,p);
  if("PLAYWRIGHT".equals(candidate.engine)&&!RemoteBrowser.configured(c))throw new IllegalArgumentException("Puente no configurado");
  if(!ChatInbox.enabled(c)||!ChatInbox.editsEnabled(c))throw new IllegalArgumentException("Permiso revocado");
  boolean changed=WatchStore.mutate(c,old.id,w->{try{
    Watch before=Watch.fromJson(w.toJson());patch(w,p);
    if(!w.sameTargetAs(before)){w.actionRequired=false;w.lastError="Configuración modificada; pendiente de comprobar";w.initialized=false;w.lastFingerprint="";}
   }catch(Exception e){throw new IllegalArgumentException(e);}});
  if(!changed)throw new IllegalArgumentException("Alarma eliminada");
  Watch latest=WatchStore.find(c,old.id);Scheduler.cancel(c,old.id);Scheduler.schedule(c,latest);
 }
}
