package com.vigilante.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class NumericTrendTest {
 @Test public void comparesReadings(){
  Watch w=new Watch();w.name="USD/CUP — test";
  NumericTrend.record(w,"900",100);assertEquals("900 CUP",w.lastNumeric);assertEquals("Primera lectura",NumericTrend.direction(w));
  NumericTrend.record(w,"915",200);assertEquals("↑ Subió +15 CUP",NumericTrend.direction(w));
  NumericTrend.record(w,"910",300);assertEquals("↓ Bajó −5 CUP",NumericTrend.direction(w));
  NumericTrend.record(w,"910.00",400);assertEquals("→ Sin cambios",NumericTrend.direction(w));
 }
 @Test public void ignoresAmbiguousAndDifferentCurrency(){
  assertNull(NumericTrend.parse("USD 400 EUR 500",""));
  assertNull(NumericTrend.parse("EUR 500","CUP"));
  assertNull(NumericTrend.parse("Actualizado hace 5 minutos",""));
  assertEquals("10.25 USD",NumericTrend.parse("USD 10,25","").stored());
 }
 @Test public void unitChangeResetsBaseline(){
  Watch w=new Watch();NumericTrend.record(w,"5 USD",1);NumericTrend.record(w,"7 EUR",2);
  assertEquals("Primera lectura",NumericTrend.direction(w));
 }
}