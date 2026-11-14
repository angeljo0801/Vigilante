package com.vigilante.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Offscreen browser for public sites, no native JS bridge or file access. */
public final class RenderedPage {
    private RenderedPage() {}
    private static boolean http(String url) {
        Uri u=Uri.parse(url);
        return u.getHost()!=null && ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()));
    }
    public static Document load(Context context,String url,int waitSeconds) throws IOException {
        if(Looper.myLooper()==Looper.getMainLooper()) throw new IOException("El navegador debe iniciarse desde una tarea de fondo.");
        if(!http(url)) throw new IOException("URL HTTP/HTTPS no válida");
        int wait=Math.max(3,Math.min(25,waitSeconds));
        Session session=new Session(context.getApplicationContext(),url,wait);
        session.handler.post(session::start);
        try {
            if(!session.latch.await(65,TimeUnit.SECONDS)) {
                session.handler.post(()->session.finish(null,"Tiempo de espera del navegador agotado"));
                throw new IOException("Tiempo de espera del navegador agotado");
            }
        } catch(InterruptedException e) {
            session.handler.post(()->session.finish(null,"Consulta cancelada"));
            Thread.currentThread().interrupt();
            throw new IOException("Consulta cancelada",e);
        }
        if(session.error.get()!=null) {
            if(session.actionRequired.get()) throw new ActionRequired(session.error.get());
            throw new IOException(session.error.get());
        }
        String html=session.html.get();
        if(html==null||html.trim().isEmpty()) throw new IOException("El navegador no encontró contenido");
        Document doc=Jsoup.parse(html,session.finalUrl.get()==null?url:session.finalUrl.get());
        ActionRequired.checkDocument(doc);
        return doc;
    }
    private static final class Session {
        final Context context;
        final String url;
        final int wait;
        final Handler handler=new Handler(Looper.getMainLooper());
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicBoolean done=new AtomicBoolean();
        final AtomicBoolean actionRequired=new AtomicBoolean();
        final AtomicReference<String> html=new AtomicReference<>(),error=new AtomicReference<>(),finalUrl=new AtomicReference<>();
        WebView view;
        final Runnable capture=this::capture;
        final Runnable timeout=()->finish(null,"El sitio no terminó de cargar en el tiempo permitido");
        Session(Context c,String u,int w){context=c;url=u;wait=w;}
        void start(){
            if(done.get())return;
            try {
                view=new WebView(context);
                WebSettings s=view.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(false);
                s.setAllowFileAccessFromFileURLs(false);
                s.setAllowUniversalAccessFromFileURLs(false);
                s.setJavaScriptCanOpenWindowsAutomatically(false);
                s.setSupportMultipleWindows(false);
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                s.setCacheMode(WebSettings.LOAD_NO_CACHE);
                s.setGeolocationEnabled(false);
                CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
                view.setWebChromeClient(new WebChromeClient(){
                    @Override public void onPermissionRequest(android.webkit.PermissionRequest request){request.deny();}
                    @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback callback){callback.invoke(origin,false,false);}
                });
                view.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                        return !http(req.getUrl().toString());
                    }
                    @Override public void onPageStarted(WebView v,String u,Bitmap favicon){handler.removeCallbacks(capture);}
                    @Override public void onPageFinished(WebView v,String u){
                        if(done.get()||!http(u))return;
                        handler.removeCallbacks(capture);
                        handler.postDelayed(capture,wait*1000L);
                    }
                    @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){
                        if(req.isForMainFrame())finish(null,"Error de navegación: "+err.getDescription());
                    }
                    @Override public void onReceivedHttpError(WebView v,WebResourceRequest req,WebResourceResponse res){
                        if(req.isForMainFrame()){
                            try { ActionRequired.checkStatus(res.getStatusCode()); }
                            catch(ActionRequired a){ actionRequired.set(true);finish(null,a.getMessage());return; }
                            finish(null,"El sitio devolvió HTTP "+res.getStatusCode());
                        }
                    }
                    @Override public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail d){
                        finish(null,"El motor del navegador se cerró");return true;
                    }
                });
                // Layout supplies a viewport for responsive sites even without an Activity.
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(1920,android.view.View.MeasureSpec.EXACTLY));
                view.layout(0,0,1080,1920);
                handler.postDelayed(timeout,60000);
                view.loadUrl(url);
            } catch(Exception e){finish(null,"No se pudo iniciar el navegador: "+e.getMessage());}
        }
        void capture(){
            if(done.get()||view==null)return;
            try {
                finalUrl.set(view.getUrl());
                view.evaluateJavascript("(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()",raw->{
                    if(done.get())return;
                    try {
                        Object value=new JSONTokener(raw==null?"null":raw).nextValue();
                        if(!(value instanceof String))throw new IOException("Respuesta del navegador vacía");
                        String result=(String)value;
                        if(result.length()>4000000)throw new IOException("Página demasiado grande para analizar (4 MB de texto)");
                        if(result.trim().isEmpty())throw new IOException("Página vacía");
                        finish(result,null);
                    } catch(Exception e){finish(null,"No se pudo leer el DOM: "+e.getMessage());}
                });
            } catch(Exception e){finish(null,"No se pudo extraer la página: "+e.getMessage());}
        }
        void finish(String data,String failure){
            if(!done.compareAndSet(false,true))return;
            handler.removeCallbacks(capture);handler.removeCallbacks(timeout);
            html.set(data);error.set(failure);
            try{
                if(view!=null){view.stopLoading();view.setWebViewClient(new WebViewClient());view.destroy();view=null;}
            }catch(Exception ignored){}finally{latch.countDown();}
        }
    }
}
