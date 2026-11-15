package com.vigilante.app;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.json.JSONObject;
import org.jsoup.Jsoup;
final class CargoReference {
 static final String URL="https://cargoconnect-production.up.railway.app/public/exchange-reference.json";
 static boolean matches(Watch w){
  String n=w.name.toLowerCase(Locale.ROOT).replace(" ","");
  return n.contains("usd/cup")&&n.contains("cibercuba")&&w.url.toLowerCase(Locale.ROOT).startsWith("https://www.cibercuba.com/");
 }
 static WebScanner.Observation scan(Watch w)throws IOException{
  try{
   String raw=Jsoup.connect(URL).ignoreContentType(true).timeout(20000).maxBodySize(16384).followRedirects(false).execute().body();
   JSONObject j=new JSONObject(raw);
   Object rate=j.get("rate");if(!(rate instanceof Number)||!Double.isFinite(((Number)rate).doubleValue())||((Number)rate).doubleValue()<=0||!"CiberCuba".equals(j.getString("source"))||!j.getString("url").startsWith("https://www.cibercuba.com/noticias/"))throw new IOException("Referencia CargoConnect inválida");
   long time=OffsetDateTime.parse(j.getString("observedAt")).toInstant().toEpochMilli(),now=System.currentTimeMillis();
   if(time>now+300000||now-time>36*3600000L)throw new IOException("Referencia CargoConnect sin actualizar (más de 36 horas)");
   String value=new java.math.BigDecimal(rate.toString()).stripTrailingZeros().toPlainString();
   boolean matched=!w.keyword.isEmpty()&&value.contains(w.keyword);
   if("ABOVE".equals(w.rule)||"BELOW".equals(w.rule)){double limit=WebScanner.number(w.keyword),v=WebScanner.number(value);matched="ABOVE".equals(w.rule)?v>limit:v<limit;}
   WebScanner.Observation o=new WebScanner.Observation(value,WebScanner.sha256(value),matched);o.observedAt=time;return o;
  }catch(Exception e){throw new IOException("CargoConnect: "+e.getMessage(),e);}
 }
}
