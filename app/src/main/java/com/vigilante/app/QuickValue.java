package com.vigilante.app;

import android.content.Context;
import android.content.ComponentName;
import android.service.quicksettings.TileService;
import java.util.List;

public final class QuickValue {
    private static final String PREF="vigilante_quick_tile";
    public static final int SLOTS=6;
    private QuickValue(){}
    private static String key(int slot){return slot==0?"watch_id":"watch_id_"+slot;}
    static String selectedId(Context c,int slot){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(key(slot),"");}
    static String selectedId(Context c){return selectedId(c,0);}
    static void select(Context c,int slot,String id){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(key(slot),id).apply();
        request(c);
    }
    static void select(Context c,String id){select(c,0,id);}
    static Watch chosen(Context c,int slot){
        String id=selectedId(c,slot);
        return slot>0&&id.isEmpty()?null:choose(WatchStore.all(c),id);
    }
    static Watch choose(List<Watch> watches,String id){
        if(!id.isEmpty()){
            for(Watch w:watches)if(id.equals(w.id)&&!w.inboxDraft)return w;
            return null;
        }
        Watch last=null,withReading=null;
        for(Watch w:watches){
            if(!w.enabled||w.inboxDraft)continue;
            last=w;
            if(hasReading(w)&&(withReading==null||w.lastChecked>=withReading.lastChecked))withReading=w;
        }
        return withReading!=null?withReading:last;
    }
    static Class<? extends TileService> service(int slot){
        switch(slot){
            case 1:return ValueTileService2.class;
            case 2:return ValueTileService3.class;
            case 3:return ValueTileService4.class;
            case 4:return ValueTileService5.class;
            case 5:return ValueTileService6.class;
            default:return ValueTileService.class;
        }
    }
    static void request(Context c){
        for(int i=0;i<SLOTS;i++)try{TileService.requestListeningState(c,new ComponentName(c,service(i)));}
        catch(RuntimeException ignored){}
    }
    static boolean hasReading(Watch w){return w!=null&&w.initialized;}
    static String value(Watch w){
        if(w==null||!hasReading(w))return "Sin lectura";
        if(!w.lastNumeric.isEmpty())return w.lastNumeric;
        return w.lastPreview.isEmpty()?"(Vacío)":w.lastPreview;
    }
    static String compact(String value,int max){
        String s=value.replaceAll("\\s+"," ").trim();
        int n=s.codePointCount(0,s.length());
        return n<=max?s:s.substring(0,s.offsetByCodePoints(0,max-1))+"…";
    }
    static String label(Watch w){
        if(w==null)return "Vigilante";
        return compact(value(w),48)+arrow(w);
    }
    static String arrow(Watch w){
        if(w==null||!hasReading(w))return "";
        if(!w.enabled||w.actionRequired||!w.lastError.isEmpty())return " !";
        String d=NumericTrend.direction(w);
        if(!w.lastNumeric.isEmpty())return d.startsWith("↑")?" ↑":d.startsWith("↓")?" ↓":d.startsWith("→")?" →":"";
        if(w.previousValueKnown)return w.previousValueChanged?" ↻":" →";
        return "";
    }
    static String subtitle(Watch w){
        if(w==null)return "Elegir vigilancia";
        if(!w.enabled)return "En pausa";
        if(w.actionRequired)return "Verificación requerida";
        if(!w.lastError.isEmpty())return "Dato sin actualizar";
        if(!hasReading(w))return "Esperando lectura";
        if(!w.lastNumeric.isEmpty())return NumericTrend.direction(w);
        if(!w.previousValueKnown)return "Primera lectura";
        return w.previousValueChanged?"Cambió":"Sin cambios";
    }
    static String iconText(Watch w){
        if(!hasReading(w))return "";
        NumericTrend n=NumericTrend.storedValue(w.lastNumeric);
        return n==null?compact(value(w),12):compact(NumericTrend.fmt(n.value),12);
    }
}
