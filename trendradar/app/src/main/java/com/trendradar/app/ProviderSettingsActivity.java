package com.trendradar.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;

public class ProviderSettingsActivity extends Activity {
    private static final int BG=0xff101820,CARD=0xff1b2933,GREEN=0xff75dab3,WHITE=0xffedf1f0,MUTED=0xffb5c9c5;
    private LinearLayout root;
    private TextView note;
    private final Map<String,TextView> statuses=new LinkedHashMap<>();
    @Override public void onCreate(Bundle b){super.onCreate(b);draw();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int fill){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(13));return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(5),0,dp(5));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private LinearLayout section(){
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(bg(CARD));p.setPadding(dp(14),dp(13),dp(14),dp(13));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(12));root.addView(p,lp);return p;
    }
    private void button(LinearLayout p,String label,Runnable click){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTextColor(WHITE);b.setBackground(bg(0xff29483f));p.addView(b);
        b.setOnClickListener(v->click.run());
    }
    private void draw(){
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(16),dp(12),dp(16),dp(25));
        sc.addView(root);setContentView(sc);
        root.addView(text("Créditos y proveedores",24,GREEN,true));
        button(root,"← Volver a Product Finder",this::finish);
        root.addView(text("Combina tres proveedores; la app detiene consultas nuevas al llegar al límite gratuito configurado. Los saldos de SerpAPI y SearchAPI se verifican con el proveedor; el de Serper es estimado localmente.",13,MUTED,false));
        note=text("",13,MUTED,false);root.addView(note);
        statuses.clear();
        for(String name:ProviderHub.NAMES){
            LinearLayout p=section();
            p.addView(text(name,18,GREEN,true));
            int max=ProviderHub.cap(name);
            p.addView(text(name.equals(ProviderHub.SERPER)?
                "Cupo inicial estimado: "+max+"; no se renueva automáticamente. Revisa tu consumo anterior desde Serper.dev.":
                name.equals(ProviderHub.SEARCH)?
                "Hasta "+max+" solicitudes gratuitas de prueba, no recurrentes.":
                "Máximo local "+max+" al mes; el saldo real puede ser menor.",12,MUTED,false));
            TextView state=text("Consultando…",13,WHITE,false);
            statuses.put(name,state);p.addView(state);
            if(name.equals(ProviderHub.SERP)){
                button(p,"Configurar SerpAPI en Product Finder",()->finish());
            } else {
                EditText api=new EditText(this);api.setHint("Clave "+name+" (se guarda cifrada)");
                api.setTextColor(WHITE);api.setHintTextColor(MUTED);
                api.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
                p.addView(api);
                button(p,"Guardar clave de "+name,()->{
                    try{
                        ProviderHub.save(this,name,api.getText().toString());
                        api.setText("");
                        Toast.makeText(this,name+" conectado",Toast.LENGTH_SHORT).show();
                        refresh();
                    }catch(Exception ex){
                        Toast.makeText(this,"No se pudo guardar: "+ex.getMessage(),Toast.LENGTH_LONG).show();
                    }
                });
                button(p,"Desconectar "+name,()->{
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Desconectar "+name)
                        .setMessage("Se borrará la clave y el contador local. El consumo real de la cuenta externa no se reinicia.")
                        .setNegativeButton("Cancelar",null)
                        .setPositiveButton("Desconectar",(d,w)->{ProviderHub.clear(this,name);refresh();})
                        .show();
                });
            }
            if(name.equals(ProviderHub.SERPER))button(p,"Consultar saldo real en Serper.dev",()->open("https://serper.dev/"));
            if(name.equals(ProviderHub.SEARCH))button(p,"Abrir panel de SearchAPI",()->open("https://www.searchapi.io/"));
        }
        button(root,"↻ Actualizar saldos y consumo",this::refresh);
        root.addView(text("Importante: los contadores locales no incluyen usos hechos antes de configurar este dispositivo ni desde otras apps. Mantén desactivada cualquier renovación anticipada o modalidad de pago en el proveedor. El límite local no controla su facturación.",12,MUTED,false));
        refresh();
    }
    private void open(String url){
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
    }
    private void refresh(){
        note.setText("Verificando créditos sin hacer búsquedas de productos…");
        new Thread(()->{
            Map<String,ProviderHub.Balance> all=new LinkedHashMap<>();
            for(String p:ProviderHub.NAMES)all.put(p,ProviderHub.balance(getApplicationContext(),p,true));
            runOnUiThread(()->{
                note.setText("Saldo comprobado. 0 consultas de productos consumidas por esta actualización.");
                for(Map.Entry<String,ProviderHub.Balance> e:all.entrySet()){
                    ProviderHub.Balance b=e.getValue();
                    TextView target=statuses.get(e.getKey());if(target==null)continue;
                    target.setText("Usados por Product Finder: "+b.used+"/"+b.cap+
                        "\nDisponibles para la app: "+b.left+
                        (b.real?" (saldo real verificado: "+b.remote+")":" (estimación local)")+
                        "\n"+b.status);
                }
            });
        }).start();
    }
}
