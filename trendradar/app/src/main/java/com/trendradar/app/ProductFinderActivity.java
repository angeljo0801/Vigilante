package com.trendradar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Product Finder: retailer search, intentionally separate from the TrendRadar findings feed. */
public class ProductFinderActivity extends Activity {
    private static final int BG = 0xff101820, CARD = 0xff1b2933, GREEN = 0xff75dab3, WHITE = 0xffedf1f0;
    private static final String[] STORES = FinderSearchV2.STORES;
    private LinearLayout root, results;
    private EditText query;
    private StoreSelector storeSelector;
    private TextView status;
    private Button more, searchButton;
    private int offset = 0;
    private boolean loading = false;
    private String currentQuery = "";
    private List<String> currentStores = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        drawSearch();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color); b.setCornerRadius(dp(16)); return b;
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4)); return t;
    }
    private TextView heading(String s, int size) {
        TextView t = text(s, size, WHITE);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private void button(LinearLayout parent, String title, Runnable callback) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(WHITE);
        b.setBackground(background(0xff29413f)); parent.addView(b);
        b.setOnClickListener(v -> callback.run());
    }
    private void screen(String title) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32)); root.setBackgroundColor(BG);
        scroll.addView(root); setContentView(scroll);
        root.addView(heading(title, 26));
    }
    private LinearLayout panel(LinearLayout parent) {
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(12), dp(14), dp(12)); p.setBackground(background(CARD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7)); parent.addView(p, lp); return p;
    }
    private void drawSearch() {
        screen("⌕ Product Finder");
        button(root, "← Volver a Tendencias", this::finish);
        root.addView(text("Selecciona una o varias tiendas y busca productos sin modificar tus tendencias ni alertas.", 14, 0xffb7c9c4));
        query = new EditText(this); query.setSingleLine(true); query.setHint("Ej. guantes, auriculares, mochilas");
        query.setHintTextColor(0xff8b9c9a); query.setTextColor(WHITE);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(query);
        storeSelector = new StoreSelector(this);
        root.addView(storeSelector.view());
        searchButton = new Button(this); searchButton.setText("Buscar productos"); searchButton.setAllCaps(false);
        searchButton.setTextColor(WHITE); searchButton.setBackground(background(0xff28594c));
        root.addView(searchButton);
        searchButton.setOnClickListener(v -> {
            offset = 0;
            currentQuery = query.getText().toString().trim();
            currentStores = storeSelector.getSelected();
            if (currentStores.isEmpty()) {
                Toast.makeText(this,"Marca al menos una tienda (círculos blancos).",Toast.LENGTH_LONG).show();
                return;
            }
            if (currentQuery.length() < 2) {
                Toast.makeText(this,"Escribe el nombre del artículo.",Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle("Buscar en " + currentStores.size() + " tienda(s)")
                .setMessage("Se buscará en cada tienda marcada. Se pueden usar hasta " + currentStores.size() +
                    " créditos SerpAPI, menos si hay resultados en caché. ¿Continuar?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Buscar", (dialog,which)->runSearch(true))
                .show();
        });
        button(root, "📷 Buscar por foto · comparar precios", () -> {
            Intent intent = new Intent(this, PhotoFinderActivity.class);
            intent.putStringArrayListExtra("selected_stores",new ArrayList<>(storeSelector.getSelected()));
            startActivity(intent);
        });
        button(root, "◉ Consultar créditos reales de SerpAPI (gratis)", this::showBalance);
        root.addView(text("Modo ahorro: una consulta por cada tienda marcada (si no está en caché), caché 12 horas y reserva de 2 créditos.", 12, 0xffb7c9c4));
        button(root, "⚙ Configurar búsqueda (SerpAPI)", this::settings);
        root.addView(text("La consulta automática utiliza un proveedor de resultados estructurados, sin scraping directo de las tiendas desde el teléfono. Los precios, imágenes y enlaces pueden cambiar.", 12, 0xff9ab0aa));
        status = text("", 14, 0xffb7c9c4); root.addView(status);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        more = new Button(this); more.setText("Cargar más resultados"); more.setAllCaps(false);
        more.setTextColor(WHITE); more.setBackground(background(0xff29413f));
        more.setVisibility(View.GONE); root.addView(more);
        more.setOnClickListener(v -> { offset += 20; runSearch(false); });
        if (!FinderSecrets.hasKey(this)) {
            status.setText("Para mostrar resultados integrados, configura SerpAPI. Puedes abrir directamente cualquiera de las tiendas sin una clave.");
        }
        directStoreLinks(root, "");
    }
    private void showBalance() {
        final String key;
        try { key = FinderSecrets.read(this); }
        catch (Exception e) { status.setText("No se pudo leer la clave."); return; }
        if (key.isEmpty()) { status.setText("Configura primero tu clave SerpAPI."); return; }
        status.setText("Consultando créditos…");
        new Thread(() -> {
            try {
                JSONObject account = FinderBudget.account(key);
                String message = account.optInt("total_searches_left",0) + " créditos disponibles / " +
                    account.optInt("searches_per_month",0) + " mensuales · " +
                    account.optString("plan_name","SerpAPI");
                runOnUiThread(() -> status.setText(message + " · Consulta gratuita."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("No se pudo consultar el saldo: " + e.getMessage()));
            }
        }).start();
    }
    private void directStoreLinks(LinearLayout parent, String searchTerm) {
        parent.addView(heading("Buscar directamente en las tiendas", 17));
        for (int i = 1; i < STORES.length; i++) {
            final String name = STORES[i];
            button(parent, "Abrir " + name, () -> open(FinderSearchV2.storeUrl(name, searchTerm.isEmpty() && query != null ? query.getText().toString().trim() : searchTerm)));
        }
    }
    private void open(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Enlace inválido");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) { Toast.makeText(this, "No se pudo abrir el enlace.", Toast.LENGTH_LONG).show(); }
    }
    private void runSearch(boolean reset) {
        if (loading) return;
        if (currentQuery.length() < 2) { status.setText("Escribe al menos dos caracteres."); return; }
        String secret;
        try { secret = FinderSecrets.read(this); }
        catch (Exception e) { status.setText("Error leyendo la clave: " + e.getMessage()); return; }
        if (secret.isEmpty()) { status.setText("Falta la clave SerpAPI. Abre Configurar búsqueda."); return; }
        loading = true; searchButton.setEnabled(false); more.setVisibility(View.GONE);
        if (reset) results.removeAllViews();
        status.setText("Buscando " + currentQuery + " en " + currentStores.size() + " tienda(s)…");
        final String q = currentQuery;
        final List<String> selected = new ArrayList<>(currentStores);
        final int pageStart = offset;
        new Thread(() -> {
            FinderSearchV2.Result data = FinderSearchV2.searchMany(getApplicationContext(), q, selected, pageStart, secret);
            runOnUiThread(() -> {
                loading = false; searchButton.setEnabled(true);
                status.setText(data.message);
                for (JSONObject item : data.items) addProduct(item);
                if (data.items.isEmpty() && reset) results.addView(text("No hay productos verificados para esta búsqueda. Prueba otro término o abre la tienda directamente.", 14, 0xffb7c9c4));
                more.setVisibility(data.items.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }).start();
    }
    private void addProduct(JSONObject item) {
        LinearLayout p = panel(results);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(row);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(background(0xff253943)); image.setClipToOutline(true);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(106), dp(106));
        row.addView(image, imageParams);
        // RadarImages fetches only the supplied image_url; sources is empty so it never scrapes product pages.
        JSONObject imageEntry = new JSONObject();
        try {
            imageEntry.put("id", item.optString("shop") + ":" + item.optString("url"));
            imageEntry.put("name", item.optString("title"));
            imageEntry.put("category", "Productos");
            imageEntry.put("image_url", item.optString("image"));
            imageEntry.put("sources", new JSONArray());
        } catch (Exception ignored) { }
        RadarImages.show(this, image, imageEntry);
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, 0, 0);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        details.addView(text(item.optString("shop"), 12, GREEN));
        TextView title = heading(item.optString("title", "Producto"), 15);
        title.setMaxLines(3); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(title);
        double price = item.optDouble("price", 0);
        details.addView(heading(price > 0 ? String.format(Locale.US, "$%,.2f", price) : "Precio no disponible", 16));
        if (!item.optString("condition","").isEmpty()) details.addView(text("Estado: " + item.optString("condition"), 12, 0xffb7c9c4));
        if (item.optBoolean("wholesale")) details.addView(text("Mayorista · verificar precio por caja, unidades y envío", 12, 0xffb7c9c4));
        double rating = item.optDouble("rating", 0);
        int reviews = item.optInt("reviews", 0);
        if (rating > 0) details.addView(text(String.format(Locale.US, "★ %.1f%s", rating, reviews > 0 ? " · " + reviews + " reseñas" : ""), 12, 0xffb7c9c4));
        String url = item.optString("url");
        if (!url.isEmpty()) button(p, item.optBoolean("direct") ? "Ver producto en " + item.optString("shop") : "Abrir ficha del producto", () -> open(url));
    }
    private void settings() {
        screen("Product Finder · Configuración");
        button(root, "← Volver", this::drawSearch);
        root.addView(text("Para consultas dentro de la aplicación necesitas una clave SerpAPI personal. La clave no se incluye en el APK y se cifra en este dispositivo con AndroidKeyStore.", 14, 0xffb7c9c4));
        root.addView(text("SerpAPI procesa búsquedas de Google Shopping, Google, Amazon, eBay y Google Lens por imagen; puede tener cuotas y costes. Las fotos subidas se procesan por un tercero y SerpAPI declara conservarlas hasta 30 días para depuración.", 13, 0xffb7c9c4));
        EditText input = new EditText(this); input.setHint("Clave SerpAPI");
        input.setHintTextColor(0xff8b9c9a); input.setTextColor(WHITE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(input);
        button(root, "Guardar clave", () -> {
            if (input.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Introduce una clave para guardarla.", Toast.LENGTH_LONG).show(); return;
            }
            try {
                FinderSecrets.save(this, input.getText().toString().trim());
                Toast.makeText(this, "Clave cifrada y guardada.", Toast.LENGTH_LONG).show();
                drawSearch();
            } catch (Exception e) { Toast.makeText(this, "No se pudo guardar la clave.", Toast.LENGTH_LONG).show(); }
        });
        button(root, "Borrar clave guardada", () -> {
            FinderSecrets.clear(this);
            Toast.makeText(this, "Clave eliminada del dispositivo.", Toast.LENGTH_SHORT).show(); drawSearch();
        });
        button(root, "Abrir página de SerpAPI", () -> open("https://serpapi.com/"));
        root.addView(text("Se recomienda un backend propio con la clave como variable de entorno para distribuir la app. Esta versión hace consultas directas solo con tu clave local.", 12, 0xff9ab0aa));
    }

    /** Reuse the existing encrypted SerpAPI key for image lookup; never copy it to disk. */
    static String apiKey(Context context) throws Exception { return FinderSecrets.read(context); }

    /** Retailer offers based on a title inferred from a photograph. */
    static List<JSONObject> searchPhotoOffers(Context context, String query, String key, Collection<String> stores) {
        return FinderSearchV2.searchMany(context, query, stores, 0, key).items;
    }

    /** Independent keystore alias; never reuses the GitHub synchronization token. */
    private static final class FinderSecrets {
        private static final String ALIAS = "trendradar-product-finder", PREF = "product_finder_key";
        static boolean hasKey(Context c) { return Store.prefs(c).contains(PREF); }
        private static SecretKey key() throws Exception {
            KeyStore k = KeyStore.getInstance("AndroidKeyStore"); k.load(null);
            if (!k.containsAlias(ALIAS)) {
                KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
                g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                g.generateKey();
            }
            return (SecretKey) k.getKey(ALIAS, null);
        }
        static void save(Context c, String secret) throws Exception {
            Cipher a = Cipher.getInstance("AES/GCM/NoPadding"); a.init(Cipher.ENCRYPT_MODE, key());
            String data = Base64.encodeToString(a.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(a.doFinal(secret.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            Store.prefs(c).edit().putString(PREF, data).apply();
        }
        static String read(Context c) throws Exception {
            String stored = Store.prefs(c).getString(PREF, "");
            if (stored.isEmpty()) return "";
            String[] parts = stored.split(":", 2);
            Cipher a = Cipher.getInstance("AES/GCM/NoPadding");
            a.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(a.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        }
        static void clear(Context c) { Store.prefs(c).edit().remove(PREF).apply(); }
    }

    /** Search APIs only. Does not fetch or parse the retailers' product webpages. */
    private static final class FinderSearch {
        static class Response {
            final List<JSONObject> items = new ArrayList<>();
            String message = "";
        }
        static class Part {
            final String shop; final List<JSONObject> items = new ArrayList<>();
            String error = "";
            Part(String shop) { this.shop = shop; }
        }
        private static String enc(String value) {
            try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception e) { return value; }
        }
        static String storeSearchUrl(String shop, String q) {
            switch (shop) {
                case "Amazon": return "https://www.amazon.com/s?k=" + enc(q);
                case "SHEIN": return "https://us.shein.com/pdsearch/" + enc(q) + "/";
                case "Temu": return "https://www.temu.com/search_result.html?search_key=" + enc(q);
                default: return "https://www.walmart.com/search?q=" + enc(q);
            }
        }
        private static String host(String url) {
            try {
                URI u = new URI(url); return "https".equalsIgnoreCase(u.getScheme()) ? (u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.US)) : "";
            } catch (Exception e) { return ""; }
        }
        private static boolean shopHost(String shop, String url) {
            String h = host(url), domain;
            switch (shop) {
                case "Amazon": domain = "amazon.com"; break;
                case "SHEIN": domain = "shein.com"; break;
                case "Temu": domain = "temu.com"; break;
                default: domain = "walmart.com";
            }
            return h.equals(domain) || h.endsWith("." + domain);
        }
        private static JSONObject get(String url) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(14000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            try {
                int status = c.getResponseCode();
                if (status != 200) throw new IOException("Proveedor HTTP " + status);
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) != -1) {
                        if (out.size() + n > 3000000) throw new IOException("Respuesta demasiado grande");
                        out.write(b, 0, n);
                    }
                    JSONObject j = new JSONObject(out.toString("UTF-8"));
                    if (j.has("error")) throw new IOException(j.optString("error"));
                    return j;
                }
            } finally { c.disconnect(); }
        }
        private static JSONObject product(String shop, String title, double price, double rating, int reviews, String image, String link) {
            JSONObject j = new JSONObject();
            try {
                j.put("shop", shop); j.put("title", title); j.put("price", price);
                j.put("rating", rating); j.put("reviews", reviews);
                j.put("image", image.startsWith("https://") ? image : "");
                j.put("url", link); j.put("direct", shopHost(shop, link));
            } catch (Exception ignored) { }
            return j;
        }
        private static String image(JSONObject o) {
            String t = o.optString("thumbnail", "");
            if (t.isEmpty()) t = o.optString("serpapi_thumbnail", "");
            return t;
        }
        private static int count(JSONObject o, String key) {
            Object v = o.opt(key);
            if (v instanceof Number) return ((Number) v).intValue();
            String s = String.valueOf(v == null ? "" : v).replaceAll("[^0-9]", "");
            try { return s.isEmpty() ? 0 : Integer.parseInt(s); } catch (Exception e) { return 0; }
        }
        private static double price(JSONObject o) {
            double p = o.optDouble("extracted_price", 0);
            if (p > 0) return p;
            Object raw = o.opt("price");
            if (raw instanceof JSONObject) return ((JSONObject) raw).optDouble("extracted_price", 0);
            if (raw instanceof Number) return ((Number) raw).doubleValue();
            return 0; // Never guess a numeric price from snippets.
        }
        private static boolean merchant(String shop, JSONObject o) {
            String source = o.optString("source", "").toLowerCase(Locale.US);
            String s = shop.toLowerCase(Locale.US);
            return source.equals(s) || source.startsWith(s + ".") || source.startsWith(s + " ") || source.contains(" " + s + ".");
        }
        private static void addUnique(Part p, JSONObject item, Set<String> seen) {
            String link = item.optString("url");
            String unique = link.isEmpty() ? item.optString("shop") + "/" + item.optString("title").toLowerCase(Locale.US) : link.split("[?#]", 2)[0];
            if (!item.optString("title").isEmpty() && seen.add(unique)) p.items.add(item);
        }
        static Part searchShop(String shop, String query, int offset, String key) {
            Part part = new Part(shop);
            Set<String> seen = new HashSet<>();
            try {
                if (shop.equals("Amazon")) {
                    JSONObject data = get("https://serpapi.com/search.json?engine=amazon&amazon_domain=amazon.com&language=en_US&k=" + enc(query) + "&page=" + (offset / 20 + 1) + "&api_key=" + enc(key));
                    JSONArray a = data.optJSONArray("organic_results");
                    if (a != null) for (int i = 0; i < a.length() && part.items.size() < 20; i++) {
                        JSONObject x = a.optJSONObject(i); if (x == null) continue;
                        String link = x.optString("link_clean", x.optString("link", ""));
                        String asin = x.optString("asin", "");
                        if (!shopHost(shop, link) && !asin.isEmpty()) link = "https://www.amazon.com/dp/" + enc(asin);
                        if (!shopHost(shop, link)) continue;
                        addUnique(part, product(shop, x.optString("title"), price(x), x.optDouble("rating", 0), count(x, "reviews"), image(x), link), seen);
                    }
                } else {
                    JSONObject data = get("https://serpapi.com/search.json?engine=google_shopping&gl=us&hl=en&q=" + enc(query + " " + shop) + "&start=" + offset + "&api_key=" + enc(key));
                    JSONArray a = data.optJSONArray("shopping_results");
                    if (a == null) a = data.optJSONArray("inline_shopping_results");
                    if (a != null) for (int i = 0; i < a.length() && part.items.size() < 20; i++) {
                        JSONObject x = a.optJSONObject(i); if (x == null || !merchant(shop, x)) continue;
                        String link = x.optString("link", x.optString("product_link", ""));
                        if (host(link).isEmpty()) link = x.optString("product_link", "");
                        if (host(link).isEmpty()) continue;
                        addUnique(part, product(shop, x.optString("title"), price(x), x.optDouble("rating", 0), count(x, "reviews"), image(x), link), seen);
                    }
                    // Indexed retailer pages fill gaps; these generally have no verifiable price.
                    if (part.items.size() < 5) {
                        String domain = shop.equals("SHEIN") ? "shein.com" : shop.equals("Temu") ? "temu.com" : "walmart.com";
                        JSONObject indexed = get("https://serpapi.com/search.json?engine=google&gl=us&hl=en&q=" + enc("site:" + domain + " " + query) + "&start=" + offset + "&api_key=" + enc(key));
                        JSONArray organic = indexed.optJSONArray("organic_results");
                        if (organic != null) for (int i = 0; i < organic.length() && part.items.size() < 25; i++) {
                            JSONObject x = organic.optJSONObject(i); if (x == null) continue;
                            String link = x.optString("link", "");
                            if (!shopHost(shop, link)) continue;
                            addUnique(part, product(shop, x.optString("title"), 0, 0, 0, image(x), link), seen);
                        }
                    }
                }
            } catch (Exception e) {
                part.error = e.getMessage() == null ? "Error de consulta" : e.getMessage();
            }
            return part;
        }
        static Response search(String query, String selected, int offset, String key) {
            Response response = new Response();
            List<String> shops = "Todas".equals(selected) ? Arrays.asList("Amazon", "SHEIN", "Temu", "Walmart") : Collections.singletonList(selected);
            ExecutorService pool = Executors.newFixedThreadPool(shops.size());
            List<Future<Part>> jobs = new ArrayList<>();
            for (String shop : shops) jobs.add(pool.submit(() -> searchShop(shop, query, offset, key)));
            int failures = 0;
            StringBuilder errors = new StringBuilder();
            for (int i = 0; i < jobs.size(); i++) {
                try {
                    Part p = jobs.get(i).get(32, TimeUnit.SECONDS);
                    response.items.addAll(p.items);
                    if (!p.error.isEmpty()) { failures++; errors.append(p.shop).append(": ").append(p.error).append(". "); }
                } catch (Exception ex) { failures++; jobs.get(i).cancel(true); errors.append(shops.get(i)).append(": tiempo de espera. "); }
            }
            pool.shutdownNow();
            response.message = response.items.size() + " productos encontrados" + (failures > 0 ? " · " + failures + " tienda(s) con error. " + errors : "") + " · Datos sujetos a cambios.";
            return response;
        }
    }
}
