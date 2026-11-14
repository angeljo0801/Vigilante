package com.vigilante.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Real Android Quick Settings tile. SystemUI controls its placement and size. */
public final class ValueTileService extends TileService {
    @Override public void onStartListening(){super.onStartListening();render();}
    @Override public void onTileAdded(){super.onTileAdded();QuickValue.request(this);}
    private void render(){
        Tile tile=getQsTile();if(tile==null)return;
        Watch watch=QuickValue.chosen(this);
        String label=QuickValue.label(watch),sub=QuickValue.subtitle(watch);
        tile.setLabel(label);
        if(Build.VERSION.SDK_INT>=29)tile.setSubtitle(sub);
        String description=watch==null?label:watch.name+" · "+label+" · "+sub;
        tile.setContentDescription(description);
        if(Build.VERSION.SDK_INT>=30)tile.setStateDescription(sub);
        boolean available=watch!=null&&watch.enabled&&!watch.lastNumeric.isEmpty()&&watch.lastError.isEmpty()&&!watch.actionRequired;
        tile.setState(available?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        tile.setIcon(icon(watch));
        tile.updateTile();
    }
    private Icon icon(Watch w){
        if(w==null||w.lastNumeric.isEmpty())return Icon.createWithResource(this,R.drawable.ic_qs_value);
        NumericTrend n=NumericTrend.storedValue(w.lastNumeric);
        if(n==null)return Icon.createWithResource(this,R.drawable.ic_qs_value);
        String value=NumericTrend.fmt(n.value);
        if(value.length()>5)return Icon.createWithResource(this,R.drawable.ic_qs_value);
        // A monochrome number inside the icon also gives compact icon-only panels a hint.
        Bitmap bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        String arrow=(!w.enabled||!w.lastError.isEmpty()||w.actionRequired)?"!":QuickValue.arrow(w).trim();
        if(arrow.isEmpty())arrow="·";
        p.setTextSize(35);canvas.drawText(arrow,48,31,p);
        p.setTextSize(value.length()<=3?47:value.length()==4?36:29);
        canvas.drawText(value,48,80,p);
        return Icon.createWithBitmap(bitmap);
    }
    @Override public void onClick(){
        super.onClick();
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setAction("com.vigilante.app.OPEN_QUICK_VALUE");
        if(Build.VERSION.SDK_INT>=34){
            PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        }else startActivityAndCollapse(open);
    }
}
