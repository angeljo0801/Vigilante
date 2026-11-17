package com.vigilante.app;
import android.widget.Toast;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A native Android dashboard, no login or hosted account required. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 16, 31);
    private static final int CARD = Color.rgb(22, 33, 55);
    private static final int BORDER = Color.rgb(48, 66, 94);
    private static final int WHITE = Color.rgb(241, 246, 255);
    private static final int MUTED = Color.rgb(167, 185, 213);
    private static final int BLUE = Color.rgb(62, 123, 231);
    private static final int GREEN = Color.rgb(99, 221, 173);
    private static final String[] RULES = {"APPEARS", "DISAPPEARS", "CHANGES", "ALWAYS", "ABOVE", "BELOW"};
    private static final String[] RULE_LABELS = {"Cuando APARECE", "Cuando DESAPARECE", "Cuando CAMBIA", "En cada lectura", "Si supera un número", "Si baja de un número"};
    private static final Integer[] INTERVALS = {15, 30, 60, 120, 360, 720, 1440};
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private LinearLayout cardList;
    private TextView statistics;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        AlarmNotifier.setup(this);
        Scheduler.syncAll(this);
        createDashboard();
        askNotificationPermission();
        if(state==null) handleIncoming(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleIncoming(intent);
    }

    private void handleIncoming(android.content.Intent intent){
        if(intent!=null&&"com.vigilante.app.OPEN_QUICK_VALUE".equals(intent.getAction())){showQuickDetail(Math.max(0,Math.min(QuickValue.SLOTS-1,intent.getIntExtra("quick_slot",0))));return;}
        receiveImport(intent);
    }

    private void receiveImport(android.content.Intent intent){
        if(intent==null||!android.content.Intent.ACTION_SEND.equals(intent.getAction()))return;
        CharSequence content=intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
        if(content!=null)showImport(content.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cardList != null) { Scheduler.syncAll(this); refreshCards(); ChatInbox.requestNow(this); QuickValue.request(this); }
    }

    @Override
    protected void onDestroy() {
        background.shutdown();
        super.onDestroy();
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (stroke != 0) bg.setStroke(dp(1), stroke);
        return bg;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams bottomMargin(int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private TextView text(String content, int size, int color, boolean strong) {
        TextView label = new TextView(this);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(color);
        if (strong) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private Button button(String title, int background) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(rounded(background, 12, background == BLUE ? 0 : BORDER));
        b.setMinHeight(dp(42));
        b.setPadding(dp(7), dp(8), dp(7), dp(8));
        return b;
    }

    private void createDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(19), dp(25), dp(19), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("MONITOR WEB  •  ANDROID", 12, GREEN, true);
        root.addView(eyebrow, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 10));

        TextView heading = text("Vigilante", 35, WHITE, true);
        root.addView(heading, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 5));

        TextView sub = text("Extrae datos de cualquier web compatible y programa alertas.",
                15, MUTED, false);
        root.addView(sub, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 23));

        LinearLayout status = column();
        status.setPadding(dp(17), dp(16), dp(17), dp(15));
        status.setBackground(rounded(CARD, 17, BORDER));
        statistics = text("", 17, WHITE, true);
        status.addView(statistics, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 7));
        TextView info = text("Scraping local · por intervalos u horas diarias. Android puede retrasar las tareas.",
                12, MUTED, false);
        status.addView(info);
        root.addView(status, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 17));

        Button business = button("Business Engine AI · Centro empresarial", BLUE);
        business.setTextSize(16);
        business.setOnClickListener(v -> startActivity(new android.content.Intent(this, BusinessActivity.class)));
        root.addView(business,bottomMargin(dp(53),12));
        Button explore = button("Explorar una página web", BLUE);
        explore.setTextSize(16);
        explore.setOnClickListener(v -> showExplorer(""));
        root.addView(explore, bottomMargin(dp(53), 12));
        Button add = button("＋  Nueva vigilancia manual", CARD);
        add.setTextSize(16);
        add.setOnClickListener(v -> showEditor(null));
        root.addView(add, bottomMargin(dp(53), 12));
        Button importTask=button("Importar búsqueda de ChatGPT",BLUE);
        importTask.setOnClickListener(v->showImport(""));
        root.addView(importTask,bottomMargin(dp(48),12));
        Button direct = button("Conectar ChatGPT · envío directo",BLUE);
        direct.setOnClickListener(v->showChatInbox());
        root.addView(direct,bottomMargin(dp(48),12));
        Button quick=button("Añadir alertas al panel de control",BLUE);
        quick.setOnClickListener(v->showQuickValue());
        root.addView(quick,bottomMargin(dp(48),12));
        Button bridge = button("Configurar Playwright remoto", CARD);
        bridge.setOnClickListener(v -> showBridgeSettings());
        root.addView(bridge,bottomMargin(dp(44),10));
        Button refresh = button("Actualizar resultados", CARD);
        refresh.setOnClickListener(v -> {refreshCards();QuickValue.request(this);});
        root.addView(refresh, bottomMargin(dp(43), 10));
        if (Build.VERSION.SDK_INT >= 31) {
            Button exact = button("Permitir alarmas a la hora elegida", CARD);
            exact.setOnClickListener(v -> {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { new AlertDialog.Builder(this).setMessage("Configura Alarmas y recordatorios desde Ajustes de Android.").setPositiveButton("Cerrar",null).show(); }
            });
            root.addView(exact,bottomMargin(dp(44), 15));
        }

        TextView listHeading = text("MIS VIGILANCIAS", 12, MUTED, true);
        root.addView(listHeading, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 11));

        cardList = column();
        root.addView(cardList, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 15));

        Button test = button("🔔  Probar sonido de alarma", CARD);
        test.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                askNotificationPermission();
                new AlertDialog.Builder(this)
                        .setMessage("Permite las notificaciones para que Vigilante pueda avisarte.")
                        .setPositiveButton("Entendido", null).show();
                return;
            }
            AlarmNotifier.test(this);
        });
        root.addView(test, bottomMargin(dp(46), 18));

        TextView foot = text("Las páginas con inicio de sesión, CAPTCHA o contenido protegido "
                + "pueden impedir la extracción. JavaScript está disponible en el motor navegador.", 12, MUTED, false);
        root.addView(foot);
        refreshCards();
    }

    private void refreshCards() {
        List<Watch> watches = WatchStore.all(this);
        int active = 0;
        for (Watch w : watches) if (w.enabled) active++;
        statistics.setText(active + " vigilancias activas  ·  " + watches.size() + " en total");
        cardList.removeAllViews();
        if (watches.isEmpty()) {
            LinearLayout empty = column();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(27), dp(20), dp(29));
            empty.setBackground(rounded(CARD, 15, BORDER));
            TextView txt = text("Aún no has creado vigilancias.\nAñade una web y la palabra que quieres detectar.",
                    15, MUTED, false);
            txt.setGravity(Gravity.CENTER);
            empty.addView(txt);
            cardList.addView(empty, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 12));
            return;
        }
        for (Watch w : watches) appendWatchCard(w);
    }

    private void appendWatchCard(Watch w) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(15), dp(14), dp(15));
        card.setBackground(rounded(CARD, 17, BORDER));

        LinearLayout top = row();
        TextView name = text(w.name, 17, WHITE, true);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(w.enabled ? "Pausar vigilancia" : "Activar vigilancia");
        toggle.setChecked(w.enabled);
        toggle.setEnabled(!w.inboxDraft);
        toggle.setOnCheckedChangeListener((b, isOn) -> {
            WatchStore.mutate(this, w.id, item -> item.enabled = isOn);
            Watch updated = WatchStore.find(this, w.id);
            if (updated != null) Scheduler.schedule(this, updated);
            refreshCards();
        });
        top.addView(toggle);
        card.addView(top, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 4));

        TextView url = text(w.url, 12, MUTED, false);
        url.setMaxLines(2);
        card.addView(url, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 11));
        if(!w.objective.isEmpty()) { TextView goal=text("Objetivo: "+w.objective,12,MUTED,false);goal.setMaxLines(3);card.addView(goal,bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,8)); }

        int rulePos = Math.max(0, Arrays.asList(RULES).indexOf(w.rule));
        String trigger = RULE_LABELS[rulePos] + (w.keyword.isEmpty() ? "" : ": " + w.keyword);
        TextView target = text("◎  " + trigger, 14, WHITE, true);
        card.addView(target, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 4));

        String scope = (Engines.label(Engines.mode(w))+" · ") + (w.selector.isEmpty() ? "Página completa" : "Selector: " + w.selector);
        if (!w.attribute.isEmpty()) scope += " · atributo: " + w.attribute;
        scope += w.times.isEmpty() ? " · cada " + w.intervalMinutes + " min" : " · diario " + w.times;
        TextView scopeView = text(scope, 12, MUTED, false);
        card.addView(scopeView, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 10));

        String state;
        int stateColor = MUTED;
        if(w.inboxDraft){
            state="⚠ Borrador recibido · selecciona el dato y guarda";
            stateColor=Color.rgb(255,190,118);
        } else if (!w.enabled) {
            state = "⏸  En pausa";
        } else if (w.actionRequired) {
            state = "⚠ Acción requerida · vigilancia en espera";
            stateColor = Color.rgb(255,190,118);
        } else if (!w.lastError.isEmpty()) {
            state = "⚠  " + w.lastError;
            stateColor = Color.rgb(255, 190, 118);
        } else if (!w.initialized) {
            state = "◌  Esperando primera comprobación";
        } else if ("CHANGES".equals(w.rule) || "ALWAYS".equals(w.rule)) {
            state = "✓  Estado inicial registrado";
            stateColor = GREEN;
        } else {
            state = w.lastMatched ? "✓  Condición cumplida" : "○  Condición no cumplida";
            if (w.lastMatched) stateColor = GREEN;
        }
        TextView status = text(state, 13, stateColor, true);
        card.addView(status, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        String when = w.lastChecked == 0 ? "Sin comprobaciones"
                : "Última revisión: " + new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                        .format(new Date(w.lastChecked));
        TextView date = text(when, 12, MUTED, false);
        card.addView(date, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        if (!w.lastPreview.isEmpty()) card.addView(text("Dato: " + w.lastPreview, 14, WHITE, false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        if(!w.lastNumeric.isEmpty()){
            String trend=NumericTrend.detail(w);
            int color=trend.startsWith("↑")?GREEN:trend.startsWith("↓")?Color.rgb(255,125,125):MUTED;
            card.addView(text(w.lastNumeric+" · "+trend,15,color,true),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,8));
        }
        if(w.pinNumeric)card.addView(text("Valor fijado en notificaciones cuando sea numérico",12,MUTED,false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,8));
        if (w.enabled && !w.times.isEmpty()) {
            long next = Scheduler.nextTime(w.times,System.currentTimeMillis());
            card.addView(text("Próxima: " + new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(next)),12,MUTED,false));
        }
        Button history = button("Historial de extracciones (" + w.history.length() + ")", CARD);
        history.setOnClickListener(v -> showHistory(w.id));
        card.addView(history,bottomMargin(dp(43),10));

        if(w.actionRequired) {
            card.addView(text(w.lastError,13,MUTED,false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,8));
            Button open=button("Abrir página para verificar",BLUE);
            open.setOnClickListener(v->openManualPage(w.url,w.id));
            card.addView(open,bottomMargin(dp(46),8));
        }
        LinearLayout actions = row();
        Button check = button(w.inboxDraft?"Elegir dato":w.actionRequired?"Reintentar":"Comprobar", BLUE);
        check.setTextSize(12);
        check.setOnClickListener(v -> {if(w.inboxDraft)showExplorer(w.url,w);else checkNow(w.id, check);});
        Button edit = button("Editar", CARD);
        edit.setOnClickListener(v -> {
            Watch latest = WatchStore.find(this, w.id);
            if (latest != null) showEditor(latest);
        });
        Button delete = button("Borrar", CARD);
        delete.setOnClickListener(v -> confirmDelete(w));
        Button[] controls = {check, edit, delete};
        for (int i = 0; i < controls.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(43), 1);
            if (i != controls.length - 1) p.rightMargin = dp(7);
            actions.addView(controls[i], p);
        }
        card.addView(actions);
        if(!w.inboxDraft){Button quickPin=button("Añadir esta alerta al panel",CARD);quickPin.setOnClickListener(v->chooseQuickSlot(w.id));card.addView(quickPin,bottomMargin(dp(44),0));}
        cardList.addView(card, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 12));
    }

    private void showHistory(String id) {
        Watch w = WatchStore.find(this,id);
        if (w == null) return;
        StringBuilder out = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault());
        for (int i=0;i<w.history.length();i++) {
            org.json.JSONObject h=w.history.optJSONObject(i);
            if(h==null) continue;
            out.append(fmt.format(new Date(h.optLong("time"))));
            if(h.optBoolean("alert")) out.append(" · ALERTA");
            out.append("\n");
            String error=h.optString("error");
            out.append(error.isEmpty()?h.optString("value"):"Error: "+error).append("\n\n");
        }
        String result=out.length()==0?"Sin lecturas todavía.":out.toString();
        new AlertDialog.Builder(this).setTitle("Historial · "+w.name).setMessage(result)
            .setNeutralButton("Copiar",(d,v)->{
                android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Historial",result));
            }).setPositiveButton("Cerrar",null).show();
    }

    private void checkNow(String id, Button origin) {
        origin.setEnabled(false);
        origin.setText("Revisando...");
        background.execute(() -> {
            String result = MonitorRunner.check(getApplicationContext(), id, true);
            runOnUiThread(() -> {
                refreshCards();
                if (!isFinishing() && !isDestroyed()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Resultado de la comprobación")
                            .setMessage(result)
                            .setPositiveButton("Cerrar", null)
                            .show();
                }
            });
        });
    }

    private EditText input(LinearLayout parent, String label, String value, String hint) {
        TextView title = text(label, 13, WHITE, true);
        parent.addView(title, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 7));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setHint(hint);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setTextSize(14);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackground(rounded(CARD, 10, BORDER));
        parent.addView(field, bottomMargin(dp(47), 16));
        return field;
    }

    private Spinner spinner(LinearLayout parent, String title, String[] options, int selected) {
        TextView label = text(title, 13, WHITE, true);
        parent.addView(label, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 7));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected));
        parent.addView(spinner, bottomMargin(dp(48), 16));
        return spinner;
    }

    private void openManualPage(String url,String id) {
        android.content.Intent i=new android.content.Intent(this,ManualPageActivity.class);
        i.setData(Uri.parse(url));
        i.putExtra("watch_id",id);
        startActivity(i);
    }

    private void showBridgeSettings(){
        LinearLayout form=column();form.setPadding(dp(20),dp(16),dp(20),dp(10));
        EditText endpoint=input(form,"Dirección HTTPS",RemoteBrowser.endpoint(this),"https://tu-pc...ts.net");
        EditText token=input(form,"Token privado",RemoteBrowser.token(this),"Token generado en tu PC");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView info=text("Opcional. Configura el puente desde tu PC. Deja ambos campos vacíos para desactivarlo.",13,MUTED,false);
        form.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Playwright remoto").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).setNeutralButton("Probar conexión",null).create();
        Runnable save=()->{
            try{RemoteBrowser.configure(this,endpoint.getText().toString(),token.getText().toString());info.setText("Configuración guardada.");}
            catch(Exception e){info.setText(e.getMessage());}
        };
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{save.run();if(info.getText().toString().equals("Configuración guardada."))dialog.dismiss();});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{
                save.run();if(!info.getText().toString().equals("Configuración guardada."))return;
                if(!RemoteBrowser.configured(this)){info.setText("Configura primero el puente.");return;}
                info.setText("Probando conexión...");
                background.execute(()->{String msg;try{RemoteBrowser.test(getApplicationContext());msg="Conexión correcta.";}catch(Exception e){msg="Error: "+e.getMessage();}final String result=msg;runOnUiThread(()->{if(dialog.isShowing())info.setText(result);});});
            });
        });dialog.show();
    }

    private void showQuickValue(){
        chooseQuickSlot(null);
    }

    private void chooseQuickSlot(String preferredId){
        String[] labels=new String[QuickValue.SLOTS];
        for(int i=0;i<labels.length;i++){
            Watch w=QuickValue.chosen(this,i);
            labels[i]="Alerta "+(i+1)+" · "+(w==null?"Sin asignar":w.name);
        }
        new AlertDialog.Builder(this).setTitle("Accesos de Vigilante")
            .setItems(labels,(d,i)->configureQuickSlot(i,preferredId))
            .setNegativeButton("Cerrar",null).show();
    }

    private void configureQuickSlot(int slot,String preferredId){
        java.util.ArrayList<Watch> choices=new java.util.ArrayList<>();
        for(Watch w:WatchStore.all(this))if(!w.inboxDraft)choices.add(w);
        if(choices.isEmpty()){new AlertDialog.Builder(this).setMessage("Crea primero una vigilancia. Puede devolver cualquier dato.").setPositiveButton("Cerrar",null).show();return;}
        String[] labels=new String[choices.size()+1];labels[0]=slot==0?"Automático · última lectura":"Sin asignar";
        String current=preferredId==null?QuickValue.selectedId(this,slot):preferredId;int initial=0;
        for(int i=0;i<choices.size();i++){
            Watch w=choices.get(i);labels[i+1]=w.name+" · "+QuickValue.compact(QuickValue.value(w),60);
            if(w.id.equals(current))initial=i+1;
        }
        final int[] selected={initial};
        new AlertDialog.Builder(this).setTitle("Alerta "+(slot+1)+" en ajustes rápidos")
            .setSingleChoiceItems(labels,initial,(d,i)->selected[0]=i)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Guardar selección",(d,v)->QuickValue.select(this,slot,selected[0]==0?"":choices.get(selected[0]-1).id))
            .setPositiveButton("Añadir al panel",(d,v)->{
                QuickValue.select(this,slot,selected[0]==0?"":choices.get(selected[0]-1).id);
                if(Build.VERSION.SDK_INT>=33){
                    try{
                        android.app.StatusBarManager manager=getSystemService(android.app.StatusBarManager.class);
                        manager.requestAddTileService(new android.content.ComponentName(this,QuickValue.service(slot)),
                            "Vigilante · Alerta "+(slot+1),android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_qs_value),
                            getMainExecutor(),result->{
                                if(result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED||result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED){
                                    QuickValue.request(this);
                                    android.widget.Toast.makeText(this,"Acceso añadido. Ordénalo con el lápiz del panel.",android.widget.Toast.LENGTH_LONG).show();
                                }else showQuickInstructions();
                            });
                    }catch(RuntimeException ex){showQuickInstructions();}
                }else showQuickInstructions();
            }).show();
    }

    private void showQuickDetail(int slot){
        Watch w=QuickValue.chosen(this,slot);
        if(w==null){configureQuickSlot(slot,null);return;}
        String value=QuickValue.value(w);
        long time=0;
        for(int i=0;i<w.history.length();i++){
            org.json.JSONObject h=w.history.optJSONObject(i);
            if(h!=null&&h.optString("error").isEmpty()){value=h.optString("value",value);time=h.optLong("time");break;}
        }
        String when=time==0?"":("\nÚltima lectura válida: "+new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(time)));
        new AlertDialog.Builder(this).setTitle(w.name).setMessage(value+"\n\n"+QuickValue.subtitle(w)+when)
            .setNegativeButton("Cerrar",null)
            .setNeutralButton("Cambiar alerta",(d,v)->configureQuickSlot(slot,null))
            .setPositiveButton("Historial",(d,v)->showHistory(w.id)).show();
    }

    private void showQuickInstructions(){
        new AlertDialog.Builder(this).setTitle("Añadir Vigilante al panel")
            .setMessage("Desliza para abrir los ajustes rápidos, pulsa el lápiz de edición y busca Vigilante · Alerta 1 a 6. Arrastra los accesos que quieras. Android decide el tamaño y si muestra las etiquetas; los textos largos se acortan en el icono y se pueden leer al tocarlo.")
            .setPositiveButton("Entendido",null).show();
    }

    private void showChatInbox(){
        LinearLayout form=column();form.setPadding(dp(20),dp(16),dp(20),dp(12));
        form.addView(text("Vincula este teléfono una vez enviando su código público a ChatGPT. Los envíos viajan cifrados por GitHub y solo este teléfono los puede leer. Si activas la recepción, las configuraciones completas se guardan y programan automáticamente; las incompletas quedan como borradores.",14,MUTED,false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,12));
        Switch enabled=new Switch(this);enabled.setText("Autorizar recepción automática");enabled.setTextColor(WHITE);enabled.setChecked(ChatInbox.enabled(this));
        enabled.setOnCheckedChangeListener((v,on)->ChatInbox.enable(this,on));form.addView(enabled,bottomMargin(dp(48),10));
        Switch edits=new Switch(this);edits.setText("Autorizar cambios en alarmas existentes");edits.setTextColor(WHITE);edits.setChecked(ChatInbox.editsEnabled(this));edits.setOnCheckedChangeListener((v,on)->ChatInbox.allowEdits(this,on));form.addView(edits,bottomMargin(dp(48),10));
        Button export=button("Copiar alarmas y resultados para ChatGPT",CARD);form.addView(export,bottomMargin(dp(48),10));
        export.setOnClickListener(v->{android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("Vigilante",RemoteEdit.export(this)));});
        Button privateSync=button("Configurar conexión bidireccional privada",BLUE);form.addView(privateSync,bottomMargin(dp(48),10));
        privateSync.setOnClickListener(v->{
            LinearLayout fields=column();fields.setPadding(dp(20),dp(12),dp(20),dp(12));
            EditText repo=new EditText(this);repo.setHint("usuario/repositorio privado");repo.setText(PrivateSync.repo(this));fields.addView(repo);
            EditText token=new EditText(this);token.setHint("Token GitHub (Contents lectura/escritura)");token.setInputType(129);fields.addView(token);
            new AlertDialog.Builder(this).setTitle("Conexión privada").setView(fields).setNegativeButton("Cancelar",null).setNeutralButton("Desvincular",(d,x)->PrivateSync.clear(this)).setPositiveButton("Guardar",(d,x)->{try{PrivateSync.configure(this,repo.getText().toString(),token.getText().toString());ChatInbox.requestNow(this);Toast.makeText(this,"Conexión guardada; sincroniza para verificar",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).show();
        });
        Button copy=button("Copiar código de vinculación",BLUE);form.addView(copy,bottomMargin(dp(48),10));
        TextView status=text(ChatInbox.lastStatus(this),13,MUTED,false);form.addView(status,bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,10));
        Button sync=button("Sincronizar ahora",CARD);form.addView(sync,bottomMargin(dp(46),8));
        copy.setOnClickListener(v->{copy.setEnabled(false);background.execute(()->{
            try{String code=ChatInbox.pairingCode();runOnUiThread(()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("Vigilante pairing",code));copy.setEnabled(true);status.setText("Código público copiado. Pégalo en ChatGPT.");});}
            catch(Exception ex){runOnUiThread(()->{copy.setEnabled(true);status.setText("No se pudo generar el código: "+ex.getMessage());});}
        });});
        sync.setOnClickListener(v->{sync.setEnabled(false);status.setText("Sincronizando...");background.execute(()->{
            String result;try{result=ChatInbox.sync(getApplicationContext());}catch(Exception ex){result=ChatInbox.status(getApplicationContext(),"Error: "+ex.getMessage());}
            final String msg=result;runOnUiThread(()->{sync.setEnabled(true);status.setText(msg);refreshCards();});
        });});
        new AlertDialog.Builder(this).setTitle("Envío directo de ChatGPT").setView(form).setPositiveButton("Cerrar",null).show();
    }

    private void showImport(String initial){
        ScrollView scroll=new ScrollView(this);
        LinearLayout form=column();form.setPadding(dp(20),dp(16),dp(20),dp(12));scroll.addView(form);
        form.addView(text("Comparte una configuración desde ChatGPT o pégala aquí. Se abrirá un borrador: no se programa nada hasta confirmar Guardar. El objetivo es una nota, no una instrucción ejecutada por IA.",14,MUTED,false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,12));
        EditText paste=new EditText(this);paste.setText(initial.length()>20000?initial.substring(0,20000):initial);paste.setHint("VIGILANTE/1 o enlace compartido");
        paste.setTextColor(WHITE);paste.setHintTextColor(MUTED);paste.setGravity(Gravity.TOP);paste.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);paste.setMinLines(5);paste.setMaxLines(10);
        paste.setBackground(rounded(CARD,10,BORDER));paste.setPadding(dp(12),dp(10),dp(12),dp(10));form.addView(paste,bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,12));
        Button template=button("Copiar plantilla para ChatGPT",CARD);
        template.setOnClickListener(v->{android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("Plantilla Vigilante",TaskImport.TEMPLATE));android.widget.Toast.makeText(this,"Plantilla copiada",android.widget.Toast.LENGTH_SHORT).show();});
        form.addView(template,bottomMargin(dp(44),10));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Importar búsqueda").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Revisar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
            try{Watch draft=TaskImport.parse(paste.getText().toString());dialog.dismiss();showImportReview(draft);}
            catch(Exception e){paste.setError(e.getMessage());}
        }));dialog.show();
    }

    private void showImportReview(Watch draft){
        String when=draft.times.isEmpty()?"Cada "+draft.intervalMinutes+" minutos":draft.times+" (diario, hora del teléfono)";
        String info=draft.name+"\n"+(draft.url.isEmpty()?"Falta indicar la URL":draft.url)+"\n\nObjetivo: "+draft.objective+"\nHorario: "+when+"\nRegla: "+draft.rule+"\nMotor: "+Engines.label(Engines.mode(draft))+"\n\nRevisa los datos antes de guardar. Si falta el selector, elige un elemento en el explorador. Las tareas de ChatGPT no se sincronizan automáticamente.";
        new AlertDialog.Builder(this).setTitle("Revisar búsqueda importada").setMessage(info)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Elegir elemento",(d,v)->showExplorer(draft.url,draft))
            .setPositiveButton("Editar y guardar",(d,v)->showEditor(null,draft)).show();
    }

    private void showExplorer(String initialUrl) {showExplorer(initialUrl,null);}

    private void showExplorer(String initialUrl, Watch imported) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = column();
        form.setPadding(dp(20),dp(16),dp(20),dp(12));
        scroll.addView(form);
        EditText url = input(form,"URL de la página",initialUrl,"https://ejemplo.com");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Spinner engine = spinner(form,"Motor de análisis",Engines.LABELS,imported==null?0:Engines.index(Engines.mode(imported)));
        Spinner wait = spinner(form,"Espera después de cargar JavaScript",new String[]{"3 segundos","8 segundos","15 segundos","25 segundos"},1);
        final int[] waits={3,8,15,25};
        if(imported!=null) for(int i=0;i<waits.length;i++)if(waits[i]==imported.renderWaitSeconds)wait.setSelection(i);
        Button analyze = button("Analizar página",BLUE);
        form.addView(analyze,bottomMargin(dp(48),12));
        TextView status = text("Detectaremos datos que se puedan vigilar sin escribir código.",14,MUTED,false);
        form.addView(status,bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,12));
        Button verify=button("Abrir página para verificar",CARD);
        verify.setVisibility(View.GONE);
        form.addView(verify,bottomMargin(dp(46),10));
        EditText filter = input(form,"Filtrar resultados","","Precio, dólar, título...");
        filter.setVisibility(View.GONE);
        LinearLayout results = column();
        form.addView(results);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Explorador web")
            .setView(scroll).setNegativeButton("Cerrar",null).create();
        final java.util.List<Engines.Found> found = new java.util.ArrayList<>();
        final String[] analyzedUrl = {""};
        final String[] analyzedMode = {"AUTO"};
        final int[] analyzedWait = {8};
        Runnable render = () -> {
            results.removeAllViews();
            String query=filter.getText().toString().trim().toLowerCase(Locale.ROOT);
            int visible=0;
            for(Engines.Found itemFound: found) {
                PageExplorer.Candidate c=itemFound.candidate;
                if(!query.isEmpty() && !(c.category+" "+c.label+" "+c.value).toLowerCase(Locale.ROOT).contains(query)) continue;
                visible++;
                LinearLayout item=column();
                item.setPadding(dp(12),dp(12),dp(12),dp(12));
                item.setBackground(rounded(CARD,12,BORDER));
                item.addView(text(c.category+" · "+c.label,14,GREEN,true));
                item.addView(text(c.value,15,WHITE,false),bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,8));
                item.addView(text("Motor: "+Engines.label(itemFound.engine),12,MUTED,false));
                Button select=button("Vigilar este dato",BLUE);
                select.setOnClickListener(v -> {
                    Watch draft=new Watch();
                    draft.name=imported==null?c.label:imported.name+" · "+c.label;
                    if(imported!=null){draft.objective=imported.objective;draft.times=imported.times;draft.intervalMinutes=imported.intervalMinutes;draft.keyword=imported.keyword;draft.pinNumeric=imported.pinNumeric;draft.numericUnit=imported.numericUnit;}
                    draft.url=analyzedUrl[0];
                    draft.selector=c.selector;
                    draft.attribute=c.attribute;
                    draft.regex=c.regex;
                    draft.rule=imported==null?"CHANGES":imported.rule;
                    draft.engine=analyzedMode[0];
                    draft.preferredEngine=itemFound.engine;
                    draft.dynamic=!"HTML".equals(itemFound.engine);
                    draft.renderWaitSeconds=analyzedWait[0];
                    if(imported!=null&&imported.inboxDraft)showEditor(imported,draft);else showEditor(null,draft);
                });
                item.addView(select,bottomMargin(dp(42),0));
                results.addView(item,bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT,10));
            }
            if(visible==0) results.addView(text("No hay coincidencias con el filtro.",14,MUTED,false));
        };
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { if(!found.isEmpty()) render.run(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        analyze.setOnClickListener(v -> {
            String web=url.getText().toString().trim();
            if(!web.contains("://")) web="https://"+web;
            final String target=web;
            Uri uri=Uri.parse(target);
            if(uri.getHost()==null || !("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))) {
                url.setError("Introduce una URL HTTP o HTTPS válida"); return;
            }
            url.setText(target);
            final String mode = Engines.MODES[engine.getSelectedItemPosition()];
            final int seconds=waits[wait.getSelectedItemPosition()];
            analyze.setEnabled(false); url.setEnabled(false); engine.setEnabled(false); wait.setEnabled(false);
            found.clear(); results.removeAllViews(); filter.setVisibility(View.GONE); verify.setVisibility(View.GONE);
            status.setText("Analizando con "+Engines.label(mode)+"...");
            background.execute(() -> {
                try {
                    java.util.List<Engines.Found> candidates=Engines.explore(getApplicationContext(),target,seconds,mode);
                    runOnUiThread(() -> {
                        if(!dialog.isShowing()||isDestroyed()) return;
                        analyze.setEnabled(true); url.setEnabled(true); engine.setEnabled(true); wait.setEnabled(true);
                        found.addAll(candidates); analyzedUrl[0]=target; analyzedMode[0]=mode; analyzedWait[0]=seconds;
                        status.setText(found.isEmpty()?"No se encontraron datos compatibles. Prueba otra espera o motor.":found.size()+" datos detectados. Elige uno para configurar su vigilancia.");
                        if(!found.isEmpty()) { filter.setVisibility(View.VISIBLE); filter.setText(""); render.run(); }
                    });
                } catch(Exception e) {
                    runOnUiThread(() -> {
                        if(!dialog.isShowing()||isDestroyed()) return;
                        analyze.setEnabled(true); url.setEnabled(true); engine.setEnabled(true); wait.setEnabled(true);
                        status.setText("No se pudo analizar: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
                        if(e instanceof ActionRequired){
                            verify.setVisibility(View.VISIBLE);
                            verify.setOnClickListener(vv->openManualPage(target,""));
                            AlarmNotifier.notifyAction(getApplicationContext(),"Explorador web",target,"explore:"+target,e.getMessage());
                        }
                    });
                }
            });
        });
        dialog.show();
    }

    private void showEditor(Watch editing) { showEditor(editing, null); }

    private void showEditor(Watch editing, Watch draft) {
        final Watch seed = draft != null ? draft : editing;
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = column();
        form.setPadding(dp(20), dp(18), dp(20), dp(7));
        scroll.addView(form);

        EditText name = input(form, "Nombre de la vigilancia",
                seed == null ? "" : seed.name, "Por ejemplo: Producto disponible");
        EditText url = input(form, "Dirección web (URL)",
                seed == null ? "" : seed.url, "https://sitio.com/producto");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText objective = input(form,"Objetivo / nota (opcional)",seed==null?"":seed.objective,"Qué información deseas vigilar");
        Switch pin=new Switch(this);pin.setText("Fijar valor numérico en notificaciones");pin.setTextColor(WHITE);pin.setChecked(seed==null||seed.pinNumeric);form.addView(pin,bottomMargin(dp(48),10));
        EditText unit=input(form,"Unidad del valor (opcional)",seed==null?"":seed.numericUnit,"CUP, USD, %, etc.");
        EditText keyword = input(form, "Palabra, estado o umbral numérico",
                seed == null ? "" : seed.keyword, "Disponible, Entregado, En stock...");
        EditText css = input(form, "Selector CSS (opcional)",
                seed == null ? "" : seed.selector, ".estado o #disponibilidad");
        EditText attribute = input(form, "Atributo HTML (opcional)",
                seed == null ? "" : seed.attribute, "aria-label, class, data-status...");
        EditText regex = input(form, "Patrón de extracción (regex, opcional)",
                seed == null ? "" : seed.regex, "Ej.: USD\\s*([0-9]+)");
        TextView tip = text("Los campos técnicos se rellenan automáticamente desde el explorador. Si dejas el selector vacío, Vigilante revisa el texto de toda la web. "
                        + "Para detectar un icono o señal HTML, señala su elemento y atributo.",
                12, MUTED, false);
        form.addView(tip, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 17));

        Spinner engine = spinner(form,"Motor de extracción",Engines.LABELS,seed == null ? 0 : Engines.index(Engines.mode(seed)));
        final int[] waitOptions={3,8,15,25};
        int waitIndex=1;
        if(seed!=null) for(int i=0;i<waitOptions.length;i++) if(waitOptions[i]==seed.renderWaitSeconds) waitIndex=i;
        Spinner renderWait = spinner(form,"Espera JavaScript",new String[]{"3 segundos","8 segundos","15 segundos","25 segundos"},waitIndex);
        int ruleIndex = seed == null ? 0 : Arrays.asList(RULES).indexOf(seed.rule);
        Spinner rule = spinner(form, "Avísame cuando...", RULE_LABELS, ruleIndex);

        String[] intervalLabels = {"15 minutos", "30 minutos", "1 hora", "2 horas", "6 horas", "12 horas", "24 horas"};
        int intervalIndex = seed == null ? 0 : Arrays.asList(INTERVALS).indexOf(seed.intervalMinutes);
        Spinner interval = spinner(form, "Intervalo de comprobación", intervalLabels, intervalIndex);

        EditText times = input(form, "Horas diarias (opcional; reemplazan intervalo)",
                seed == null ? "" : seed.times, "08:00, 12:00, 18:30");
        TextView first = text("Usa formato 24 h. Para números, extrae un único valor sin separadores de miles. Las reglas de cambio registran una lectura inicial; Cada lectura avisa siempre. Android puede retrasar el acceso a la red.",
                12, MUTED, false);
        form.addView(first);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing == null ? "Nueva vigilancia" : "Editar vigilancia")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String web = url.getText().toString().trim();
                    Uri uri = Uri.parse(web);
                    String scheme = uri.getScheme();
                    if (scheme == null || !("http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                        url.setError("Usa una dirección válida: https://sitio.com");
                        return;
                    }

                    String selectedRule = RULES[rule.getSelectedItemPosition()];
                    String phrase = keyword.getText().toString().trim();
                    String selector = css.getText().toString().trim();
                    String attr = attribute.getText().toString().trim();
                    if (!"CHANGES".equals(selectedRule) && !"ALWAYS".equals(selectedRule) && phrase.isEmpty()) {
                        keyword.setError("Indica qué palabra o estado quieres detectar.");
                        return;
                    }
                    if (selector.isEmpty() && !attr.isEmpty()) {
                        css.setError("Para leer un atributo, indica antes el selector CSS.");
                        return;
                    }

                    String schedule, pattern=regex.getText().toString().trim();
                    try { schedule=Scheduler.normalizeTimes(times.getText().toString()); }
                    catch (IllegalArgumentException e) { times.setError(e.getMessage()); return; }
                    try { if (!pattern.isEmpty()) java.util.regex.Pattern.compile(pattern); }
                    catch (Exception e) { regex.setError("Patrón no válido"); return; }
                    if ("ABOVE".equals(selectedRule) || "BELOW".equals(selectedRule)) {
                        try { WebScanner.number(phrase); } catch (Exception e) { keyword.setError("Introduce un número, sin separadores de miles"); return; }
                    }
                    Watch updated = new Watch();
                    updated.times=schedule;
                    updated.regex=pattern;
                    updated.engine=Engines.MODES[engine.getSelectedItemPosition()];
                    updated.preferredEngine=seed==null?"":seed.preferredEngine;
                    if("PLAYWRIGHT".equals(updated.engine)&&!RemoteBrowser.configured(this)){new AlertDialog.Builder(this).setMessage("Configura el puente Playwright en el dashboard.").setPositiveButton("Cerrar",null).show();return;}
                    updated.dynamic=!"HTML".equals(updated.engine);
                    updated.renderWaitSeconds=waitOptions[renderWait.getSelectedItemPosition()];
                    if (editing != null) updated.id = editing.id;
                    updated.name = name.getText().toString().trim();
                    updated.objective=objective.getText().toString().trim();
                    updated.pinNumeric=pin.isChecked();updated.numericUnit=unit.getText().toString().trim();
                    if(updated.numericUnit.length()>20){unit.setError("Máximo 20 caracteres");return;}
                    if (updated.name.isEmpty()) updated.name = uri.getHost();
                    updated.url = web;
                    updated.keyword = phrase;
                    updated.selector = selector;
                    updated.attribute = attr;
                    updated.rule = selectedRule;
                    updated.intervalMinutes = INTERVALS[interval.getSelectedItemPosition()];
                    if (editing != null) {
                        updated.enabled = editing.inboxDraft || editing.enabled;
                        if (editing.sameTargetAs(updated)) {
                            updated.initialized = editing.initialized;
                            updated.lastMatched = editing.lastMatched;
                            updated.lastFingerprint = editing.lastFingerprint;
                            updated.lastPreview = editing.lastPreview;
                            updated.previousValueKnown=editing.previousValueKnown;updated.previousValueChanged=editing.previousValueChanged;
                            updated.lastError = editing.lastError;
                            updated.lastChecked = editing.lastChecked;
                            updated.lastAlert = editing.lastAlert;
                            updated.history = editing.history;
                            updated.actionRequired = editing.actionRequired;
                            if(NumericTrend.unitHint(editing).equals(NumericTrend.unitHint(updated))){
                                updated.lastNumeric=editing.lastNumeric;updated.previousNumeric=editing.previousNumeric;updated.lastNumericAt=editing.lastNumericAt;
                            }
                        }
                    }

                    if(editing!=null && editing.inboxDraft && updated.selector.isEmpty()&&updated.regex.isEmpty()&&updated.keyword.isEmpty()){
                        css.setError("Selecciona el dato que quieres vigilar o indica una palabra.");return;
                    }
                    WatchStore.upsert(this, updated);
                    Scheduler.schedule(this, updated);
                    refreshCards();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDelete(Watch w) {
        new AlertDialog.Builder(this)
                .setTitle("Borrar vigilancia")
                .setMessage("¿Quieres eliminar \"" + w.name + "\"?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (dialog, button) -> {
                    AlarmNotifier.clearAction(this,w.id);
                    AlarmNotifier.clearValue(this,w.id);
                    WatchStore.remove(this, w.id);
                    Scheduler.cancel(this, w.id);
                    refreshCards();
                })
                .show();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==301)for(Watch w:WatchStore.all(this))AlarmNotifier.refreshValue(this,w);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 301);
        }
    }
}
