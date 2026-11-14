package com.vigilante.app;
import android.app.PendingIntent;
import android.content.Intent;
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
    @Override public void onStartListening(){super.onStartListening();render();}
    @Override public void onTileAdded(){super.onTileAdded();QuickValue.request(this);}
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
        if(value.isEmpty())return Icon.createWithResource(this,R.drawable.ic_qs_value);
        Bitmap bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        String arrow=QuickValue.arrow(w).trim();if(arrow.isEmpty())arrow="·";
        p.setTextSize(31);canvas.drawText(arrow,56,27,p);
        int n=value.codePointCount(0,value.length());
        if(n<=6){drawFit(canvas,p,value,56,83,52);}
        else{
            int mid=value.offsetByCodePoints(0,Math.min(6,n));
            int space=value.lastIndexOf(' ',mid);
            if(space>0&&space<mid&&value.length()-space<=7)mid=space;
            drawFit(canvas,p,value.substring(0,mid).trim(),56,65,32);
            drawFit(canvas,p,value.substring(mid).trim(),56,101,32);
        }
        return Icon.createWithBitmap(bitmap);
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
