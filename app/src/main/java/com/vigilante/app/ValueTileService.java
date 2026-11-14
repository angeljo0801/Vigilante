package com.vigilante.app;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class ValueTileService extends TileService {
    protected int slot(){return 0;}
    private boolean listening=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable redraw=()->{if(listening)render();};
    private final BroadcastReceiver updates=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){if(listening){main.removeCallbacks(redraw);main.post(redraw);}}
    };
    @Override public void onStartListening(){
        super.onStartListening();
        if(!listening){
            IntentFilter filter=new IntentFilter(QuickValue.ACTION_REFRESH);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,filter,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(updates,filter);
            listening=true;
        }
        render();
    }
    @Override public void onStopListening(){stopUpdates();super.onStopListening();}
    @Override public void onDestroy(){stopUpdates();super.onDestroy();}
    private void stopUpdates(){
        main.removeCallbacks(redraw);
        if(listening){listening=false;try{unregisterReceiver(updates);}catch(IllegalArgumentException ignored){}}
    }
    private void render(){
        Tile tile=getQsTile();if(tile==null)return;
        Watch watch=QuickValue.chosen(this,slot());
        String label=QuickValue.label(watch),sub=QuickValue.subtitle(watch);
        tile.setLabel(label);
        if(Build.VERSION.SDK_INT>=29)tile.setSubtitle(sub);
        tile.setContentDescription(watch==null?label:watch.name+" · "+label+" · "+sub);
        if(Build.VERSION.SDK_INT>=30)tile.setStateDescription(sub);
        boolean available=QuickValue.hasReading(watch)&&watch.enabled&&watch.lastError.isEmpty()&&!watch.actionRequired;
        tile.setState(available?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        tile.setIcon(icon(watch));tile.updateTile();
    }
    private Icon icon(Watch w){
        String value=QuickValue.iconText(w);
        Bitmap bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        String arrow=QuickValue.arrow(w).trim();
        int n=value.codePointCount(0,value.length());
        if(n<=6){
            if(!arrow.isEmpty()){p.setTextSize(32);canvas.drawText(arrow,56,28,p);drawFit(canvas,p,value,56,94,72);}
            else drawFit(canvas,p,value,56,80,80);
        }
        else{
            if(!arrow.isEmpty()){p.setTextSize(29);canvas.drawText(arrow,56,25,p);}
            int mid=value.offsetByCodePoints(0,Math.min(6,n));
            int space=value.lastIndexOf(' ',mid);
            if(space>0&&space<mid&&value.length()-space<=7)mid=space;
            drawFit(canvas,p,value.substring(0,mid).trim(),56,65,32);
            drawFit(canvas,p,value.substring(mid).trim(),56,101,32);
        }
        // Transfer an independent PNG icon rather than reusing a static resource.
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);
        byte[] png=bytes.toByteArray();
        return Icon.createWithData(png,0,png.length);
    }
    private void drawFit(Canvas canvas,Paint p,String value,float x,float y,float max){
        p.setTextSize(max);
        float width=p.measureText(value);if(width>104)p.setTextSize(max*104/width);
        canvas.drawText(value,x,y,p);
    }
    @Override public void onClick(){
        super.onClick();
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setAction("com.vigilante.app.OPEN_QUICK_VALUE");
        open.setData(Uri.parse("vigilante://quick/"+slot()));open.putExtra("quick_slot",slot());
        if(Build.VERSION.SDK_INT>=34){
            PendingIntent pending=PendingIntent.getActivity(this,slot(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        }else startActivityAndCollapse(open);
    }
}
