package com.trendradar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;

/**
 * Three independent free-tier providers. Conservative counters; no claims that local
 * counters reflect usage on other devices or before app installation.
 */
final class ProviderHub {
    static final String SERP="SerpAPI", SERPER="Serper", SEARCH="SearchAPI";
    static final String[] NAMES={SERP,SERPER,SEARCH};
    private static final String ALIAS="trendradar-provider-hub", PREFS="product_finder_providers";
    private static final int MAX_JSON=2600000;
    static final class Balance {
        String name, status;
        int used, cap, left, remote=-1;
        boolean real, active;
    }
    static final class Results {
        final List<JSONObject> items=new ArrayList<>();
        final List<String> notes=new ArrayList<>();
        int attempts=0;
    }
    private ProviderHub(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    private static String prefId(String name){return name.toLowerCase(Locale.US).replaceAll("[^a-z]","");}
    static int cap(String name){
        if(SERP.equals(name))return 250;
        if(SERPER.equals(name))return 2500;
        return 100;  // SearchAPI free *trial* total, not every month.
    }
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    static void save(Context c,String provider,String value)throws Exception{
        if(SERP.equals(provider))throw new IllegalArgumentException("SerpAPI uses the existing settings");
        if(value.trim().isEmpty())throw new IllegalArgumentException("Enter a key");
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE,key());
        String stored=Base64.encodeToString(aes.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(aes.doFinal(value.trim().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        String id=prefId(provider);
        // New key => reset counter; same key does not reset limits.
        // Preserve local limits across reconnects and changed API keys.
        prefs(c).edit().putString(id+"_secret",stored).apply();
    }
    static void clear(Context c,String provider){
        String id=prefId(provider);
        // Disconnecting does not replenish promotional/trial credits.
        prefs(c).edit().remove(id+"_secret").apply();
    }
    private static String decode(String stored)throws Exception{
        if(stored.isEmpty())return "";
        String[] a=stored.split(":",2);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    static String read(Context c,String provider)throws Exception{
        if(SERP.equals(provider))return ProductFinderActivity.apiKey(c);
        return decode(prefs(c).getString(prefId(provider)+"_secret",""));
    }
    private static String period(Context c){
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date());
    }
    static synchronized int used(Context c,String provider){
        String id=prefId(provider);
        if(SERP.equals(provider)){
            String month=period(c);
            if(!month.equals(prefs(c).getString("serpapi_month",""))){
                prefs(c).edit().putString("serpapi_month",month).putInt(id+"_used",0).apply();
            }
        }
        return prefs(c).getInt(id+"_used",0);
    }
    private static synchronized void reserve(Context c,String provider){
        int u=used(c,provider);
        prefs(c).edit().putInt(prefId(provider)+"_used",u+1).commit();
    }
    private static String enc(String v){return FinderBudget.enc(v);}
    private static JSONObject readJson(HttpURLConnection con)throws Exception{
        try{
            int status=con.getResponseCode();
            if(status<200||status>=300)throw new IOException("HTTP "+status);
            try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    if(out.size()+n>MAX_JSON)throw new IOException("Response too large");
                    out.write(buffer,0,n);
                }
                JSONObject json=new JSONObject(out.toString("UTF-8"));
                if(json.has("error"))throw new IOException(json.optString("error"));
                return json;
            }
        } finally{con.disconnect();}
    }
    private static JSONObject httpsGet(String url,String token)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(22000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        if(token!=null&&!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
        return readJson(conn);
    }
    private static JSONObject serpAccount(String key)throws Exception{
        JSONObject info=FinderBudget.account(key);
        if(info.has("error"))throw new IOException(info.optString("error"));
        return info;
    }
    private static JSONObject searchAccount(String key)throws Exception{
        return httpsGet("https://www.searchapi.io/api/v1/me",key).optJSONObject("account");
    }
    static Balance balance(Context c,String provider,boolean online) {
        Balance b=new Balance();b.name=provider;b.cap=cap(provider);
        b.used=used(c,provider);b.left=Math.max(0,b.cap-b.used);
        try{
            String secret=read(c,provider);
            b.active=!secret.isEmpty();
            b.status=b.active?"Contador local desde esta instalación":"Sin conectar";
            if(!b.active)return b;
            if(online&&SERP.equals(provider)){
                JSONObject remote=serpAccount(secret);
                b.remote=remote.optInt("total_searches_left",-1);
                b.real=b.remote>=0;
                if(remote.optDouble("plan_monthly_price",0)>0) {
                    b.left=0;b.status="Plan de pago detectado: bloqueado para evitar cargos";
                } else {
                    b.left=Math.max(0,Math.min(b.left,b.remote));
                    b.status="Saldo real: "+b.remote+" · uso local: "+b.used;
                }
            } else if(online&&SEARCH.equals(provider)){
                JSONObject remote=searchAccount(secret);
                if(remote==null)throw new IOException("No account details");
                b.remote=remote.optInt("remaining_searches",-1);
                b.real=b.remote>=0;
                if(remote.optBoolean("has_automatic_early_renewal_enabled",false)) {
                    b.left=0;b.status="Renovación anticipada activa: desactívala en SearchAPI";
                }else{
                    b.left=Math.max(0,Math.min(b.left,b.remote));
                    b.status="Saldo real: "+b.remote+" · consumo local: "+b.used+"/100 (prueba)";
                }
            }else if(SERPER.equals(provider)){
                b.status="Estimado local: "+b.used+"/2500 (créditos de bienvenida, no renovables)";
            }
        }catch(Exception e){
            b.left=online?0:b.left;
            b.status="No se pudo verificar saldo: "+e.getMessage();
        }
        return b;
    }
    static boolean eligible(Context c,String provider){
        Balance b=balance(c,provider,true);
        return b.active && b.left>0;
    }
    private static JSONObject serperQuery(String q,String key)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL("https://google.serper.dev/shopping").openConnection();
        conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setConnectTimeout(12000);
        conn.setReadTimeout(24000);conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type","application/json");
        conn.setRequestProperty("X-API-KEY",key);
        byte[] body=new JSONObject().put("q",q).put("gl","us").put("hl","en").put("num",10).toString().getBytes(StandardCharsets.UTF_8);
        try(OutputStream out=conn.getOutputStream()){out.write(body);}
        return readJson(conn);
    }
    private static JSONObject searchApiQuery(String store,String q,int offset,String key)throws Exception{
        String engine,extra="";
        if("SHEIN".equals(store)){engine="shein_search";extra="&shein_domain=us.shein.com";}
        else if("AliExpress".equals(store)){engine="aliexpress_search";extra="&delivery_country=US&currency=USD";}
        else{engine="google_shopping";extra="&gl=us&hl=en";q=q+" "+store;}
        String url="https://www.searchapi.io/api/v1/search?engine="+engine+"&q="+enc(q)+
            "&page="+(offset/20+1)+extra;
        return httpsGet(url,key);
    }
    private static String hostShop(String s) {
        String x=s.toLowerCase(Locale.US).trim();
        if(x.contains("amazon"))return "Amazon";
        if(x.contains("shein"))return "SHEIN";
        if(x.contains("temu"))return "Temu";
        if(x.contains("walmart"))return "Walmart";
        if(x.contains("ebay"))return "eBay";
        if(x.contains("target"))return "Target";
        if(x.contains("aliexpress")||x.contains("ali express"))return "AliExpress";
        if(x.contains("best buy")||x.contains("bestbuy"))return "Best Buy";
        if(x.contains("home depot")||x.contains("homedepot"))return "Home Depot";
        if(x.contains("etsy"))return "Etsy";
        return "";
    }
    private static double price(JSONObject x){
        double p=x.optDouble("extracted_price_usd",0);
        if(p==0)p=x.optDouble("extracted_price",0);
        if(p==0){
            Object raw=x.opt("price");
            if(raw instanceof Number)p=((Number)raw).doubleValue();
            else if(raw instanceof JSONObject)p=((JSONObject)raw).optDouble("extracted_price",0);
            else if(raw instanceof String){
                String s=((String)raw).trim();
                if(s.startsWith("$")||s.startsWith("US$")){
                    try{p=Double.parseDouble(s.replaceAll("[^0-9.]",""));}catch(Exception ignored){}
                }
            }
        }
        return Math.max(0,p);
    }
    private static JSONObject normalize(JSONObject x,String provider,String requestedStore){
        JSONObject y=new JSONObject();
        try{
            String url=x.optString("link","");
            if(url.isEmpty())url=x.optString("url","");
            if(url.isEmpty())url=x.optString("product_link","");
            if(!url.startsWith("https://"))return null;
            String image=x.optString("thumbnail",x.optString("imageUrl",""));
            if(image.isEmpty())image=x.optString("image","");
            if(image.isEmpty()){JSONArray imgs=x.optJSONArray("images");if(imgs!=null)image=imgs.optString(0,"");}
            String title=x.optString("title","");
            if(title.length()<4)return null;
            String store=requestedStore;
            String reported=hostShop(x.optString("source",x.optString("seller","")));
            if("Google Shopping".equals(store))store=reported;
            else if(!reported.isEmpty()&&!reported.equals(store))return null;
            if(store.isEmpty())return null;
            // Do not mislabel Google Shopping pages from unrelated stores.
            String linkMerchant=hostShop(url);
            if(!linkMerchant.isEmpty()&&!linkMerchant.equals(store))return null;
            if(linkMerchant.isEmpty()&&reported.isEmpty())return null;
            y.put("shop",store);y.put("title",title);y.put("price",price(x));
            y.put("rating",x.optDouble("rating",0));y.put("reviews",x.optInt("reviews",0));
            y.put("image",image.startsWith("https://")?image:"");
            y.put("url",url);y.put("provider",provider);
            y.put("condition",x.optString("condition",""));
            y.put("model",x.optString("sku",x.optString("asin",x.optString("product_id",""))));
        }catch(Exception e){return null;}
        return y;
    }
    /** Uncached requests are pre-reserved to safely handle interruptions / unknown costs. */
    private static List<JSONObject> query(Context c,String provider,String store,String q,int offset,String key)throws Exception{
        String tag="hub:"+provider+":"+store+":"+q.toLowerCase(Locale.US).trim()+":"+offset;
        JSONObject cached=FinderBudget.cached(c,tag,key);
        JSONObject raw;
        if(cached!=null){raw=cached;}else{
            Balance balance=balance(c,provider,true);
            if(!balance.active||balance.left<=0)throw new IOException(balance.status);
            reserve(c,provider); // pessimistic: includes uncertain failed calls
            if(SERPER.equals(provider))raw=serperQuery(q+" "+store,key);
            else if(SEARCH.equals(provider))raw=searchApiQuery(store,q,offset,key);
            else {
                // Existing provider handles its own official per-account checks/cache.
                FinderSearchV2.Result legacy=FinderSearchV2.search(c,q,store,offset,key);
                if(legacy.message.startsWith("Búsqueda bloqueada o fallida"))
                    throw new IOException(legacy.message);
                JSONArray list=new JSONArray();for(JSONObject x:legacy.items)list.put(x);
                raw=new JSONObject().put("_normalized",list);
            }
            FinderBudget.putCache(c,tag,key,raw);
        }
        List<JSONObject> products=new ArrayList<>();
        JSONArray ready=raw.optJSONArray("_normalized");
        if(ready!=null){
            for(int i=0;i<ready.length();i++){
                JSONObject item=ready.optJSONObject(i);
                if(item!=null){try{item.put("provider",provider);}catch(Exception ignored){}products.add(item);}
            }
            return products;
        }
        JSONArray arr=raw.optJSONArray(SERPER.equals(provider)?"shopping":(SEARCH.equals(provider)&&
            ("SHEIN".equals(store)||"AliExpress".equals(store))?"organic_results":"shopping_results");
        if(arr==null)arr=raw.optJSONArray("organic_results");
        if(arr==null)arr=raw.optJSONArray("shopping_results");
        if(arr!=null)for(int i=0;i<arr.length()&&products.size()<40;i++){
            JSONObject row=arr.optJSONObject(i);if(row==null)continue;
            JSONObject item=normalize(row,provider,store);
            if(item==null)continue;
            // Google Shopping can return other merchants for "query + store".
            // Filter only if source/merchant is known and explicitly different.
            if(SEARCH.equals(provider)&&!"SHEIN".equals(store)&&!"AliExpress".equals(store)){
                String merchant=hostShop(row.optString("source",row.optString("seller","")));
                if(!merchant.isEmpty()&&!merchant.equals(store))continue;
            }
            products.add(item);
        }
        return products;
    }
    private static List<String> order(String store){
        if(FinderSearchV2.isWholesale(store))
            return Collections.singletonList(SERP); // wholesale catalog has no verified numeric unit price
        if("SHEIN".equals(store)||"AliExpress".equals(store))
            return Arrays.asList(SEARCH,SERPER,SERP);
        if("Amazon".equals(store)||"eBay".equals(store))
            return Arrays.asList(SERP,SERPER,SEARCH);
        return Arrays.asList(SERPER,SEARCH,SERP);
    }
    static Results find(Context c,String q,Collection<String> selected,int offset){
        Results all=new Results();
        if(selected==null||selected.isEmpty()){all.notes.add("Selecciona una o varias tiendas.");return all;}
        for(String shop:new LinkedHashSet<>(selected)){
            if("Todas".equals(shop))continue;
            boolean found=false;
            StringBuilder reason=new StringBuilder();
            for(String provider:order(shop)){
                String api;
                try{api=read(c,provider);}catch(Exception e){api="";}
                if(api.isEmpty())continue;
                try{
                    List<JSONObject> hits=query(c,provider,shop,q,offset,api);
                    all.attempts++;
                    if(!hits.isEmpty()){
                        all.items.addAll(hits);
                        all.notes.add(shop+": "+hits.size()+" de "+provider);
                        found=true;break; // don't spend another provider on the same store
                    }
                    reason.append(provider).append(" sin productos. ");
                }catch(Exception e){
                    reason.append(provider).append(": ").append(e.getMessage()).append(". ");
                }
            }
            if(!found)all.notes.add(shop+": "+(reason.length()==0?"ningún proveedor configurado":reason));
        }
        return all;
    }
}
