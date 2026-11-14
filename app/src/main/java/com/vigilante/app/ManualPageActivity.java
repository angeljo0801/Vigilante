package com.vigilante.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.CookieManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;

/** User-controlled navigation only. No extraction, challenge solving, or native JavaScript bridge. */
public final class ManualPageActivity extends Activity {
    private WebView web;
    private static boolean http(Uri u){
        return u!=null&&u.getHost()!=null&&("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()));
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        Uri url=getIntent().getData();
        if(!http(url)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TextView message=new TextView(this);
        message.setPadding(18,14,18,14);
        message.setText("Revisa el sitio manualmente. No introduzcas datos si no confías en la dirección. Si el sitio restringe el acceso automatizado, respeta esa restricción.");
        root.addView(message);
        TextView address=new TextView(this);address.setText(url.toString());address.setPadding(18,4,18,4);root.addView(address);
        Button close=new Button(this);close.setText("Volver a Vigilante");close.setOnClickListener(v->finish());root.addView(close);
        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setGeolocationEnabled(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(android.webkit.PermissionRequest r){r.deny();}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback cb){cb.invoke(origin,false,false);}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return !http(req.getUrl());}
            @Override public void onPageFinished(WebView v,String u){address.setText(u);}
            @Override public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail d){finish();return true;}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);web.loadUrl(url.toString());
    }
    @Override protected void onDestroy(){
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        super.onDestroy();
    }
}
