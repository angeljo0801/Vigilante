package com.vigilante.app;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class WebScanner {
    private WebScanner() {}

    public static final class Observation {
        public final String observed;
        public final String fingerprint;
        public final boolean matched;
        Observation(String observed, String fingerprint, boolean matched) {
            this.observed = observed;
            this.fingerprint = fingerprint;
            this.matched = matched;
        }

        public String preview() {
            return observed.length() > 180 ? observed.substring(0, 180) + "…" : observed;
        }
    }

    public static Observation scan(Watch watch) throws IOException {
        try {
            String url = watch.url.trim();
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new IOException("La dirección debe empezar por https:// o http://");
            }
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; Vigilante/1.0; Android)")
                    .referrer("https://www.google.com/")
                    .timeout(15000)
                    .maxBodySize(2_000_000)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .execute();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("El sitio devolvió HTTP " + response.statusCode());
            }
            String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase(Locale.ROOT);
            String text;
            if (watch.selector.trim().isEmpty() && !contentType.contains("html")) {
                text = response.body(); // JSON/plain-text APIs can also be monitored.
            } else {
                Document document = response.parse();
                if (watch.selector.trim().isEmpty()) {
                    text = document.body() == null ? "" : document.body().text();
                } else {
                    Elements selected = document.select(watch.selector.trim());
                    if (selected.isEmpty()) {
                        throw new IOException("No se encontró el selector CSS: " + watch.selector);
                    }
                    StringBuilder buf = new StringBuilder();
                    for (Element element : selected) {
                        String part = watch.attribute.trim().isEmpty()
                                ? element.text() : element.attr(watch.attribute.trim());
                        if (buf.length() > 0) buf.append(" | ");
                        buf.append(part);
                    }
                    text = buf.toString();
                }
            }
            text = text.replaceAll("\\s+", " ").trim();
            String search = watch.keyword.trim().toLowerCase(Locale.ROOT);
            boolean matches = !search.isEmpty() && text.toLowerCase(Locale.ROOT).contains(search);
            return new Observation(text, sha256(text), matches);
        } catch (IllegalArgumentException badSelector) {
            throw new IOException("Selector o dirección no válida: " + badSelector.getMessage(), badSelector);
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
