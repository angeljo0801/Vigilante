package com.trendradar.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** One-credit search for many shops; no direct retailer scraping. */
final class FinderSearchV2 {
    static final String[] STORES = {"Todas", "Amazon", "SHEIN", "Temu", "Walmart", "eBay", "Target", "AliExpress", "Best Buy", "Home Depot", "Etsy"};
    static final class Result {
        final List<JSONObject> items = new ArrayList<>();
        String message = "";
    }
    private static String enc(String q) { return FinderBudget.enc(q); }
    static String storeUrl(String shop,String query) {
        String q=enc(query);
        switch(shop) {
            case "Amazon":return "https://www.amazon.com/s?k="+q;
            case "SHEIN":return "https://us.shein.com/pdsearch/"+q+"/";
            case "Temu":return "https://www.temu.com/search_result.html?search_key="+q;
            case "Walmart":return "https://www.walmart.com/search?q="+q;
            case "eBay":return "https://www.ebay.com/sch/i.html?_nkw="+q;
            case "Target":return "https://www.target.com/s?searchTerm="+q;
            case "AliExpress":return "https://www.aliexpress.com/w/wholesale-"+q.replace("+","-")+".html";
            case "Best Buy":return "https://www.bestbuy.com/site/searchpage.jsp?st="+q;
            case "Home Depot":return "https://www.homedepot.com/s/"+q;
            case "Etsy":return "https://www.etsy.com/search?q="+q;
            default:return "https://www.google.com/search?tbm=shop&q="+q;
        }
    }
    private static String merchant(String text) {
        String s=text.toLowerCase(Locale.US).trim();
        if(s.contains("amazon"))return "Amazon";
        if(s.contains("shein"))return "SHEIN";
        if(s.contains("temu"))return "Temu";
        if(s.contains("walmart"))return "Walmart";
        if(s.contains("ebay"))return "eBay";
        if(s.equals("target")||s.startsWith("target ")||s.contains("target.com"))return "Target";
        if(s.contains("aliexpress")||s.contains("ali express"))return "AliExpress";
        if(s.contains("best buy")||s.contains("bestbuy"))return "Best Buy";
        if(s.contains("home depot")||s.contains("homedepot"))return "Home Depot";
        if(s.contains("etsy"))return "Etsy";
        return "";
    }
    private static double price(JSONObject x) {
        double p=x.optDouble("extracted_price",0);
        if(p>0)return p;
        Object value=x.opt("price");
        if(value instanceof Number)return ((Number)value).doubleValue();
        if(value instanceof JSONObject) {
            JSONObject j=(JSONObject)value;
            p=j.optDouble("extracted_price",0);
            if(p>0)return p;
            JSONObject from=j.optJSONObject("from");
            if(from!=null)return Math.max(0,from.optDouble("extracted",0));
            return Math.max(0,j.optDouble("extracted",0));
        }
        return 0;
    }
    private static int reviews(JSONObject x) {
        Object r=x.opt("reviews");
        if(r instanceof Number)return ((Number)r).intValue();
        try{return Integer.parseInt(x.optString("reviews","0").replaceAll("[^0-9]",""));}
        catch(Exception e){return 0;}
    }
    private static JSONObject normalize(String shop,JSONObject x) {
        JSONObject y=new JSONObject();
        try {
            String link=x.optString("link","");
            if(link.isEmpty())link=x.optString("product_link","");
            if(!link.startsWith("https://"))link="";
            String image=x.optString("thumbnail","");
            if(image.isEmpty())image=x.optString("serpapi_thumbnail","");
            if(!image.startsWith("https://"))image="";
            y.put("shop",shop);
            y.put("title",x.optString("title",""));
            y.put("price",price(x));
            y.put("rating",x.optDouble("rating",0));
            y.put("reviews",reviews(x));
            y.put("image",image);
            y.put("url",link);
            y.put("condition",x.optString("condition",x.optString("second_hand_condition","")));
        }catch(Exception ignored){}
        return y;
    }
    static Result search(Context context,String query,String selected,int offset,String key) {
        Result result=new Result();
        try {
            String url,cacheKey;
            if(selected.equals("Amazon")) {
                url="https://serpapi.com/search.json?engine=amazon&amazon_domain=amazon.com&language=en_US&k="+enc(query)+"&page="+(offset/20+1)+"&api_key="+enc(key);
                cacheKey="amazon:"+query+":"+offset;
            } else if(selected.equals("eBay")) {
                url="https://serpapi.com/search.json?engine=ebay&ebay_domain=ebay.com&_nkw="+enc(query)+"&_pgn="+(offset/20+1)+"&api_key="+enc(key);
                cacheKey="ebay:"+query+":"+offset;
            } else {
                String expanded=selected.equals("Todas")?query:query+" "+selected;
                url="https://serpapi.com/search.json?engine=google_shopping&gl=us&hl=en&num=100&q="+enc(expanded)+"&start="+offset+"&api_key="+enc(key);
                cacheKey="shopping:"+expanded+":"+offset;
            }
            JSONObject json=FinderBudget.search(context,url,cacheKey,key);
            JSONArray a=json.optJSONArray(selected.equals("Amazon")||selected.equals("eBay")?"organic_results":"shopping_results");
            if(a==null)a=json.optJSONArray("inline_shopping_results");
            Set<String> seen=new HashSet<>();
            if(a!=null)for(int i=0;i<a.length()&&result.items.size()<100;i++) {
                JSONObject row=a.optJSONObject(i);
                if(row==null)continue;
                String store=selected.equals("Amazon")||selected.equals("eBay")?selected:merchant(row.optString("source",""));
                if(store.isEmpty()||(!selected.equals("Todas")&&!selected.equals(store)))continue;
                if(selected.equals("Amazon")&&row.optString("link","").isEmpty()&&!row.optString("asin","").isEmpty()) {
                    row.put("link","https://www.amazon.com/dp/"+enc(row.optString("asin")));
                }
                JSONObject product=normalize(store,row);
                String link=product.optString("url");
                if(link.isEmpty()||product.optString("title").isEmpty())continue;
                if(seen.add(link.split("[?#]",2)[0]))result.items.add(product);
            }
            result.items.sort((a1,b1)->{
                double a=a1.optDouble("price",0),b=b1.optDouble("price",0);
                return Double.compare(a>0?a:Double.POSITIVE_INFINITY,b>0?b:Double.POSITIVE_INFINITY);
            });
            result.message=result.items.size()+" resultados · máximo 1 consulta nueva a SerpAPI (o caché) · ordenados por precio, sin envío.";
            if(result.items.isEmpty())result.message+=" Prueba la búsqueda directa en la tienda.";
        } catch(Exception e) {
            result.message="Búsqueda bloqueada o fallida: "+(e.getMessage()==null?"Sin conexión":e.getMessage());
        }
        return result;
    }
}
