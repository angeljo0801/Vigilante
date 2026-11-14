package com.vigilante.app;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class RemoteBrowser {
    private RemoteBrowser(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("vigilante_bridge",Context.MODE_PRIVATE);}
    public static String endpoint(Context c){return prefs(c).getString("endpoint","");}
    public static String token(Context c){return prefs(c).getString("token","");}
    public static boolean configured(Context c){return !endpoint(c).isEmpty()&&!token(c).isEmpty();}
    public static void configure(Context c,String endpoint,String token)throws IOException{
        String u=endpoint.trim().replaceAll("/+$","");
        if(u.isEmpty()&&token.isEmpty()){prefs(c).edit().clear().apply();return;}
        URL parsed=new URL(u);
        if(!"https".equals(parsed.getProtocol())||parsed.getUserInfo()!=null||parsed.getHost().isEmpty()||parsed.getQuery()!=null||parsed.getRef()!=null)
            throw new IOException("Usa una URL HTTPS del puente (por ejemplo https://tu-pc...ts.net)");
        if(token.trim().length()<32)throw new IOException("El token debe tener al menos 32 caracteres");
        prefs(c).edit().putString("endpoint",u).putString("token",token.trim()).apply();
    }
    public static Document render(Context c,String url,int wait)throws IOException{
        if(!configured(c))throw new IOException("Configura el puente Playwright en el dashboard.");
        try{
            JSONObject input=new JSONObject();input.put("url",url);input.put("wait",wait);
            JSONObject result=request(c,"/render",input);
            if(result.optBoolean("actionRequired"))throw new ActionRequired(result.optString("error","Acción requerida por el sitio"));
            if(!result.optBoolean("ok"))throw new IOException(result.optString("error","Error del puente"));
            String html=result.optString("html");
            if(html.isEmpty())throw new IOException("El puente no devolvió contenido");
            Document doc=Jsoup.parse(html,result.optString("url",url));
            ActionRequired.checkDocument(doc);
            return doc;
        }catch(IOException ex){throw ex;}catch(Exception ex){throw new IOException("Error de Playwright: "+ex.getMessage(),ex);}
    }
    public static void test(Context c)throws IOException{
        try{if(!request(c,"/health",null).optBoolean("ok"))throw new IOException("Puente no disponible");}
        catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
    }
    private static JSONObject request(Context c,String path,JSONObject body)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(endpoint(c)+path).openConnection();
        try{
            conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(10000);conn.setReadTimeout(90000);
            conn.setRequestProperty("Authorization","Bearer "+token(c));conn.setRequestProperty("Accept","application/json");
            if(body!=null){conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();
            if(status==401||status==403)throw new IOException("El puente rechazó las credenciales o la conexión");
            if(status>=300&&status<400)throw new IOException("El puente no debe redirigir");
            InputStream in=status>=400?conn.getErrorStream():conn.getInputStream();
            if(in==null)throw new IOException("Respuesta del puente HTTP "+status);
            try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n,total=0;
                while((n=stream.read(buf))!=-1){total+=n;if(total>16000000)throw new IOException("Respuesta del puente demasiado grande");out.write(buf,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
}
