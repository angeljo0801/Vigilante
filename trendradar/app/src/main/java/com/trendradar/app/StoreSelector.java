package com.trendradar.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Independent, accessible multiple-store checkboxes with white empty circles. */
final class StoreSelector {
    private static final int WHITE=0xfff5faf7, GREEN=0xff75dab3, BG=0xff1b2933;
    private final Context context;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private final LinearLayout root;
    private final TextView count;
    private final Map<String,Tile> tiles=new LinkedHashMap<>();
    private Runnable onChange;
    private static final class Tile {
        final LinearLayout container;
        final TextView icon;
        final TextView text;
        Tile(LinearLayout c,TextView i,TextView t){container=c;icon=i;text=t;}
    }
    StoreSelector(Context context) {
        this.context=context;
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(12);
        root.setPadding(pad,pad,pad,pad);
        root.setBackground(bg(BG,16,0));
        TextView title=label("Elige las tiendas que deseas buscar",16,WHITE,true);
        root.addView(title);
        count=label("Ninguna tienda seleccionada",12,0xffb7c9c4,false);
        root.addView(count);
        String[] stores=FinderSearchV2.STORES;
        for(int j=1;j<stores.length;j+=2){
            LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(row);
            for(int k=j;k<Math.min(j+2,stores.length);k++){
                final String name=stores[k];
                LinearLayout tile=new LinearLayout(context);tile.setOrientation(LinearLayout.HORIZONTAL);
                tile.setGravity(android.view.Gravity.CENTER_VERTICAL);
                tile.setPadding(dp(8),dp(10),dp(4),dp(10));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(53),1);
                p.setMargins(dp(2),dp(4),dp(2),0);
                row.addView(tile,p);
                TextView icon=label("○",28,WHITE,true);
                icon.setPadding(0,0,dp(8),0);
                tile.addView(icon,new LinearLayout.LayoutParams(dp(32),-2));
                TextView text=label(name,14,WHITE,false);
                text.setMaxLines(1);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(text);
                tile.setClickable(true);
                tile.setFocusable(true);
                tile.setOnClickListener(v->toggle(name));
                tiles.put(name,new Tile(tile,icon,text));
            }
        }
        LinearLayout actions=new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);root.addView(actions);
        Button all=new Button(context);all.setText("Marcar todas");all.setAllCaps(false);
        Button none=new Button(context);none.setText("Limpiar");none.setAllCaps(false);
        actions.addView(all,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(none,new LinearLayout.LayoutParams(0,dp(48),1));
        all.setOnClickListener(v->{
            selected.clear();for(int i=1;i<stores.length;i++)selected.add(stores[i]);refresh();
        });
        none.setOnClickListener(v->{selected.clear();refresh();});
        refresh();
    }
    void changed(Runnable callback){onChange=callback;}
    View view(){return root;}
    List<String> getSelected(){return new ArrayList<>(selected);}
    void setSelected(Collection<String> wanted){
        selected.clear();
        if(wanted!=null)for(int i=1;i<FinderSearchV2.STORES.length;i++)
            if(wanted.contains(FinderSearchV2.STORES[i]))selected.add(FinderSearchV2.STORES[i]);
        refresh();
    }
    private void toggle(String s){
        if(selected.contains(s))selected.remove(s);else selected.add(s);
        refresh();
    }
    private void refresh(){
        count.setText(selected.size()==0?"Ninguna tienda seleccionada · marca al menos una":
            selected.size()+" tienda(s) seleccionada(s) · hasta "+selected.size()+" consulta(s) nuevas");
        for(Map.Entry<String,Tile> e:tiles.entrySet()){
            boolean marked=selected.contains(e.getKey());
            Tile t=e.getValue();
            t.icon.setText(marked?"●":"○");
            t.icon.setTextColor(marked?GREEN:WHITE);
            t.text.setTextColor(marked?GREEN:WHITE);
            t.container.setBackground(bg(marked?0xff2b5046:0xff23313b,11,marked?GREEN:0xffb9cbca));
            t.container.setContentDescription(e.getKey()+(marked?", seleccionada":", no seleccionada")+". Toca para cambiar.");
            t.container.setSelected(marked);
        }
        if(onChange!=null)onChange.run();
    }
    private int dp(int n){return (int)(context.getResources().getDisplayMetrics().density*n+.5f);}
    private static GradientDrawable bg(int fill,int radius,int stroke){
        GradientDrawable b=new GradientDrawable();
        b.setColor(fill);b.setCornerRadius(radius*3); // Android pixel approximation, density via parent padding
        if(stroke!=0)b.setStroke(1,stroke);
        return b;
    }
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(context);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
}