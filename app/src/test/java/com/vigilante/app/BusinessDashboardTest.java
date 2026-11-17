package com.vigilante.app;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class BusinessDashboardTest {
 private BusinessActivity a;
 @Before public void setup() throws Exception {
  a=Robolectric.buildActivity(BusinessActivity.class).create().start().resume().get();
  JSONArray j=new JSONArray().put(new JSONObject().put("id","test-product").put("title","Producto de prueba").put("type","Producto físico").put("status","research")).put(new JSONObject().put("id","test-service").put("title","Jardinería de prueba").put("type","Servicio / software").put("status","needs_validation")).put(new JSONObject().put("id","test-software").put("title","Software de prueba").put("type","Software").put("status","research"));
  BusinessSync.prefs(a).edit().putString("reports",j.toString()).commit();
  java.lang.reflect.Method render=BusinessActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);
 }
 @After public void clean(){if(a!=null){BusinessSync.prefs(a).edit().clear().commit();a.finish();}}
 private View find(View v,String text,boolean button){if(v instanceof TextView && (!button||v instanceof Button)&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),text,button);if(x!=null)return x;}}return null;}
 private View root(){return a.findViewById(android.R.id.content);}
 private void tap(String label){View v=find(root(),label,true);assertNotNull(label,v);assertTrue(label,v.performClick());}
 private void visible(String label){assertNotNull(label,find(root(),label,false));}
 @Test public void filtersOpenSeparatePagesAndProjects(){tap("Productos físicos");visible("Producto de prueba");assertNull(find(root(),"Jardinería de prueba",false));tap("Abrir proyecto");visible("Presentación comercial");tap("Volver a proyectos");tap("Volver al dashboard");tap("Servicios");visible("Jardinería de prueba");assertNull(find(root(),"Software de prueba",false));tap("Volver al dashboard");tap("Software / apps");visible("Software de prueba");}
 @Test public void potentialRankingIsDescending() throws Exception {
  JSONArray rows=BusinessSync.reports(a);
  rows.getJSONObject(0).put("potential",new JSONObject().put("factors",new JSONObject().put("demand",10).put("profit",10).put("differentiation",5).put("execution",10).put("growth",5).put("risk",5)));
  rows.getJSONObject(1).put("potential",new JSONObject().put("factors",new JSONObject().put("demand",20).put("profit",20).put("differentiation",10).put("execution",10).put("growth",8).put("risk",7)));
  BusinessSync.prefs(a).edit().putString("reports",rows.toString()).commit();
  java.lang.reflect.Method render=BusinessActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);
  visible("#1 · 75/100");visible("#2 · 45/100");
  assertTrue(BusinessPotential.compare(rows.getJSONObject(1),rows.getJSONObject(0))<0);
  assertEquals(-1,BusinessPotential.score(rows.getJSONObject(2)));
 }
 @Test public void departmentsAndAgentsExpand(){tap("Departamentos");tap("Mercado");tap("Investigador de mercado");visible("Demanda, tamaño");visible("Consultar informe en proyecto");}
}
