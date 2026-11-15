package com.trendradar.app;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import android.util.LruCache;

final class RadarImages {
 private static final ExecutorService POOL=Executors.newFixedThreadPool(3);
 private static final LruCache<String,Bitmap> MEMORY=new LruCache<>(12*1024*1024){
  @Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
 };
 static int accent(String c){switch(c){case "Servicios":return 0xffe9b879;case "Empresas":case "IPO":return 0xff91b8fc;case "Innovaciones":return 0xffb6a0ef;case "Cuba":return 0xfff3a18b;default:return 0xff75dab3;}}
 static String symbol(String c){switch(c){case "Servicios":return "⚙";case "Empresas":return "▥";case "IPO":return "↗";case "Innovaciones":return "✦";case "Cuba":return "◎";default:return "◇";}}
 static Bitmap artwork(String cat){
  Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);int a=accent(cat);
  p.setShader(new LinearGradient(0,0,640,360,new int[]{0xff192c34,0xff111b27,0xff26353c},null,Shader.TileMode.CLAMP));c.drawRect(0,0,640,360,p);p.setShader(null);
  p.setColor(a);p.setAlpha(36);for(int i=0;i<5;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(480,140,52+i*45,p);}p.setStyle(Paint.Style.FILL);p.setAlpha(255);
  p.setColor(a);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(122);c.drawText(symbol(cat),320,215,p);
  p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(23);c.drawText(cat.toUpperCase(java.util.Locale.ROOT),320,286,p);return b;
 }
 static boolean publicHttps(String s){try{URL u=new URL(s);if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)return false;String h=u.getHost();if(h.isEmpty()||!h.contains(".")||h.endsWith(".local")||h.endsWith(".internal"))return false;for(InetAddress a:InetAddress.getAllByName(h))if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;return true;}catch(Exception e){return false;}}
 private static Bitmap download(String url){if(!publicHttps(url))return null;HttpURLConnection h=null;try{h=(HttpURLConnection)new URL(url).openConnection();h.setInstanceFollowRedirects(false);h.setConnectTimeout(4500);h.setReadTimeout(5000);h.setRequestProperty("User-Agent","TrendRadar/1.1");if(h.getResponseCode()!=200)return null;String type=h.getContentType();if(type==null||!type.startsWith("image/"))return null;try(InputStream in=h.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>4*1024*1024)return null;out.write(buf,0,n);}byte[] bytes=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<32||o.outHeight<32||o.outWidth>20000||o.outHeight>20000)return null;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1200||o.outHeight/o.inSampleSize>1200)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}}catch(Exception e){return null;}finally{if(h!=null)h.disconnect();}}
 static Bitmap photo(Context c,JSONObject item){
  String key=item.optString("id")+":"+item.optString("image_url");Bitmap cached=MEMORY.get(key);if(cached!=null)return cached;
  File dir=new File(c.getCacheDir(),"radar-images");dir.mkdirs();File file=new File(dir,java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()+".jpg");
  if(file.exists()){Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath());if(b!=null){MEMORY.put(key,b);return b;}}
  Bitmap b=download(item.optString("image_url"));JSONArray urls=item.optJSONArray("sources");
  if(b==null&&urls!=null){for(int i=0;i<Math.min(2,urls.length());i++){String url=urls.optString(i);if(!publicHttps(url))continue;try{org.jsoup.nodes.Document doc=Jsoup.connect(url).userAgent("TrendRadar/1.1").timeout(4500).maxBodySize(350000).followRedirects(false).get();Element e=doc.selectFirst("meta[property=og:image],meta[name=twitter:image]");if(e!=null){String src=e.absUrl("content");if(src.isEmpty())src=new URL(new URL(url),e.attr("content")).toString();b=download(src);if(b!=null)break;}}catch(Exception ignored){}}}
  if(b!=null){MEMORY.put(key,b);try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.JPEG,83,out);}catch(Exception ignored){}File[] all=dir.listFiles();if(all!=null&&all.length>160){java.util.Arrays.sort(all,java.util.Comparator.comparingLong(File::lastModified));for(int i=0;i<all.length-160;i++)all[i].delete();}}
  return b;
 }
 static void show(Context c,ImageView v,JSONObject item){v.setImageBitmap(artwork(item.optString("category")));String key=item.optString("id");v.setTag(key);Context app=c.getApplicationContext();POOL.execute(()->{Bitmap b=photo(app,item);if(b!=null)v.post(()->{if(key.equals(v.getTag()))v.setImageBitmap(b);});});}
}
