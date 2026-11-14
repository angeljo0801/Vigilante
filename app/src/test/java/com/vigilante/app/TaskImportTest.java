package com.vigilante.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class TaskImportTest {
 @Test public void templateImportsDailyTimesAndThreshold(){
  Watch w=TaskImport.parse("VIGILANTE/1\nNOMBRE: Precio\nURL: https://example.com\nOBJETIVO: Producto\nHORAS: 18:00, 08:00\nREGLA: BELOW\nPALABRA: 25\nMOTOR: AUTO\nFIN");
  assertEquals("08:00,18:00",w.times);assertEquals("BELOW",w.rule);assertEquals("25",w.keyword);assertEquals("AUTO",w.engine);assertEquals("Producto",w.objective);
 }
 @Test public void importsInterval(){
  Watch w=TaskImport.parse("VIGILANTE/1\nURL: https://example.com\nINTERVALO: 120\nFIN");
  assertEquals(120,w.intervalMinutes);
 }
 @Test public void ordinaryShareDoesNotInferSchedule(){
  Watch w=TaskImport.parse("Consulta https://example.com a las 08:00");
  assertEquals("https://example.com",w.url);assertEquals("",w.times);assertEquals("CHANGES",w.rule);
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsUnsupportedSchedule(){TaskImport.parse("VIGILANTE/1\nHORAS: 25:00\nFIN");}
 @Test(expected=IllegalArgumentException.class) public void rejectsNonWebUrl(){TaskImport.parse("VIGILANTE/1\nURL: file:///etc/passwd\nFIN");}
}