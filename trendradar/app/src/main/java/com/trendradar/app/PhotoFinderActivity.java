package com.trendradar.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Photo comparison using SerpAPI's Image API and Google Lens API.
 * No retailer-page scraping; price matches may refer to visually similar models.
 */
public class PhotoFinderActivity extends Activity {
    private static final int REQUEST_IMAGE = 7081;
    private static final int BG = 0xff101820, PANEL = 0xff1b2933;
    private static final int GREEN = 0xff75dab3, TEXT = 0xffedf1f0, MUTED = 0xffb6c8c5;
    private LinearLayout root, output;
    private ImageView preview;
    private EditText productName;
    private TextView state;
    private Button searchButton;
    private byte[] imageBytes;
    private boolean loading = false;
    private String inferred = "";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        draw();
    }
    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }
    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(16)); return drawable;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(5), 0, dp(5));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private void button(LinearLayout parent, String value, Runnable fn) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false);
        b.setTextColor(TEXT); b.setBackground(background(0xff284940));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(5), 0, dp(6)); parent.addView(b, lp);
        b.setOnClickListener(v -> fn.run());
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(12), dp(14), dp(12));
        p.setBackground(background(PANEL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(9)); parent.addView(p, lp);
        return p;
    }
    private void draw() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setBackgroundColor(BG);
        scroll.addView(root); setContentView(scroll);
        root.addView(label("📷 Buscar por foto", 25, GREEN, true));
        button(root, "← Product Finder", this::finish);
        root.addView(label("Selecciona una foto y compara precios publicados en Amazon, SHEIN, Temu y Walmart.", 14, MUTED, false));
        preview = new ImageView(this);
        preview.setBackground(background(PANEL));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageResource(android.R.drawable.ic_menu_gallery);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, dp(185));
        imageLp.setMargins(0, dp(6), 0, dp(8)); root.addView(preview, imageLp);
        button(root, "Seleccionar foto de galería o archivos", this::pickImage);
        productName = new EditText(this);
        productName.setSingleLine(false);
        productName.setMaxLines(2);
        productName.setTextColor(TEXT);
        productName.setHintTextColor(MUTED);
        productName.setHint("Marca o modelo exacto (opcional)");
        root.addView(productName);
        root.addView(label("Para comparar el mismo modelo, escribe marca, referencia, talla o capacidad cuando la sepas. Sin esos datos, algunas ofertas pueden ser similares.", 12, MUTED, false));
        searchButton = new Button(this);
        searchButton.setText("Identificar foto y buscar el menor precio");
        searchButton.setAllCaps(false); searchButton.setTextColor(TEXT);
        searchButton.setBackground(background(0xff28594c));
        root.addView(searchButton);
        searchButton.setOnClickListener(v -> search());
        state = label("La fotografía se enviará a SerpAPI/Google Lens para localizar coincidencias. SerpAPI informa que conserva imágenes hasta 30 días para depuración. No subas fotos privadas.", 12, MUTED, false);
        root.addView(state);
        output = new LinearLayout(this); output.setOrientation(LinearLayout.VERTICAL);
        root.addView(output);
        root.addView(label("Los importes son precios publicados, no totales finales: pueden faltar envío, impuestos y descuentos. No se garantiza cobertura completa ni identidad exacta del artículo.", 12, MUTED, false));
    }
    private void pickImage() {
        if (loading) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
    }
    @Override protected void onActivityResult(int request, int resultCode, Intent data) {
        super.onActivityResult(request, resultCode, data);
        if (request != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        state.setText("Preparando la fotografía…"); output.removeAllViews();
        new Thread(() -> {
            try {
                Bitmap picture = loadScaled(uri);
                byte[] bytes = compress(picture);
                runOnUiThread(() -> {
                    imageBytes = bytes; inferred = ""; preview.setImageBitmap(picture);
                    state.setText("Foto preparada para búsqueda (" + (bytes.length / 1024) + " KB). Pulsa identificar.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    imageBytes = null;
                    state.setText("No pude abrir esta foto. Prueba otra imagen JPG, PNG o WebP: " + safe(e));
                });
            }
        }).start();
    }
    private Bitmap loadScaled(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Archivo no disponible");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth < 16 || bounds.outHeight < 16) throw new IOException("Imagen demasiado pequeña o no válida");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 1400 || bounds.outHeight / options.inSampleSize > 1400)
            options.inSampleSize *= 2;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Archivo no disponible");
            b = BitmapFactory.decodeStream(in, null, options);
        }
        if (b == null) throw new IOException("No se pudo decodificar");
        int angle = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) angle = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) angle = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) angle = 270;
            }
        } catch (Exception ignored) {}
        if (angle != 0) {
            Matrix matrix = new Matrix(); matrix.postRotate(angle);
            Bitmap turned = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
            if (turned != b) b.recycle();
            b = turned;
        }
        return b;
    }
    /** SerpAPI's Image API requires no more than 500 KB. */
    private byte[] compress(Bitmap source) throws IOException {
        Bitmap b = source;
        for (int attempt = 0; attempt < 12; attempt++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, Math.max(46, 86 - 6 * attempt), out);
            if (out.size() < 470000) return out.toByteArray();
            int w = Math.max(200, (int)(b.getWidth() * .85));
            int h = Math.max(200, (int)(b.getHeight() * .85));
            if (w == b.getWidth() && h == b.getHeight()) break;
            Bitmap smaller = Bitmap.createScaledBitmap(b, w, h, true);
            if (b != source) b.recycle();
            b = smaller;
        }
        if (b != source) b.recycle();
        throw new IOException("No se pudo comprimir a menos de 500 KB");
    }
    private void search() {
        if (loading) return;
        if (imageBytes == null) { state.setText("Selecciona primero una imagen."); return; }
        String key;
        try { key = ProductFinderActivity.apiKey(this); }
        catch (Exception e) { state.setText("No se puede leer la clave SerpAPI: " + safe(e)); return; }
        if (key.isEmpty()) { state.setText("Configura tu clave SerpAPI desde Product Finder → Configurar búsqueda."); return; }
        final byte[] bytes = imageBytes;
        final String manual = productName.getText().toString().trim();
        loading = true; searchButton.setEnabled(false);
        output.removeAllViews(); state.setText("Buscando coincidencias visuales…");
        new Thread(() -> {
            try {
                LensData lens = LensClient.lookup(bytes, key);
                String term = manual.isEmpty() ? lens.guess : manual;
                if (term.length() > 110) term = term.substring(0, 110);
                List<JSONObject> byName = new ArrayList<>();
                if (term.length() >= 3) {
                    final String finalTerm = term;
                    runOnUiThread(() -> state.setText("Comparando ofertas de las cuatro tiendas para: " + finalTerm));
                    byName = ProductFinderActivity.searchPhotoOffers(term, key);
                }
                final List<JSONObject> named = byName;
                final String searched = term;
                runOnUiThread(() -> render(lens, named, searched, manual.isEmpty()));
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    loading = false; searchButton.setEnabled(true);
                    state.setText("La búsqueda por imagen falló: " + safe(ex) + ". Verifica la clave, cuota y conexión.");
                });
            }
        }).start();
    }
    private void render(LensData lens, List<JSONObject> nameResults, String term, boolean inferredAutomatically) {
        loading = false; searchButton.setEnabled(true);
        output.removeAllViews();
        state.setText("Se analizaron coincidencias de Google Lens y catálogos indexados. Revisa cada variante antes de comprar.");
        if (inferredAutomatically && !term.isEmpty()) productName.setText(term);
        if (!term.isEmpty()) {
            LinearLayout info = card(output);
            info.addView(label("Artículo identificado / término de búsqueda", 13, GREEN, true));
            info.addView(label(term, 16, TEXT, true));
            info.addView(label(inferredAutomatically ? "Término inferido de una coincidencia visual: puede no representar el modelo exacto. Edita el nombre arriba y repite la búsqueda si es necesario." : "Se ha utilizado la identificación escrita por ti.", 12, MUTED, false));
        }
        Map<String, JSONObject> visualMap = new LinkedHashMap<>();
        for (JSONObject offer : lens.matches) visualMap.put(offer.optString("url"), offer);
        List<JSONObject> visual = new ArrayList<>(visualMap.values());
        Set<String> seen = new HashSet<>(visualMap.keySet());
        List<JSONObject> byText = new ArrayList<>();
        for (JSONObject p : nameResults) {
            String url = p.optString("url");
            if (!url.isEmpty() && seen.add(url)) byText.add(p);
        }
        Comparator<JSONObject> cheapestFirst = (a,b) -> {
            double pa = a.optDouble("price", 0), pb = b.optDouble("price", 0);
            if (pa <= 0) pa = Double.POSITIVE_INFINITY;
            if (pb <= 0) pb = Double.POSITIVE_INFINITY;
            return Double.compare(pa, pb);
        };
        visual.sort(cheapestFirst); byText.sort(cheapestFirst);
        JSONObject visualBest = firstPriced(visual);
        JSONObject nameBest = firstPriced(byText);
        if (visualBest != null) {
            summary("Precio más bajo entre coincidencias visuales", visualBest, "No confirma marca, modelo, talla o estado.");
        } else if (nameBest != null) {
            summary("Menor precio observado entre alternativas por nombre", nameBest, "No hubo precios visuales comparables; verifica que sea el mismo artículo.");
        } else {
            LinearLayout empty = card(output);
            empty.addView(label("Sin precios comparables disponibles", 18, TEXT, true));
            empty.addView(label("Google Lens puede identificar imágenes sin publicar precios. Puedes abrir los artículos encontrados o buscar con otro nombre.", 13, MUTED, false));
        }
        if (visualBest != null && nameBest != null && nameBest.optDouble("price", 0) < visualBest.optDouble("price", 0)) {
            LinearLayout cheaper = card(output);
            cheaper.addView(label("Hay una alternativa por nombre más barata: " + money(nameBest.optDouble("price")) + " · " + nameBest.optString("shop"), 14, GREEN, true));
            cheaper.addView(label("Puede ser otro modelo. Comprueba el artículo antes de considerarlo una oferta equivalente.", 12, MUTED, false));
        }
        output.addView(label("Coincidencias visuales · " + visual.size(), 19, GREEN, true));
        if (visual.isEmpty()) output.addView(label("No aparecieron ofertas visuales dentro de las cuatro tiendas.", 13, MUTED, false));
        for (JSONObject offer : visual) offerCard(offer, "Imagen coincidente · verificar referencia");
        output.addView(label("Otras opciones por nombre · " + byText.size(), 19, GREEN, true));
        if (byText.isEmpty()) output.addView(label("No aparecieron alternativas adicionales por texto, o no se pudo identificar un nombre útil.", 13, MUTED, false));
        for (JSONObject offer : byText) offerCard(offer, "Búsqueda textual · producto similar o variante");
        output.addView(label("El más barato significa el mínimo entre los resultados obtenidos, no el menor precio garantizado de todo Internet. Revisa disponibilidad, unidades, modelo, impuestos y envío.", 12, MUTED, false));
    }
    private JSONObject firstPriced(List<JSONObject> items) {
        for (JSONObject o : items) if (o.optDouble("price", 0) > 0) return o;
        return null;
    }
    private String money(double x) { return String.format(Locale.US, "$%,.2f", x); }
    private void summary(String caption, JSONObject offer, String caveat) {
        LinearLayout p = card(output);
        p.setBackground(background(0xff27483f));
        p.addView(label(caption, 14, GREEN, true));
        p.addView(label(money(offer.optDouble("price",0)), 31, TEXT, true));
        p.addView(label(offer.optString("shop") + " · " + offer.optString("title"), 14, TEXT, true));
        p.addView(label(caveat, 12, MUTED, false));
        button(p, "Ver oferta", () -> open(offer.optString("url")));
    }
    private void offerCard(JSONObject offer, String subtitle) {
        LinearLayout p = card(output);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(row);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(background(0xff22343b));
        img.setClipToOutline(true);
        row.addView(img, new LinearLayout.LayoutParams(dp(100), dp(100)));
        JSONObject image = new JSONObject();
        try {
            image.put("id", offer.optString("shop") + ":" + offer.optString("url"));
            image.put("name", offer.optString("title"));
            image.put("category", "Productos");
            image.put("image_url", offer.optString("image"));
            image.put("sources", new JSONArray());
        } catch (Exception ignored) {}
        RadarImages.show(this, img, image);
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(12), 0, 0, 0);
        content.setOrientation(LinearLayout.VERTICAL);
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(label(offer.optString("shop"), 12, GREEN, true));
        TextView name = label(offer.optString("title", "Artículo"), 15, TEXT, true);
        name.setMaxLines(3); name.setEllipsize(TextUtils.TruncateAt.END); content.addView(name);
        double price = offer.optDouble("price", 0);
        content.addView(label(price > 0 ? money(price) : "Precio no disponible", 16, TEXT, true));
        p.addView(label(subtitle, 12, MUTED, false));
        if (offer.optBoolean("out_of_stock")) p.addView(label("Agotado en la fuente", 12, 0xffffba7c, false));
        if (!offer.optString("url").isEmpty()) button(p, "Abrir artículo en tienda", () -> open(offer.optString("url")));
    }
    private void open(String link) {
        try {
            Uri u = Uri.parse(link);
            if (!"https".equalsIgnoreCase(u.getScheme())) throw new IllegalArgumentException();
            startActivity(new Intent(Intent.ACTION_VIEW,u));
        } catch (Exception e) { Toast.makeText(this,"Enlace no disponible",Toast.LENGTH_SHORT).show(); }
    }
    private String safe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static class LensData {
        final List<JSONObject> matches = new ArrayList<>();
        String guess = "";
    }
    private static class LensClient {
        static String enc(String value) { try { return URLEncoder.encode(value,"UTF-8"); } catch (Exception e) { return value; } }
        private static JSONObject readJson(InputStream stream, int limit) throws Exception {
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > limit) throw new IOException("Respuesta demasiado grande");
                    out.write(b,0,n);
                }
                JSONObject json = new JSONObject(out.toString("UTF-8"));
                if (json.has("error")) throw new IOException(json.optString("error"));
                return json;
            }
        }
        static JSONObject postImage(byte[] jpg, String key) throws Exception {
            String boundary = "----TrendRadarImage" + System.nanoTime();
            HttpURLConnection c = (HttpURLConnection) new URL("https://serpapi.com/image").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000); c.setReadTimeout(30000);
            c.setRequestProperty("Content-Type","multipart/form-data; boundary=" + boundary);
            try {
                try (OutputStream out = c.getOutputStream()) {
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"api_key\"\r\n\r\n" + key + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"product.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(jpg);
                    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int status = c.getResponseCode();
                if (status != 200 && status != 201) throw new IOException("Error al subir imagen HTTP " + status);
                return readJson(c.getInputStream(),200000);
            } finally { c.disconnect(); }
        }
        static JSONObject getLens(String id, String key) throws Exception {
            String url = "https://serpapi.com/search.json?engine=google_lens&type=visual_matches&country=us&hl=en&image_id=" + enc(id) + "&api_key=" + enc(key);
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000); c.setReadTimeout(30000);
            try {
                if (c.getResponseCode() != 200) throw new IOException("Google Lens HTTP " + c.getResponseCode());
                return readJson(c.getInputStream(),3500000);
            } finally { c.disconnect(); }
        }
        static String shop(String link) {
            try {
                URI uri = new URI(link);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return "";
                String host = uri.getHost().toLowerCase(Locale.US);
                if (host.equals("amazon.com") || host.endsWith(".amazon.com")) return "Amazon";
                if (host.equals("shein.com") || host.endsWith(".shein.com")) return "SHEIN";
                if (host.equals("temu.com") || host.endsWith(".temu.com")) return "Temu";
                if (host.equals("walmart.com") || host.endsWith(".walmart.com")) return "Walmart";
            } catch (Exception ignored) { }
            return "";
        }
        static double priceUSD(JSONObject x) {
            JSONObject p = x.optJSONObject("price");
            if (p == null) return 0;
            String currency = p.optString("currency","").trim();
            String value = p.optString("value","").trim();
            boolean usd = "USD".equalsIgnoreCase(currency) || "US$".equalsIgnoreCase(currency) ||
                "$".equals(currency) || (currency.isEmpty() && value.startsWith("$"));
            return usd ? Math.max(0,p.optDouble("extracted_value",0)) : 0;
        }
        static String guessFromTitle(String title) {
            String candidate = title.replaceAll("\\s*[|•].*$","").replaceAll("\\s+[-–]\\s+(Amazon|Walmart|Temu|SHEIN).*","").trim();
            if (candidate.length() > 110) candidate = candidate.substring(0,110);
            return candidate;
        }
        static LensData lookup(byte[] bytes, String key) throws Exception {
            JSONObject upload = postImage(bytes,key);
            String id = upload.optString("image_id","");
            if (id.isEmpty()) throw new IOException("El proveedor no devolvió image_id");
            JSONObject result = getLens(id,key);
            LensData data = new LensData();
            JSONArray matches = result.optJSONArray("visual_matches");
            if (matches == null) matches = new JSONArray();
            Set<String> seen = new HashSet<>();
            String fallback = "";
            for (int i=0; i<matches.length(); i++) {
                JSONObject x = matches.optJSONObject(i); if (x == null) continue;
                String title = x.optString("title","").trim();
                if (title.length() < 6) continue;
                if (fallback.isEmpty()) fallback = guessFromTitle(title);
                String link = x.optString("link","");
                String shop = shop(link);
                if (shop.isEmpty() || !seen.add(link)) continue;
                if (data.guess.isEmpty()) data.guess = guessFromTitle(title);
                double price = priceUSD(x);
                if (x.has("in_stock") && !x.optBoolean("in_stock",true)) price = 0;
                JSONObject item = new JSONObject();
                item.put("shop",shop); item.put("title",title);
                item.put("price",price); item.put("url",link);
                item.put("image",x.optString("thumbnail",x.optString("image","")));
                item.put("rating",x.optDouble("rating",0));
                item.put("reviews",x.optInt("reviews",0));
                item.put("out_of_stock",x.has("in_stock") && !x.optBoolean("in_stock",true));
                data.matches.add(item);
            }
            if (data.guess.isEmpty()) data.guess = fallback;
            return data;
        }
    }
}
