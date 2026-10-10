package com.vigilante.app;

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
    private static final String[] RULES = {"APPEARS", "DISAPPEARS", "CHANGES"};
    private static final String[] RULE_LABELS = {"Cuando APARECE", "Cuando DESAPARECE", "Cuando CAMBIA"};
    private static final Integer[] INTERVALS = {15, 30, 60, 120};
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cardList != null) refreshCards();
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

        TextView sub = text("Tu bot revisa páginas y te avisa cuando sucede lo que esperas.",
                15, MUTED, false);
        root.addView(sub, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 23));

        LinearLayout status = column();
        status.setPadding(dp(17), dp(16), dp(17), dp(15));
        status.setBackground(rounded(CARD, 17, BORDER));
        statistics = text("", 17, WHITE, true);
        status.addView(statistics, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 7));
        TextView info = text("Vigilancia en segundo plano · Android revisa a intervalos, no al segundo.",
                12, MUTED, false);
        status.addView(info);
        root.addView(status, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 17));

        Button add = button("＋  Nueva vigilancia", BLUE);
        add.setTextSize(16);
        add.setOnClickListener(v -> showEditor(null));
        root.addView(add, bottomMargin(dp(53), 23));

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

        TextView foot = text("Las páginas que necesitan inicio de sesión, CAPTCHA o JavaScript dinámico "
                + "requieren una modalidad de vigilancia desde servidor.", 12, MUTED, false);
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

        String trigger = "CHANGES".equals(w.rule) ? "Cambios en el texto/atributo"
                : ("DISAPPEARS".equals(w.rule) ? "Desaparece: " : "Aparece: ") + w.keyword;
        TextView target = text("◎  " + trigger, 14, WHITE, true);
        card.addView(target, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 4));

        String scope = w.selector.isEmpty() ? "Página completa" : "Selector: " + w.selector;
        if (!w.attribute.isEmpty()) scope += " · atributo: " + w.attribute;
        scope += " · cada " + w.intervalMinutes + " min";
        TextView scopeView = text(scope, 12, MUTED, false);
        card.addView(scopeView, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 10));

        String state;
        int stateColor = MUTED;
        if (!w.enabled) {
            state = "⏸  En pausa";
        } else if (!w.lastError.isEmpty()) {
            state = "⚠  " + w.lastError;
            stateColor = Color.rgb(255, 190, 118);
        } else if (!w.initialized) {
            state = "◌  Esperando primera comprobación";
        } else if ("CHANGES".equals(w.rule)) {
            state = "✓  Estado inicial registrado";
            stateColor = GREEN;
        } else {
            state = w.lastMatched ? "✓  Palabra presente" : "○  Palabra no detectada";
            if (w.lastMatched) stateColor = GREEN;
        }
        TextView status = text(state, 13, stateColor, true);
        card.addView(status, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        String when = w.lastChecked == 0 ? "Sin comprobaciones"
                : "Última revisión: " + new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                        .format(new Date(w.lastChecked));
        TextView date = text(when, 12, MUTED, false);
        card.addView(date, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 13));

        LinearLayout actions = row();
        Button check = button("Comprobar", BLUE);
        check.setTextSize(12);
        check.setOnClickListener(v -> checkNow(w.id, check));
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
        cardList.addView(card, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 12));
    }

    private void checkNow(String id, Button origin) {
        origin.setEnabled(false);
        origin.setText("Revisando...");
        background.execute(() -> {
            String result = MonitorRunner.check(getApplicationContext(), id);
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

    private void showEditor(Watch editing) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = column();
        form.setPadding(dp(20), dp(18), dp(20), dp(7));
        scroll.addView(form);

        EditText name = input(form, "Nombre de la vigilancia",
                editing == null ? "" : editing.name, "Por ejemplo: Producto disponible");
        EditText url = input(form, "Dirección web (URL)",
                editing == null ? "" : editing.url, "https://sitio.com/producto");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText keyword = input(form, "Palabra o estado que buscas",
                editing == null ? "" : editing.keyword, "Disponible, Entregado, En stock...");
        EditText css = input(form, "Selector CSS (opcional)",
                editing == null ? "" : editing.selector, ".estado o #disponibilidad");
        EditText attribute = input(form, "Atributo HTML (opcional)",
                editing == null ? "" : editing.attribute, "aria-label, class, data-status...");
        TextView tip = text("Si dejas el selector vacío, Vigilante revisa el texto de toda la web. "
                        + "Para detectar un icono o señal HTML, señala su elemento y atributo.",
                12, MUTED, false);
        form.addView(tip, bottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, 17));

        int ruleIndex = editing == null ? 0 : Arrays.asList(RULES).indexOf(editing.rule);
        Spinner rule = spinner(form, "Avísame cuando...", RULE_LABELS, ruleIndex);

        String[] intervalLabels = {"15 minutos", "30 minutos", "1 hora", "2 horas"};
        int intervalIndex = editing == null ? 0 : Arrays.asList(INTERVALS).indexOf(editing.intervalMinutes);
        Spinner interval = spinner(form, "Intervalo de comprobación", intervalLabels, intervalIndex);

        TextView first = text("La primera comprobación registra el estado sin lanzar una alarma. "
                + "Las siguientes te alertarán cuando ocurra la transición seleccionada.",
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
                    if (!"CHANGES".equals(selectedRule) && phrase.isEmpty()) {
                        keyword.setError("Indica qué palabra o estado quieres detectar.");
                        return;
                    }
                    if (selector.isEmpty() && !attr.isEmpty()) {
                        css.setError("Para leer un atributo, indica antes el selector CSS.");
                        return;
                    }

                    Watch updated = new Watch();
                    if (editing != null) updated.id = editing.id;
                    updated.name = name.getText().toString().trim();
                    if (updated.name.isEmpty()) updated.name = uri.getHost();
                    updated.url = web;
                    updated.keyword = phrase;
                    updated.selector = selector;
                    updated.attribute = attr;
                    updated.rule = selectedRule;
                    updated.intervalMinutes = INTERVALS[interval.getSelectedItemPosition()];
                    if (editing != null) {
                        updated.enabled = editing.enabled;
                        if (editing.sameTargetAs(updated)) {
                            updated.initialized = editing.initialized;
                            updated.lastMatched = editing.lastMatched;
                            updated.lastFingerprint = editing.lastFingerprint;
                            updated.lastPreview = editing.lastPreview;
                            updated.lastError = editing.lastError;
                            updated.lastChecked = editing.lastChecked;
                            updated.lastAlert = editing.lastAlert;
                        }
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
                    WatchStore.remove(this, w.id);
                    Scheduler.cancel(this, w.id);
                    refreshCards();
                })
                .show();
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
