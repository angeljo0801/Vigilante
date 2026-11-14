package com.vigilante.app;

import java.io.IOException;
import java.util.Locale;
import org.jsoup.nodes.Document;

/** A possible human verification or access restriction, not an extracted datum. */
public final class ActionRequired extends IOException {
    public ActionRequired(String message){super(message);}
    static void checkStatus(int status) throws ActionRequired {
        if(status==401) throw new ActionRequired("La página requiere autorización (HTTP 401).");
        if(status==403) throw new ActionRequired("Acceso restringido (HTTP 403). Puede requerir verificación humana.");
        if(status==429) throw new ActionRequired("El sitio limitó las consultas (HTTP 429). Respeta el tiempo de espera del sitio antes de reintentar.");
    }
    static void checkDocument(Document doc) throws ActionRequired {
        String title=doc.title().toLowerCase(Locale.ROOT);
        String body=doc.body()==null?"":doc.body().text().toLowerCase(Locale.ROOT);
        boolean titleChallenge=title.matches(".*(just a moment|attention required|verify (that )?you are human|checking your browser|verifica que eres humano|access denied|robot check).*");
        boolean textChallenge=body.contains("verify you are human")||body.contains("verify that you are human")||body.contains("checking your browser")||body.contains("complete the security check")||body.contains("verifica que eres humano")||body.contains("verifica que no eres un robot")||body.contains("comprueba que eres humano")||body.contains("please solve the captcha")||body.contains("confirm you are not a robot")||body.contains("are you a robot");
        boolean widget=doc.selectFirst("form#challenge-form, #cf-challenge-running, #challenge-stage, iframe[src*=captcha], iframe[src*=challenges.cloudflare.com], .g-recaptcha, .h-captcha, .cf-turnstile")!=null;
        if(titleChallenge || (textChallenge && (widget || body.length()<1800))) {
            throw new ActionRequired("Posible CAPTCHA o verificación humana. Abre la página y sigue sus instrucciones.");
        }
    }
}
