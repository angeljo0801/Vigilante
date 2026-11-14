package com.vigilante.app;
import android.content.Context;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class Engines {
    public static final String[] MODES={"AUTO","HTML","WEBVIEW","PLAYWRIGHT"};
    public static final String[] LABELS={"Automático (híbrido)","HTML directo (Jsoup)","Navegador local (WebView)","Playwright remoto"};
    private Engines(){}
    static class MissingData extends IOException { MissingData(String message){super(message);} }
    static String mode(Watch w){
        for(String m:MODES) if(m.equals(w.engine)) return m;
        return w.dynamic?"WEBVIEW":"HTML";
    }
    static String label(String mode){for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return LABELS[i];return mode;}
    static int index(String mode){for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return i;return 0;}
    static List<String> chain(String preferred,boolean remote){
        ArrayList<String> c=new ArrayList<>();
        if(!"WEBVIEW".equals(preferred)&&!"PLAYWRIGHT".equals(preferred))c.add("HTML");
        if(!"PLAYWRIGHT".equals(preferred))c.add("WEBVIEW");
        if(remote)c.add("PLAYWRIGHT");
        return c;
    }
    static WebScanner.Observation scan(Context c,Watch w)throws IOException{
        String mode=mode(w);
        if(!"AUTO".equals(mode)) return scanOne(c,w,mode);
        List<String> engines=chain(w.preferredEngine,RemoteBrowser.configured(c));
        if(engines.isEmpty())throw new IOException("Configura el puente Playwright para esta vigilancia.");
        MissingData missing=null;
        for(String engine:engines){
            try{return scanOne(c,w,engine);}
            catch(MissingData e){missing=e;} // never retry using another engine after access restrictions or network failures
        }
        throw missing==null?new IOException("No se encontraron datos"):missing;
    }
    private static WebScanner.Observation scanOne(Context c,Watch w,String engine)throws IOException{
        if("HTML".equals(engine))return WebScanner.scanHtml(w);
        Document doc="PLAYWRIGHT".equals(engine)?RemoteBrowser.render(c,w.url,w.renderWaitSeconds):RenderedPage.load(c,w.url,w.renderWaitSeconds);
        return WebScanner.observe(w,WebScanner.extract(doc,w));
    }
    public static final class Found{
        public final PageExplorer.Candidate candidate;
        public final String engine;
        Found(PageExplorer.Candidate c,String e){candidate=c;engine=e;}
    }
    private static List<PageExplorer.Candidate> discover(Context c,String url,int wait,String mode)throws IOException{
        if("HTML".equals(mode))return PageExplorer.inspect(url);
        return PageExplorer.discover("PLAYWRIGHT".equals(mode)?RemoteBrowser.render(c,url,wait):RenderedPage.load(c,url,wait));
    }
    public static List<Found> explore(Context c,String url,int wait,String mode)throws IOException{
        LinkedHashMap<String,Found> result=new LinkedHashMap<>();
        List<String> engines="AUTO".equals(mode)?chain("",RemoteBrowser.configured(c)):java.util.Collections.singletonList(mode);
        for(String engine:engines){
            List<PageExplorer.Candidate> items=discover(c,url,wait,engine);
            for(PageExplorer.Candidate item:items){
                String key=item.selector+"|"+item.attribute+"|"+item.regex;
                Found prior=result.get(key);
                if(prior==null || !prior.candidate.value.equals(item.value))result.put(key,new Found(item,engine));
            }
            // Playwright is only an explicit option or the last resort when local engines return no fields.
            if("AUTO".equals(mode)&&"WEBVIEW".equals(engine)&&!result.isEmpty())break;
            if("AUTO".equals(mode)&&"HTML".equals(engine)&&items.size()==1&&items.get(0).selector.isEmpty())break; // raw JSON/text
        }
        return new ArrayList<>(result.values());
    }
}
