package com.vigilante.app;

/** Pure transition evaluation, separated for unit testing. */
public final class TriggerLogic {
    private TriggerLogic() {}

    public static boolean shouldAlert(
            String rule, boolean initialized, boolean previousMatched,
            boolean currentMatched, String previousFingerprint, String currentFingerprint) {
        if (!initialized) return false; // establish baseline without surprise alert
        if ("DISAPPEARS".equals(rule)) return previousMatched && !currentMatched;
        if ("CHANGES".equals(rule)) return !previousFingerprint.equals(currentFingerprint);
        return !previousMatched && currentMatched;
    }
}
