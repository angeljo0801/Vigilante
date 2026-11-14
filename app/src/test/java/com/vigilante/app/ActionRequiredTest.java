package com.vigilante.app;
import org.jsoup.Jsoup;
import org.junit.Test;
public class ActionRequiredTest {
    @Test(expected=ActionRequired.class) public void detectsChallengeTitle() throws Exception {
        ActionRequired.checkDocument(Jsoup.parse("<title>Just a moment...</title><p>Checking your browser</p>"));
    }
    @Test public void ordinaryPageWithCaptchaWidgetIsNotBlocked() throws Exception {
        ActionRequired.checkDocument(Jsoup.parse("<title>Shop</title><h1>Product</h1><div class='g-recaptcha'></div>"));
    }
    @Test(expected=ActionRequired.class) public void detectsRateLimit() throws Exception { ActionRequired.checkStatus(429); }
    @Test(expected=ActionRequired.class) public void detectsRestrictedAccess() throws Exception { ActionRequired.checkStatus(403); }
    @Test public void ordinaryStatusPasses() throws Exception { ActionRequired.checkStatus(200); }
}