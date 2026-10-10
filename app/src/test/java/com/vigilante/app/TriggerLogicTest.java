package com.vigilante.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TriggerLogicTest {
    @Test public void firstObservationNeverAlerts() {
        assertFalse(TriggerLogic.shouldAlert("APPEARS", false, false, true, "", "a"));
        assertFalse(TriggerLogic.shouldAlert("CHANGES", false, false, false, "", "a"));
    }
    @Test public void appearsOnlyOnTransition() {
        assertTrue(TriggerLogic.shouldAlert("APPEARS", true, false, true, "x", "y"));
        assertFalse(TriggerLogic.shouldAlert("APPEARS", true, true, true, "x", "y"));
        assertFalse(TriggerLogic.shouldAlert("APPEARS", true, true, false, "x", "y"));
    }
    @Test public void disappearsOnlyOnTransition() {
        assertTrue(TriggerLogic.shouldAlert("DISAPPEARS", true, true, false, "x", "y"));
        assertFalse(TriggerLogic.shouldAlert("DISAPPEARS", true, false, false, "x", "y"));
    }
    @Test public void changesBasedOnFingerprint() {
        assertTrue(TriggerLogic.shouldAlert("CHANGES", true, false, false, "x", "y"));
        assertFalse(TriggerLogic.shouldAlert("CHANGES", true, false, false, "x", "x"));
    }
}
