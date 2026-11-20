package com.trendradar.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.*;

/**
 * Conservative duplicate grouping. One product card, multiple retailer offers.
 * Keep variants separate if model, size, quantity, colour or specification differs.
 */
final class ProductDeduper {
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
        "the","and","for","with","from","this","that","a","an","new","latest","original",
        "official","brand","sale","buy","at","by","of","packaging","free","shipping",
        "amazon","walmart","ebay","temu","shein","target","aliexpress","etsy","best",
        "home","depot","www","com","item","product","products","store","bestbuy"));
    private ProductDeduper(){}
    private static String norm(String t) {
        return (" "+t.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ").trim()+" ").replaceAll("\\s+"," ").trim();
    }
    private static Set<String> tokens(String name) {
        Set<String> result=new HashSet<>();
        for(String token:norm(name).split(" "))if(token.length()>1&&!STOP.contains(token))result.add(token);
        return result;
    }
    private static Set<String> specs(Set<String> words){
        Set<String> s=new HashSet<>();
        for(String word:words)if(word.matches(".*\\d.*")||Arrays.asList(
            "black","white","blue","red","pink","green","yellow","silver","gold","gray","grey",
            "small","medium","large","xl","xxl","mini","max","pro","plus","ultra","new","used")
            .contains(word))s.add(word);
        return s;
    }
    private static String normalizeLink(String link){
        try{
            URI u=new URI(link);
            String h=u.getHost()==null?"":u.getHost().toLowerCase(Locale.US).replaceFirst("^www\\.","");
            String path=u.getPath()==null?"":u.getPath().replaceAll("/+$","");
            return h+path;
        }catch(Exception e){return link.split("[?#]",2)[0];}
    }
    static boolean equivalent(JSONObject a,JSONObject b){return sameProduct(a,b);}
    private static boolean sameProduct(JSONObject a,JSONObject b){
        String conditionA=a.optString("condition","").toLowerCase(Locale.US);
        String conditionB=b.optString("condition","").toLowerCase(Locale.US);
        if(!conditionA.isEmpty()&&!conditionB.isEmpty()&&!conditionA.equals(conditionB))return false;
        String ua=normalizeLink(a.optString("url","")),ub=normalizeLink(b.optString("url",""));
        if(!ua.isEmpty()&&ua.equals(ub))return true;
        String modelA=a.optString("model",""),modelB=b.optString("model","");
        if(!modelA.isEmpty()&&modelA.equalsIgnoreCase(modelB)&&
            a.optString("shop").equalsIgnoreCase(b.optString("shop")))return true;
        Set<String> aa=tokens(a.optString("title")),bb=tokens(b.optString("title"));
        if(aa.size()<3||bb.size()<3)return false;
        if(!specs(aa).equals(specs(bb)))return false;
        Set<String> overlap=new HashSet<>(aa);overlap.retainAll(bb);
        Set<String> union=new HashSet<>(aa);union.addAll(bb);
        double jac=overlap.size()/(double)union.size();
        return jac>=0.88 && overlap.size()>=3;
    }
    private static JSONArray offers(JSONObject product) {
        JSONArray arr=product.optJSONArray("offers");
        if(arr!=null)return arr;
        arr=new JSONArray();
        arr.put(copyOffer(product));
        try{product.put("offers",arr);}catch(Exception ignored){}
        return arr;
    }
    private static JSONObject copyOffer(JSONObject product){
        JSONObject offer=new JSONObject();
        try{
            for(String field:Arrays.asList("shop","url","price","provider","condition")){
                if(product.has(field))offer.put(field,product.opt(field));
            }
        }catch(Exception ignored){}
        return offer;
    }
    private static boolean sameOffer(JSONObject left,JSONObject right){
        return left.optString("shop").equals(right.optString("shop")) &&
            normalizeLink(left.optString("url")).equals(normalizeLink(right.optString("url")));
    }
    private static void merge(JSONObject base,JSONObject incoming){
        JSONArray have=offers(base),newOnes=offers(incoming);
        for(int i=0;i<newOnes.length();i++){
            JSONObject candidate=newOnes.optJSONObject(i);
            if(candidate==null)continue;
            boolean duplicated=false;
            for(int j=0;j<have.length();j++){
                JSONObject existing=have.optJSONObject(j);
                if(existing!=null&&sameOffer(existing,candidate)){duplicated=true;break;}
            }
            if(!duplicated)have.put(candidate);
        }
        double first=base.optDouble("price",0),second=incoming.optDouble("price",0);
        if(second>0&&(first<=0||second<first)){
            for(String field:Arrays.asList("price","url","shop","provider","image","rating","reviews","condition")){
                try{base.put(field,incoming.opt(field));}catch(Exception ignored){}
            }
        }
        try{base.put("offer_count",have.length());}catch(Exception ignored){}
    }
    static List<JSONObject> group(Collection<JSONObject> source){
        List<JSONObject> merged=new ArrayList<>();
        for(JSONObject item:source){
            if(item==null||item.optString("title","").isEmpty())continue;
            JSONObject base=null;
            for(JSONObject candidate:merged)if(sameProduct(candidate,item)){base=candidate;break;}
            if(base==null){
                JSONObject clone;
                try{clone=new JSONObject(item.toString());}catch(Exception e){continue;}
                JSONArray a=offers(clone);
                try{clone.put("offer_count",a.length());}catch(Exception ignored){}
                merged.add(clone);
            }else merge(base,item);
        }
        merged.sort((a,b)->{
            double pa=a.optDouble("price",0),pb=b.optDouble("price",0);
            return Double.compare(pa>0?pa:Double.POSITIVE_INFINITY,pb>0?pb:Double.POSITIVE_INFINITY);
        });
        return merged;
    }
}
