package com.vigilante.app;
import org.json.JSONObject;
final class BusinessPotential {
 static final String[] KEYS={"demand","profit","differentiation","execution","growth","risk"};
 static final int[] MAX={25,25,15,15,10,10};
 static int score(JSONObject j){JSONObject p=j.optJSONObject("potential");if(p==null)return -1;JSONObject f=p.optJSONObject("factors");if(f==null)return -1;int total=0;for(int i=0;i<KEYS.length;i++){int n=f.optInt(KEYS[i],-1);if(n<0||n>MAX[i])return -1;total+=n;}return total;}
 static String level(int n){return n>=85?"Muy alto":n>=70?"Alto":n>=50?"Moderado":n>=0?"Bajo":"Pendiente";}
 static int compare(JSONObject a,JSONObject b){boolean ar="rejected".equals(a.optString("status")),br="rejected".equals(b.optString("status"));if(ar!=br)return ar?1:-1;int n=Integer.compare(score(b),score(a));if(n!=0)return n;return a.optString("id").compareTo(b.optString("id"));}
}
