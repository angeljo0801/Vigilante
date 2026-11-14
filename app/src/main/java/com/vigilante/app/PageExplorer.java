package com.vigilante.app;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Heuristic discovery of public HTML fields. Every proposed selector is verified
 * against the same document, using the same extraction semantics as WebScanner. */
public final class PageExplorer {
    private static final int LIMIT = 200;
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}\\d])([+-]?\\d+(?:[.,]\\d+)?)(?![\\p{L}\\d])");
    private static final Pattern PRICE=Pattern.compile("(?i)[$€£]|\\b(?:USD|CUP|EUR|precio|price|cost|importe)\\b");
    private static final Pattern STATUS=Pattern.compile("(?i)\\b(?:disponible|agotado|en stock|sin stock|available|unavailable|in stock|out of stock|sold out|availability|disponibilidad|entregado)\\b");
    private static final Pattern RATE=Pattern.compile("(?i)\\b(?:tasa|cambio|exchange rate|d[oó]lar|dollar|USD|CUP|EUR)\\b");
    private PageExplorer() {}
    public static final class Candidate {
        public final String category,label,value,selector,attribute,regex;
        Candidate(String c,String l,String v,String s,String a,String r) {
            category=c;label=l;value=v;selector=s;attribute=a;regex=r;
        }
    }
    public static List<Candidate> inspect(String url) throws IOException {
        Connection.Response response=Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; Vigilante/2.1; Android)")
            .timeout(15000).maxBodySize(2000000).followRedirects(true)
            .ignoreContentType(true).execute();
        String type=response.contentType();
        if(type!=null && !type.toLowerCase(Locale.ROOT).contains("html")) {
            String body=response.body().replaceAll("\\s+"," ").trim();
            if(body.isEmpty()) throw new IOException("Respuesta vacía");
            List<Candidate> list=new ArrayList<>();
            list.add(new Candidate("Contenido","Respuesta completa",preview(body),"","",""));
            return list;
        }
        return discover(response.parse());
    }
    static List<Candidate> discover(Document doc) {
        List<Candidate> out=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        // Structured semantic fields, then compact visible values, then general content.
        for(Element e:doc.select("meta[content], [itemprop], [data-price], [data-availability], time[datetime], [role=status]")) {
            String hint=e.attr("itemprop")+" "+e.attr("property")+" "+e.attr("name")+" "+e.className()+" "+e.id();
            String attr=e.hasAttr("content")?"content":e.hasAttr("data-price")?"data-price":e.hasAttr("data-availability")?"data-availability":e.hasAttr("datetime")?"datetime":e.hasAttr("href")&&e.tagName().equals("link")?"href":"";
            String value=value(e,attr);
            if(e.tagName().equals("meta")&&!hint.matches("(?i).*(price|availability|title|description|published|modified|date|currency|rating|stock|exchange).*")) continue;
            if(value.isEmpty()||value.length()>600) continue;
            String cat=category(hint+" "+value,e);
            add(doc,out,seen,e,attr,cat,hint.trim().isEmpty()?cat:hint.trim(),value);
        }
        for(Element e:doc.select("body *")) {
            if(out.size()>=100) break;
            if(ignored(e)) continue;
            String own=e.ownText().trim(), all=e.text().trim();
            if(all.isEmpty()||all.length()>180) continue;
            String context=e.id()+" "+e.className()+" "+e.attr("aria-label")+" "+all;
            String cat=category(context,e);
            if(cat.equals("Texto")||cat.equals("Enlace")||cat.equals("Título")) continue;
            if(own.isEmpty() && e.childrenSize()>1 && all.length()>100) continue;
            add(doc,out,seen,e,"",cat,cat,all);
        }
        for(Element e:doc.select("title, h1, h2, h3, h4, time, tr, dt, dd, p, a[href], button, [aria-label], td, li, span, div, strong, label")) {
            if(out.size()>=LIMIT) break;
            if(ignored(e)) continue;
            String val=e.text().trim();
            String attr="";
            if(val.isEmpty()&&e.hasAttr("aria-label")) {attr="aria-label";val=e.attr(attr);}
            if(val.isEmpty()||val.length()>800) continue;
            if((e.tagName().equals("span")||e.tagName().equals("div")) && e.ownText().trim().isEmpty()) continue;
            String cat=category(e.className()+" "+e.id()+" "+val,e);
            add(doc,out,seen,e,attr,cat,cat,val);
        }
        return out;
    }
    private static boolean ignored(Element e) {
        return e.closest("script, style, noscript, svg, template, [hidden], [aria-hidden=true]")!=null;
    }
    private static String category(String s,Element e) {
        String lower=s.toLowerCase(Locale.ROOT);
        if(STATUS.matcher(s).find()||lower.contains("availability")||e.hasAttr("data-availability")) return "Disponibilidad / estado";
        if(RATE.matcher(s).find() && NUMBER.matcher(s).find() && !lower.contains("price")&&!lower.contains("precio")) return "Moneda / tasa";
        if(PRICE.matcher(s).find()||lower.contains("price")||e.hasAttr("data-price")) return "Precio / importe";
        if(e.tagName().equals("time")||lower.contains("published_time")||lower.contains("modified_time")||lower.contains("datepublished")) return "Fecha / hora";
        if(e.tagName().matches("h[1-6]|title")||lower.contains("og:title")) return "Título";
        if(e.tagName().equals("tr")||e.tagName().equals("dt")||e.tagName().equals("dd")) return "Tabla / dato";
        if(e.tagName().equals("a")) return "Enlace";
        return "Texto";
    }
    private static String value(Element e,String attr) {
        return (attr.isEmpty()?e.text():e.attr(attr)).replaceAll("\\s+"," ").trim();
    }
    private static void add(Document doc,List<Candidate> out,Set<String> seen,Element e,String attr,String cat,String label,String v) {
        if(out.size()>=LIMIT||v.isEmpty())return;
        String selector=e.cssSelector();
        if(selector.isEmpty())return;
        try {
            Elements matches=doc.select(selector);
            if(matches.size()!=1||matches.first()!=e) return;
        } catch(Exception ex){return;}
        String val=value(e,attr);
        if(val.isEmpty())return;
        String identity=selector+"|"+attr;
        if(!seen.add(identity))return;
        String friendly=label.trim();
        if(friendly.isEmpty()||friendly.equals(cat)) friendly=cat+" · "+preview(val,45);
        else friendly=preview(friendly,65);
        out.add(new Candidate(cat,friendly,preview(val),selector,attr,""));
        // Offer a separate numeric extraction when the field contains exactly one number.
        if(out.size()<LIMIT && (cat.equals("Precio / importe")||cat.equals("Moneda / tasa"))) {
            Matcher matcher=NUMBER.matcher(val);
            if(matcher.find()){
                String n=matcher.group(1);
                if(!matcher.find() && seen.add("Número|"+selector+"|"+attr)) {
                    out.add(new Candidate("Valor numérico",friendly+" (número)",n,selector,attr,NUMBER.pattern()));
                }
            }
        }
        if(out.size()<LIMIT && e.tagName().equals("a") && e.hasAttr("href") && seen.add("URL|"+selector)) {
            out.add(new Candidate("Dirección de enlace",friendly+" (URL)",preview(e.attr("href")),selector,"href",""));
        }
    }
    private static String preview(String s){return preview(s,260);}
    private static String preview(String s,int n){return s.length()>n?s.substring(0,n)+"…":s;}
}
