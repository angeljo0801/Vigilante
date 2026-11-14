package com.vigilante.app;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
public final class TaskImport {
    public static final String TEMPLATE="VIGILANTE/1\nNOMBRE: Nombre de la búsqueda\nURL: https://example.com\nOBJETIVO: Dato exacto que quiero vigilar\nREGLA: CHANGES\nHORAS: 08:00, 18:00\nMOTOR: AUTO\nFIN";
    private static final Pattern URL=Pattern.compile("https?://[^\\s<>\"\\x60]+",Pattern.CASE_INSENSITIVE);
    private TaskImport(){}
    static boolean validUrl(String s){
        try{URI u=new URI(s);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null;}
        catch(Exception e){return false;}
    }
    public static Watch parse(String raw){
        if(raw==null||raw.trim().isEmpty())throw new IllegalArgumentException("Pega una configuración o un enlace.");
        if(raw.length()>20000)throw new IllegalArgumentException("Texto demasiado largo.");
        Watch w=new Watch();w.engine="AUTO";w.rule="CHANGES";
        String text=raw.trim();String[] lines=text.split("\\r?\\n");int start=-1;
        for(int i=0;i<lines.length;i++)if(lines[i].trim().equalsIgnoreCase("VIGILANTE/1")){start=i;break;}
        if(start<0){
            Matcher matcher=URL.matcher(text);
            if(matcher.find()){String url=matcher.group().replaceAll("[).,;\\]]+$","");if(validUrl(url))w.url=url;}
            w.objective=text.length()>2000?text.substring(0,2000):text;w.name="Búsqueda importada";return w;
        }
        Map<String,String> fields=new LinkedHashMap<>();
        for(int i=start+1;i<lines.length;i++){
            String line=lines[i].trim();
            if(line.equalsIgnoreCase("FIN")||(!line.isEmpty()&&line.charAt(0)==96))break;
            if(line.isEmpty())continue;
            int colon=line.indexOf(':');if(colon<=0)throw new IllegalArgumentException("Línea no válida: "+line);
            String key=line.substring(0,colon).trim().toUpperCase(Locale.ROOT);
            if(!Arrays.asList("NOMBRE","URL","OBJETIVO","REGLA","PALABRA","HORAS","INTERVALO","MOTOR","CSS","ATRIBUTO","REGEX","ESPERA").contains(key))throw new IllegalArgumentException("Campo desconocido: "+key);
            if(fields.put(key,line.substring(colon+1).trim())!=null)throw new IllegalArgumentException("Campo repetido: "+key);
        }
        w.name=fields.getOrDefault("NOMBRE","Búsqueda importada");w.url=fields.getOrDefault("URL","");
        if(!w.url.isEmpty()&&!validUrl(w.url))throw new IllegalArgumentException("URL HTTP/HTTPS no válida.");
        w.objective=fields.getOrDefault("OBJETIVO","");
        w.rule=fields.getOrDefault("REGLA","CHANGES").toUpperCase(Locale.ROOT);
        if(!Arrays.asList("CHANGES","ALWAYS","APPEARS","DISAPPEARS","ABOVE","BELOW").contains(w.rule))throw new IllegalArgumentException("Regla desconocida.");
        w.keyword=fields.getOrDefault("PALABRA","");w.selector=fields.getOrDefault("CSS","");w.attribute=fields.getOrDefault("ATRIBUTO","");w.regex=fields.getOrDefault("REGEX","");
        if(!w.regex.isEmpty())Pattern.compile(w.regex);
        w.times=Scheduler.normalizeTimes(fields.getOrDefault("HORAS",""));
        if(fields.containsKey("INTERVALO")){
            try{w.intervalMinutes=Integer.parseInt(fields.get("INTERVALO"));}catch(NumberFormatException e){throw new IllegalArgumentException("INTERVALO debe ser minutos.");}
            if(!Arrays.asList(15,30,60,120,360,720,1440).contains(w.intervalMinutes))throw new IllegalArgumentException("Intervalo permitido: 15,30,60,120,360,720,1440.");
        }
        w.engine=fields.getOrDefault("MOTOR","AUTO").toUpperCase(Locale.ROOT);
        if(!Arrays.asList("AUTO","HTML","WEBVIEW","PLAYWRIGHT").contains(w.engine))throw new IllegalArgumentException("Motor desconocido.");
        if(fields.containsKey("ESPERA")){
            try{w.renderWaitSeconds=Integer.parseInt(fields.get("ESPERA"));}catch(NumberFormatException e){throw new IllegalArgumentException("ESPERA debe ser segundos.");}
            if(!Arrays.asList(3,8,15,25).contains(w.renderWaitSeconds))throw new IllegalArgumentException("Espera permitida: 3,8,15,25.");
        }
        w.dynamic=!"HTML".equals(w.engine);return w;
    }
}
