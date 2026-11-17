package com.vigilante.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.*;

final class BusinessImages {
 private static final ExecutorService POOL=Executors.newFixedThreadPool(3);
 private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(16*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 static boolean publicHttps(String s){
  try{URL u=new URL(s);if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
  String h=u.getHost();if(h.isEmpty()||!h.contains(".")||h.endsWith(".local")||h.endsWith(".internal"))return false;
  for(InetAddress addr:InetAddress.getAllByName(h)){if(addr.isAnyLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()||addr.isSiteLocalAddress()||addr.isMulticastAddress())return false;byte[] bytes=addr.getAddress();if(bytes.length==16&&((bytes[0]&0xfe)==0xfc))return false;if(bytes.length==4&&((bytes[0]&255)==100)&&((bytes[1]&0xc0)==64))return false;}
  return true;}catch(Exception e){return false;}
 }
 private static Bitmap download(String url){
  HttpURLConnection h=null;
  try{for(int redirect=0;redirect<4;redirect++){if(!publicHttps(url))return null;
   h=(HttpURLConnection)new URL(url).openConnection();h.setInstanceFollowRedirects(false);h.setConnectTimeout(5000);h.setReadTimeout(7000);h.setRequestProperty("User-Agent","Vigilante/3.9.3");h.setRequestProperty("Accept","image/*");
   int status=h.getResponseCode();if(status>=300&&status<400){String location=h.getHeaderField("Location");if(location==null)return null;url=new URL(new URL(url),location).toString();h.disconnect();h=null;continue;}
   if(status!=200)return null;String type=h.getContentType();if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))return null;
   try(InputStream in=h.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>5*1024*1024)return null;out.write(buf,0,n);}byte[] bytes=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<32||o.outHeight<32||o.outWidth>16000||o.outHeight>16000)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1400||o.outHeight/o.inSampleSize>1400)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}
  }}catch(Exception ignored){}finally{if(h!=null)h.disconnect();}return null;
 }
 private static Bitmap photo(Context c,String url){
  Bitmap b=MEMORY.get(url);if(b!=null)return b;
  File dir=new File(c.getCacheDir(),"business-images");dir.mkdirs();File file=new File(dir,UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8))+".jpg");
  if(file.exists()){b=BitmapFactory.decodeFile(file.getAbsolutePath());if(b!=null){file.setLastModified(System.currentTimeMillis());MEMORY.put(url,b);return b;}}
  b=download(url);if(b!=null){MEMORY.put(url,b);try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.JPEG,85,out);}catch(Exception ignored){}File[] all=dir.listFiles();if(all!=null&&all.length>180){Arrays.sort(all,Comparator.comparingLong(File::lastModified));for(int i=0;i<all.length-180;i++)all[i].delete();}}return b;
 }
 static void show(Context c,ImageView view,JSONObject item,TextView caption){
  String url=item.optString("imageUrl","");String label=item.optString("imageCaption","Imagen de referencia");
  view.setTag(Boolean.FALSE);view.setImageResource(android.R.drawable.ic_menu_gallery);view.setColorFilter(0xffa7b9d5);
  if(url.isEmpty()){caption.setText("Imagen de referencia pendiente");return;}
  caption.setText("Cargando imagen de referencia…");Context app=c.getApplicationContext();
  POOL.execute(()->{Bitmap b=photo(app,url);view.post(()->{if(b!=null){view.clearColorFilter();view.setImageBitmap(b);view.setTag(Boolean.TRUE);caption.setText(label);}else caption.setText("Imagen no disponible · "+label);});});
 }
}
