package com.vigilante.app;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares complete, unambiguous numeric readings, never arbitrary digits in text. */
public final class NumericTrend {
    private static final Pattern VALUE=Pattern.compile("^\\s*(?:(USD|CUP|EUR|GBP|CAD|\\$|€|£)\\s*)?([+-]?[0-9]{1,18}(?:[.,][0-9]{1,10})?)\\s*(USD|CUP|EUR|GBP|CAD|%|\\$|€|£)?\\s*$",Pattern.CASE_INSENSITIVE);
    public final BigDecimal value;
    public final String unit;
    private NumericTrend(BigDecimal n,String u){value=n;unit=u;}
    static String normalizedUnit(String s){
        if(s==null)return "";
        s=s.trim().toUpperCase(Locale.ROOT);
        if(s.equals("€"))return "EUR";
        if(s.equals("£"))return "GBP";
        return s;
    }
    static NumericTrend parse(String raw,String hint){
        if(raw==null)return null;
        Matcher m=VALUE.matcher(raw.replace('\u00a0',' '));
        if(!m.matches())return null;
        String pre=normalizedUnit(m.group(1)),post=normalizedUnit(m.group(3)),configured=normalizedUnit(hint);
        if(!pre.isEmpty()&&!post.isEmpty()&&!pre.equals(post))return null;
        String explicit=pre.isEmpty()?post:pre;
        if(!explicit.isEmpty()&&!configured.isEmpty()&&!explicit.equals(configured))return null;
        try{return new NumericTrend(new BigDecimal(m.group(2).replace(',','.')),explicit.isEmpty()?configured:explicit);}
        catch(NumberFormatException e){return null;}
    }
    static String unitHint(Watch w){
        if(!w.numericUnit.trim().isEmpty())return w.numericUnit;
        String name=w.name.toUpperCase(Locale.ROOT).replace(" ","");
        return name.contains("USD/CUP")?"CUP":"";
    }
    static String fmt(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
    String stored(){return fmt(value)+(unit.isEmpty()?"":" "+unit);}
    static NumericTrend storedValue(String raw){
        if(raw==null||raw.isEmpty())return null;
        int space=raw.indexOf(' ');
        String number=space<0?raw:raw.substring(0,space);
        String unit=space<0?"":raw.substring(space+1);
        try{return new NumericTrend(new BigDecimal(number),unit);}catch(Exception e){return null;}
    }
    static void record(Watch w,String observed,long time){
        NumericTrend now=parse(observed,unitHint(w));
        if(now==null){w.lastNumeric="";w.previousNumeric="";w.lastNumericAt=0;return;}
        NumericTrend before=storedValue(w.lastNumeric);
        w.previousNumeric=before!=null&&before.unit.equals(now.unit)?before.stored():"";
        w.lastNumeric=now.stored();w.lastNumericAt=time;
    }
    static String direction(Watch w){
        NumericTrend now=storedValue(w.lastNumeric),before=storedValue(w.previousNumeric);
        if(now==null)return "";
        if(before==null||!now.unit.equals(before.unit))return "Primera lectura";
        BigDecimal delta=now.value.subtract(before.value);
        int sign=delta.signum();
        if(sign==0)return "→ Sin cambios";
        return (sign>0?"↑ Subió +":"↓ Bajó −")+fmt(delta.abs())+(now.unit.isEmpty()?"":" "+now.unit);
    }
    static String detail(Watch w){
        String direction=direction(w);
        return w.previousNumeric.isEmpty()?direction:direction+" · Anterior: "+w.previousNumeric;
    }
}