package com.vigilante.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Calendar;
import java.util.TimeZone;

public class ScrapingV2Test {
    @Test public void timesAreSortedAndDeduplicated() {
        assertEquals("08:00,12:30,18:00",Scheduler.normalizeTimes("18:00, 8:00;12:30 08:00"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidClock() {
        Scheduler.normalizeTimes("25:00");
    }
    @Test public void nextClockUsesTomorrowAfterLastSlot() {
        Calendar now=Calendar.getInstance();
        now.set(2026,Calendar.NOVEMBER,14,19,0,0);
        now.set(Calendar.MILLISECOND,0);
        Calendar next=Calendar.getInstance();
        next.setTimeInMillis(Scheduler.nextTime("08:00,18:00",now.getTimeInMillis()));
        assertEquals(15,next.get(Calendar.DAY_OF_MONTH));
        assertEquals(8,next.get(Calendar.HOUR_OF_DAY));
        assertEquals(0,next.get(Calendar.MINUTE));
    }
    @Test public void alertsOnEachReadingOrThresholdCrossing() {
        assertTrue(TriggerLogic.shouldAlert("ALWAYS",false,false,false,"",""));
        assertTrue(TriggerLogic.shouldAlert("ABOVE",false,false,true,"",""));
        assertTrue(TriggerLogic.shouldAlert("BELOW",true,false,true,"",""));
        assertFalse(TriggerLogic.shouldAlert("BELOW",true,true,true,"",""));
        assertFalse(TriggerLogic.shouldAlert("CHANGES",false,false,false,"","new"));
    }
    @Test public void numericExtractionSupportsDecimalComma() {
        assertEquals(420.5,WebScanner.number("420,5"),0.001);
        assertEquals(-1.25,WebScanner.number("-1.25"),0.001);
    }
    @Test(expected=NumberFormatException.class) public void rejectsAmbiguousValues() {
        WebScanner.number("420 USD");
    }
}