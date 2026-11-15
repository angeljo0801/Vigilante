package com.vigilante.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.work.*;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.*;

public final class ChatInbox {
    private static final String ALIAS="vigilante_chat_inbox_v1", PREF="vigilante_chat_inbox";
    private static final String ROOT="https://raw.githubusercontent.com/angeljo0801/Vigilante/inbox/inbox/";
    private ChatInbox(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean editsEnabled(Context c){return prefs(c).getBoolean("remote_edits",false);}
    public static void allowEdits(Context c,boolean value){prefs(c).edit().putBoolean("remote_edits",value).apply();}
    public static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    public static void enable(Context c,boolean value){
        prefs(c).edit().putBoolean("enabled",value).apply();
        WorkManager wm=WorkManager.getInstance(c);
        if(!value){wm.cancelUniqueWork("chat_inbox_periodic");wm.cancelUniqueWork("chat_inbox_now");return;}
        Constraints net=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        wm.enqueueUniquePeriodicWork("chat_inbox_periodic",ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(ChatInboxWorker.class,15,TimeUnit.MINUTES).setConstraints(net).build());
        requestNow(c);
    }
    public static void requestNow(Context c){
        if(!enabled(c))return;
        WorkManager.getInstance(c).enqueueUniqueWork("chat_inbox_now",ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(ChatInboxWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
    static synchronized KeyStore keyStore()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build());
            g.generateKeyPair();ks.load(null);
        }
        return ks;
    }
    public static String pairingCode()throws Exception{
        byte[] key=keyStore().getCertificate(ALIAS).getPublicKey().getEncoded();
        return "VIGILANTE-PAIR/1 "+Base64.encodeToString(key,Base64.NO_WRAP);
    }
    private static String recipient(KeyStore ks)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(ks.getCertificate(ALIAS).getPublicKey().getEncoded());
        StringBuilder s=new StringBuilder();
        for(byte x:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",x&255));
        return s.toString();
    }
    static byte[] decode(JSONObject j,String name)throws Exception{return Base64.decode(j.getString(name),Base64.DEFAULT);}
    private static String decrypt(KeyStore ks,JSONObject entry,String id,String recipient)throws Exception{
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE,ks.getKey(ALIAS,null),new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
        byte[] key=rsa.doFinal(decode(entry,"key"));
        if(key.length!=32)throw new GeneralSecurityException("Clave inválida");
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,decode(entry,"iv")));
        aes.updateAAD(("VIGILANTE/1:"+recipient+":"+id).getBytes(StandardCharsets.UTF_8));
        return new String(aes.doFinal(decode(entry,"data")),StandardCharsets.UTF_8);
    }
    public static synchronized String sync(Context c)throws Exception{
        if(!enabled(c))return "Recepción desactivada.";
        KeyStore ks=keyStore();String recipient=recipient(ks);
        HttpURLConnection con=(HttpURLConnection)new URL(ROOT+recipient+".json?ts="+System.currentTimeMillis()).openConnection();
        con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setInstanceFollowRedirects(false);con.setRequestProperty("Cache-Control","no-cache");
        int added=0,drafts=0,invalid=0,edited=0;
        try{
            int status=con.getResponseCode();
            if(status==404)return status(c,"Sin envíos nuevos.");
            if(status!=200)throw new IOException("Bandeja HTTP "+status);
            String raw;
            try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n,total=0;
                while((n=in.read(buf))!=-1){total+=n;if(total>1000000)throw new IOException("Bandeja demasiado grande");out.write(buf,0,n);}
                raw=out.toString("UTF-8");
            }
            JSONArray rows=new JSONArray(raw);
            if(rows.length()>100)throw new IOException("Demasiados mensajes");
            for(int i=0;i<rows.length()&&enabled(c);i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                String id=row.optString("id");
                if(!id.matches("[a-f0-9-]{36}")||!recipient.equals(row.optString("recipient"))||row.optInt("v")!=1)continue;
                if(prefs(c).getBoolean("seen:"+id,false))continue;
                try{
                    String plain=decrypt(ks,row,id,recipient);
                    if(plain.trim().startsWith("VIGILANTE-EDIT/1")){
                        if(!editsEnabled(c))continue;
                        RemoteEdit.apply(c,plain,id);
                        prefs(c).edit().putBoolean("seen:"+id,true).commit();edited++;continue;
                    }
                    Watch watch=TaskImport.parse(plain);watch.id="inbox-"+id;
                    if(WatchStore.find(c,watch.id)!=null){prefs(c).edit().putBoolean("seen:"+id,true).commit();continue;}
                    boolean ready=TaskImport.readyForAuto(watch,plain)&&(!"PLAYWRIGHT".equals(watch.engine)||RemoteBrowser.configured(c));
                    watch.inboxDraft=!ready;watch.enabled=ready;
                    if(!enabled(c))break;
                    WatchStore.upsert(c,watch);
                    if(ready){Scheduler.schedule(c,watch);added++;}else{drafts++;}
                    prefs(c).edit().putBoolean("seen:"+id,true).commit();
                    AlarmNotifier.notifyInbox(c,watch,ready);
                }catch(Exception bad){invalid++;}
            }
        }finally{con.disconnect();}
        return status(c,"Nuevas alarmas: "+added+" · Modificaciones: "+edited+" · Borradores: "+drafts+(invalid>0?" · Envíos no válidos: "+invalid:""));
    }
    static String status(Context c,String s){prefs(c).edit().putString("status",s).putLong("last",System.currentTimeMillis()).apply();return s;}
    public static String lastStatus(Context c){return prefs(c).getString("status","Sin sincronizar todavía.");}
}
