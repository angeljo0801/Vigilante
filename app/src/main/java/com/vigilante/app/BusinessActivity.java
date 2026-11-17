package com.vigilante.app;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.*;

public final class BusinessActivity extends Activity {
 private static final int BG=0xff0a101f,CARD=0xff162137,BORDER=0xff30425e,WHITE=0xfff1f6ff,MUTED=0xffa7b9d5,BLUE=0xff3e7be7,GREEN=0xff63ddad;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private LinearLayout root; private ScrollView scroll; private String selected="",filter="",quickSection=""; private boolean busy=false;
 private final Set<Integer> expanded=new HashSet<>();
 private final Set<String> departments=new HashSet<>();
 private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
 private GradientDrawable bg(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(14));g.setStroke(dp(1),BORDER);return g;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);return l;}
 private TextView t(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(3),0,dp(3));return v;}
 private void add(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);p.addView(v,lp);}
 private Button btn(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(14);b.setBackground(bg(CARD));b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setOnClickListener(v->action.run());return b;}
 private LinearLayout card(){LinearLayout l=col();l.setBackground(bg(CARD));l.setPadding(dp(14),dp(12),dp(14),dp(12));return l;}
 private void tile(LinearLayout r,String value,String label,Runnable action){LinearLayout c=card();c.addView(t(value,25,WHITE,true));c.addView(t(label,12,MUTED,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);r.addView(c,lp);c.setOnClickListener(v->action.run());}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);render();refresh();}
 @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
 @Override public void onBackPressed(){if(!selected.isEmpty()){selected="";filter="";quickSection="";expanded.clear();render();}else if(!filter.isEmpty()){filter="";render();}else super.onBackPressed();}
 private void refresh(){if(busy)return;busy=true;io.execute(()->{String s;try{s=BusinessSync.sync(this);}catch(Exception e){s="Error de sincronización: "+e.getMessage();}final String result=s;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;BusinessSync.prefs(this).edit().putString("status",result).apply();render();});});}
 private void render(){int position=scroll==null?0:scroll.getScrollY();ScrollView sc=new ScrollView(this);scroll=sc;sc.setBackgroundColor(BG);root=col();root.setPadding(dp(16),dp(18),dp(16),dp(24));sc.addView(root);setContentView(sc);sc.post(()->sc.scrollTo(0,position));
  add(root,t("VIGILANTE  •  INTELIGENCIA EMPRESARIAL",12,GREEN,true));
  add(root,t("Business Engine AI",29,WHITE,true));
  if(!selected.isEmpty()){add(root,btn("‹  Volver a proyectos",()->{selected="";quickSection="";expanded.clear();render();}));detail();return;}
  add(root,t("Centro de control · 27 especialistas coordinados por ChatGPT",14,MUTED,false));
  JSONArray a=BusinessSync.reports(this);int approved=0,review=0;for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;if(j.optBoolean("approved"))approved++;if("ready_for_review".equals(j.optString("status")))review++;}
  LinearLayout r=row();tile(r,"27","Agentes especializados",()->{filter="agents";render();});tile(r,"6","Departamentos",()->{filter="agents";render();});add(root,r);
  r=row();tile(r,""+a.length(),"Proyectos",()->{filter="";render();});tile(r,""+review,"Por aprobar",()->{filter="review";render();});add(root,r);
  add(root,btn("＋  Introducir producto o servicio",this::newIdea));
  add(root,btn("↻  "+(busy?"Sincronizando…":"Actualizar desde ChatGPT"),this::refresh));
  add(root,t(BusinessSync.prefs(this).getString("status","Pendiente de sincronización"),12,MUTED,false));
  r=row();tile(r,"Radar","Ideas automáticas",()->{filter="radar";render();});tile(r,""+approved,"Ideas aprobadas",()->{filter="approved";render();});add(root,r);
  add(root,btn("Equipo de agentes · ver las 27 funciones",()->{filter="agents";render();}));
  if("agents".equals(filter)){add(root,btn("Ver proyectos",()->{filter="";render();}));agentDirectory();return;}
  add(root,t("Proyectos comerciales"+(filter.isEmpty()?"":" · "+filter),19,WHITE,true));
  if(!filter.isEmpty())add(root,btn("Ver todos",()->{filter="";render();}));
  int shown=0;for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;if("radar".equals(filter)&&!"radar".equals(j.optString("origin")))continue;if("review".equals(filter)&&!"ready_for_review".equals(j.optString("status")))continue;if("approved".equals(filter)&&!j.optBoolean("approved"))continue;shown++;projectCard(j);}
  JSONArray pending=BusinessSync.visibleRequests(this);for(int i=0;i<pending.length();i++){JSONObject j=pending.optJSONObject(i);if(j!=null&&"submit".equals(j.optString("action"))&&BusinessSync.report(this,j.optString("projectId")).length()==0)add(root,t((j.optBoolean("sent")?"Enviado · pendiente de investigación: ":"Por enviar: ")+j.optString("title"),14,MUTED,false));}
  if(shown==0)add(root,t("Todavía no hay informes en esta vista. Introduce una idea o sincroniza el radar.",15,MUTED,false));
  add(root,t("Accesos rápidos",19,WHITE,true));
  for(String label:new String[]{"Investigaciones","Clientes potenciales","Campañas","Rentabilidad","Publicaciones"})add(root,btn(label,()->chooseProject(label)));
 }
 private void chooseProject(String section){
  JSONArray a=BusinessSync.reports(this);java.util.ArrayList<String> titles=new java.util.ArrayList<>(),ids=new java.util.ArrayList<>();
  for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null){titles.add(x.optString("title"));ids.add(x.optString("id"));}}
  if(titles.isEmpty()){Toast.makeText(this,"Todavía no hay proyectos",0).show();return;}
  new AlertDialog.Builder(this).setTitle(section+" · elige proyecto").setItems(titles.toArray(new String[0]),(d,n)->{selected=ids.get(n);quickSection=section;expanded.clear();departments.clear();if("Investigaciones".equals(section)){departments.add("Mercado");departments.add("Producto y finanzas");}else if("Clientes potenciales".equals(section)){departments.add("Mercado");departments.add("Ventas");expanded.add(4);expanded.add(16);}else if("Campañas".equals(section)){departments.add("Marketing");expanded.add(10);expanded.add(15);}else if("Rentabilidad".equals(section)){departments.add("Producto y finanzas");expanded.add(8);}else{departments.add("Marketing");expanded.add(12);expanded.add(13);expanded.add(14);}if(scroll!=null)scroll.scrollTo(0,0);render();}).show();
 }
 private void projectCard(JSONObject j){LinearLayout c=card();c.addView(t(j.optString("title"),19,WHITE,true));c.addView(t(j.optString("type")+" · "+j.optString("market")+" · "+j.optString("origin"),12,MUTED,false));c.addView(t(status(j.optString("status")),14,GREEN,true));JSONObject agents=j.optJSONObject("agents");int n=0;if(agents!=null)for(int k=1;k<=27;k++){JSONObject x=agents.optJSONObject(""+k);if(x!=null&&"complete".equals(x.optString("status")))n++;}ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(27);bar.setProgress(n);c.addView(bar);c.addView(t(n+"/27 informes completos",12,MUTED,false));c.setOnClickListener(v->{selected=j.optString("id");quickSection="";expanded.clear();departments.clear();departments.add("Dirección");expanded.add(27);if(scroll!=null)scroll.scrollTo(0,0);render();});add(root,c);}
 private String status(String s){switch(s){case "ready_for_review":return "Esperando tu decisión";case "approved":return "Aprobado · preparando lanzamiento";case "rejected":return "Rechazado";case "launch_ready":return "Estrategia preparada";case "needs_validation":return "Requiere validación";default:return "En investigación";}}
 private void agentDirectory(){for(String d:BusinessAgents.DEPTS){add(root,t(d,18,GREEN,true));for(int k=0;k<27;k++){String[] a=BusinessAgents.ALL[k];if(!a[0].equals(d))continue;final int id=k+1;add(root,btn(id+". "+a[1]+(expanded.contains(id)?"  ▴":"  ▾"),()->{if(!expanded.add(id))expanded.remove(id);render();}));if(expanded.contains(id))add(root,t(a[2],14,MUTED,false));}}}
 private void detail(){JSONObject j=BusinessSync.report(this,selected);add(root,t(j.optString("title","Proyecto"),23,WHITE,true));add(root,t(status(j.optString("status")),15,GREEN,true));add(root,t(j.optString("summary","Investigación pendiente."),15,WHITE,false));add(root,t("Origen: "+j.optString("origin")+" · Mercado: "+j.optString("market"),12,MUTED,false));
  if(canApprove(j)){add(root,btn("✓  Aprobar y preparar marketing",()->decision("approve")));add(root,btn("Solicitar más investigación",()->decision("revise")));add(root,btn("Rechazar idea",()->decision("reject")));}
  else if(!j.optBoolean("approved")&&!"rejected".equals(j.optString("status"))){add(root,btn("Solicitar revisión",()->decision("revise")));add(root,btn("Rechazar idea",()->decision("reject")));}
  JSONObject reports=j.optJSONObject("agents");for(String d:BusinessAgents.DEPTS){add(root,btn(d+(departments.contains(d)?"  ▴":"  ▾"),()->{if(!departments.add(d))departments.remove(d);render();}));if(!departments.contains(d))continue;for(int k=0;k<27;k++){String[] a=BusinessAgents.ALL[k];if(!d.equals(a[0]))continue;int id=k+1;JSONObject report=reports==null?null:reports.optJSONObject(""+id);boolean locked=!j.optBoolean("approved")&&!BusinessAgents.pre(id);String st=locked?"Bloqueado hasta aprobación":report==null?"Pendiente":("complete".equals(report.optString("status"))?"Completo":"En investigación");add(root,btn(id+". "+a[1]+" · "+st+(expanded.contains(id)?" ▴":" ▾"),()->{if(!expanded.add(id))expanded.remove(id);render();}));if(expanded.contains(id)){LinearLayout c=card();c.addView(t(a[2],13,MUTED,false));if(locked)c.addView(t("Disponible tras la aprobación del equipo y del propietario.",14,WHITE,false));else if(report!=null){c.addView(t(report.optString("summary"),16,WHITE,true));c.addView(t(report.optString("findings"),14,WHITE,false));c.addView(t("Incertidumbre: "+report.optString("uncertainty","No indicada"),13,MUTED,false));c.addView(t("Próximos pasos: "+report.optString("nextSteps"),14,WHITE,false));JSONArray src=report.optJSONArray("sources");if(src!=null)for(int z=0;z<src.length();z++){JSONObject source=src.optJSONObject(z);if(source==null)continue;String url=source.optString("url");String title=source.optString("title",url);if(url.startsWith("https://"))c.addView(btn(title,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No se pudo abrir",0).show();}}));}}else c.addView(t("Todavía no hay informe.",14,MUTED,false));add(root,c);}}}
  add(root,btn("Actualizar informes",this::refresh));
 }
 private boolean canApprove(JSONObject j){if(!"ready_for_review".equals(j.optString("status"))||j.optBoolean("approved")||!"approve".equals(j.optString("recommendation")))return false;JSONObject a=j.optJSONObject("agents");if(a==null)return false;for(int k=1;k<=27;k++)if(BusinessAgents.pre(k)){JSONObject x=a.optJSONObject(""+k);if(x==null||!"complete".equals(x.optString("status")))return false;}return true;}
 private void decision(String action){EditText note=new EditText(this);note.setHint("Comentarios (opcional)");new AlertDialog.Builder(this).setTitle("approve".equals(action)?"Aprobar idea":"reject".equals(action)?"Rechazar idea":"Solicitar revisión").setView(note).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->{try{BusinessSync.enqueue(this,action,selected,"","","",note.getText().toString());Toast.makeText(this,"Decisión guardada; pendiente de sincronización",1).show();refresh();}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}}).show();}
 private EditText field(LinearLayout f,String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(WHITE);e.setHintTextColor(MUTED);f.addView(e);return e;}
 private void newIdea(){LinearLayout f=col();f.setPadding(dp(20),dp(8),dp(20),dp(8));EditText title=field(f,"Nombre del producto o servicio");Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Producto","Servicio","Aplicación","Otro"}));f.addView(type);EditText market=field(f,"Mercado (ej. Estados Unidos)");market.setText("Estados Unidos");EditText desc=field(f,"Descripción, presupuesto, enlaces o detalles");desc.setMinLines(3);
 AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Nueva idea de negocio").setView(f).setNegativeButton("Cancelar",null).setPositiveButton("Investigar",null).create();dlg.setOnShowListener(d->dlg.getButton(-1).setOnClickListener(v->{if(title.getText().toString().trim().isEmpty()){title.setError("Escribe una idea");return;}try{BusinessSync.enqueue(this,"submit","",title.getText().toString().trim(),type.getSelectedItem().toString(),market.getText().toString(),desc.getText().toString());dlg.dismiss();render();refresh();}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}}));dlg.show();}
}
