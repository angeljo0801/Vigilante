package com.vigilante.app;

import android.content.Context;
import android.content.ComponentName;
import android.service.quicksettings.TileService;
import java.util.List;
import java.util.Locale;

public final class QuickValue {
    private static final String PREF="vigilante_quick_tile";
    private QuickValue(){}
    static String selectedId(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("watch_id","");}
    static void select(Context c,String id){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("watch_id",id).apply();
        request(c);
    }
    static Watch chosen(Context c){return choose(WatchStore.all(c),selectedId(c));}
    static Watch choose(List<Watch> watches,String id){
        if(!id.isEmpty()){
            for(Watch w:watches)if(id.equals(w.id)&&!w.inboxDraft)return w;
            return null;
        }
        Watch dollar=null,other=null;
        for(Watch w:watches){
            if(!w.enabled||w.inboxDraft)continue;
            String name=w.name.toUpperCase(Locale.ROOT).replace(" ","");
            if(name.contains("USD/CUP"))dollar=w;
            if(!w.lastNumeric.isEmpty())other=w;
        }
        return dollar!=null?dollar:other;
    }
    static void request(Context c){
        try{TileService.requestListeningState(c,new ComponentName(c,ValueTileService.class));}
        catch(RuntimeException ignored){}
    }
    static String label(Watch w){
        if(w==null)return "Vigilante";
        if(w.lastNumeric.isEmpty())return "Sin lectura";
        return w.lastNumeric+arrow(w);
    }
    static String arrow(Watch w){
        String d=NumericTrend.direction(w);
        return d.startsWith("↑")?" ↑":d.startsWith("↓")?" ↓":d.startsWith("→")?" →":"";
    }
    static String subtitle(Watch w){
        if(w==null)return "Elegir vigilancia";
        if(!w.enabled)return "En pausa";
        if(w.actionRequired)return "Verificación requerida";
        if(!w.lastError.isEmpty())return "Dato sin actualizar";
        if(w.lastNumeric.isEmpty())return "Esperando lectura";
        return NumericTrend.direction(w);
    }
}
